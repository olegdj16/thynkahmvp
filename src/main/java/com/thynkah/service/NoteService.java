package com.thynkah.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thynkah.model.Note;
import com.thynkah.repository.NoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class NoteService {

    @Value("${openai.api.key}")
    private String OPENAI_API_KEY;

    private final NoteRepository repo;
    private final EmbeddingService embeddingService;
    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String EMBEDDING_URL = "https://api.openai.com/v1/embeddings";
    private static final String CHAT_URL      = "https://api.openai.com/v1/chat/completions";

    @Autowired
    public NoteService(NoteRepository repo, EmbeddingService embeddingService) {
        this.repo = repo;
        this.embeddingService = embeddingService;
        this.restTemplate = new RestTemplate();
    }

    @PostConstruct
    public void init() {
        if (OPENAI_API_KEY == null || OPENAI_API_KEY.isEmpty()) {
            throw new IllegalStateException("OpenAI API key is not configured!");
        }
    }

    /* ---------- CRUD ---------- */

    public Note save(Note note) {
        if (note.getCreatedAt() == null) {
            note.setCreatedAt(LocalDateTime.now());
        }

        String embedding = generateEmbedding(note.getText());
        note.setEmbedding(embedding);

        return repo.save(note);
    }

    public List<Note> findAll() {
        return repo.findAll();
    }

    public void delete(Long id) {
        repo.deleteById(id);
    }

    public Note updateText(Long id, String newText) {
        return repo.findById(id).map(note -> {
            note.setText(newText);
            note.setEmbedding(generateEmbedding(newText));
            return repo.save(note);
        }).orElseThrow(() -> new RuntimeException("Note not found with ID: " + id));
    }

    public Note updateTag(Long id, String newTag) {
        return repo.findById(id).map(note -> {
            note.setTag(newTag);
            return repo.save(note);
        }).orElseThrow(() -> new RuntimeException("Note not found with ID: " + id));
    }

    /* ---------- Embeddings helpers ---------- */

    /**
     * Used only for legacy/debug fallback testing.
     * For real embeddings, use EmbeddingService instead.
     */
    private String generateEmbedding(String text) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("input", text);
            requestBody.put("model", "text-embedding-3-small");

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + OPENAI_API_KEY);
            headers.set("Content-Type", "application/json");

            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response =
                    restTemplate.postForEntity(EMBEDDING_URL, request, String.class);

            JsonNode json = mapper.readTree(response.getBody());
            JsonNode data = json.path("data");

            if (!data.isArray() || data.isEmpty()) {
                System.err.println("Invalid embedding response: " + json);
                return Collections.emptyList().toString();
            }

            JsonNode array = data.get(0).get("embedding");
            List<Double> vector = new ArrayList<>();
            for (JsonNode val : array) {
                vector.add(val.asDouble());
            }
            return vector.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList().toString();
        }
    }

    public double[] parseEmbeddingVector(String jsonArray) {
        try {
            return mapper.readValue(jsonArray, double[].class);
        } catch (Exception e) {
            e.printStackTrace();
            return new double[0];
        }
    }

    private double cosineSimilarity(double[] a, double[] b) {
        double dot = 0.0, normA = 0.0, normB = 0.0;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private double[] toDoubleArray(float[] arr) {
        double[] out = new double[arr.length];
        for (int i = 0; i < arr.length; i++) {
            out[i] = arr[i];
        }
        return out;
    }

    /* ---------- “Most relevant note” (single) ---------- */

    // Used for the “Most relevant note:” display under the answer
    public Note findMostRelevantNote(String query) {
        float[] queryEmbedding = embeddingService.generateEmbedding(query);
        double[] queryVector = toDoubleArray(queryEmbedding);

        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime startOfTomorrow = today.plusDays(1).atStartOfDay();

        List<Note> candidates = repo.findByCreatedAtBetween(startOfDay, startOfTomorrow);
        if (candidates.isEmpty()) {
            candidates = repo.findTop50ByOrderByCreatedAtDesc();
        }
        if (candidates.isEmpty()) {
            return null;
        }

        return candidates.stream()
                .filter(n -> n.getEmbedding() != null && !n.getEmbedding().isBlank())
                .max(Comparator.comparingDouble(note ->
                        cosineSimilarity(
                                queryVector,
                                parseEmbeddingVector(note.getEmbedding())
                        )))
                .orElse(null);
    }

    /* ---------- Main QA entry point ---------- */

    public String answerQuestion(String question) {
        // 1) Embed the question
        float[] queryEmbedding = embeddingService.generateEmbedding(question);
        double[] queryVector = toDoubleArray(queryEmbedding);

        // 2) Load ALL notes
        List<Note> allNotes = repo.findAll();
        if (allNotes.isEmpty()) {
            // no notes → ask model without context
            return callChatModel(question, Collections.<Note>emptyList());
        }

        // 3) Score each note by cosine similarity and keep top K
        final int TOP_K = 5;  // how many notes to send as context

        class Scored {
            final Note note;
            final double score;

            Scored(Note note, double score) {
                this.note = note;
                this.score = score;
            }
        }

        List<Note> topNotes = allNotes.stream()
                .filter(n -> n.getEmbedding() != null && !n.getEmbedding().isBlank())
                .map(n -> {
                    double[] vec = parseEmbeddingVector(n.getEmbedding());
                    double sim = cosineSimilarity(queryVector, vec);
                    return new Scored(n, sim);
                })
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(TOP_K)
                .map(s -> s.note)
                .collect(Collectors.toList());

        if (topNotes.isEmpty()) {
            return callChatModel(question, Collections.<Note>emptyList());
        }

        // 4) Ask OpenAI to synthesize an answer based on those notes
        return callChatModel(question, topNotes);
    }

    /* ---------- OpenAI Chat call (multi-note context) ---------- */

    private String callChatModel(String question, List<Note> contextNotes) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + OPENAI_API_KEY);
            headers.set("Content-Type", "application/json");

            // System prompt: how the AI should behave
            Map<String, Object> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put(
                    "content",
                    "You are Thynkah, a personal second-brain for the user. " +
                            "You read the user's past notes and answer questions.\n" +
                            "IMPORTANT RULES:\n" +
                            "- Synthesize and paraphrase; DO NOT copy the notes verbatim.\n" +
                            "- Combine information from multiple notes if helpful.\n" +
                            "- If the notes do not contain the answer, say that clearly.\n" +
                            "- Be short, clear, and practical."
            );

            // Build context from multiple notes
            StringBuilder notesText = new StringBuilder();
            if (contextNotes != null && !contextNotes.isEmpty()) {
                notesText.append("Here are some of the user's relevant notes:\n\n");
                for (Note n : contextNotes) {
                    notesText.append("Note (")
                            .append(n.getCreatedAt() != null ? n.getCreatedAt() : "no date")
                            .append(")");
                    if (n.getTag() != null && !n.getTag().isBlank()) {
                        notesText.append(" [tags: ").append(n.getTag()).append("]");
                    }
                    notesText.append(":\n");
                    notesText.append(n.getText()).append("\n\n");
                }
            } else {
                notesText.append("The user currently has no saved notes.\n\n");
            }

            notesText.append("User's question: ").append(question);

            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", notesText.toString());

            List<Map<String, Object>> messages = List.of(systemMsg, userMsg);

            Map<String, Object> body = new HashMap<>();
            body.put("model", "gpt-4.1-mini");
            body.put("messages", messages);

            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(body, headers);

            ResponseEntity<String> response =
                    restTemplate.postForEntity(CHAT_URL, request, String.class);

            JsonNode root = mapper.readTree(response.getBody());
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                System.err.println("Invalid chat response: " + root);
                return "I couldn't generate an answer.";
            }

            return choices.get(0).path("message").path("content").asText();

        } catch (Exception e) {
            e.printStackTrace();
            return "There was an error talking to the AI service.";
        }
    }
}
