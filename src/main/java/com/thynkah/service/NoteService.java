package com.thynkah.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thynkah.model.Note;
import com.thynkah.repository.NoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class NoteService {

    @Value("${openai.api.key}")
    private String openAiApiKey;          // <== THIS is the injected API key

    private final NoteRepository repo;
    private final EmbeddingService embeddingService;
    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String EMBEDDING_URL = "https://api.openai.com/v1/embeddings";
    private static final String CHAT_URL      = "https://api.openai.com/v1/chat/completions";

    @Autowired
    public NoteService(NoteRepository repo,
                       EmbeddingService embeddingService) {
        this.repo = repo;
        this.embeddingService = embeddingService;
        this.restTemplate = new RestTemplate();
    }

    @PostConstruct
    public void init() {
        if (openAiApiKey == null || openAiApiKey.isEmpty()) {
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
            headers.set("Authorization", "Bearer " + openAiApiKey);
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

    /**
     * Used by the UI for “Most relevant note”.
     * Now also prefers recent notes.
     */
    public Note findMostRelevantNote(String question) {
        if (question == null || question.isBlank()) {
            return null;
        }

        List<Note> allNotes = repo.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        if (allNotes.isEmpty()) {
            return null;
        }

        String qLower = question.toLowerCase(Locale.ROOT);
        boolean aboutToday = qLower.contains("today");

        LocalDate today = LocalDate.now();
        LocalDateTime startToday = today.atStartOfDay();
        LocalDateTime endToday = startToday.plusDays(1);

        // Filter to notes that actually have embeddings
        List<Note> candidates = allNotes.stream()
                .filter(n -> n.getEmbedding() != null && !n.getEmbedding().isBlank())
                .filter(n -> {
                    if (!aboutToday) {
                        return true;
                    }
                    // If the question is about "today", only consider notes from today
                    if (n.getCreatedAt() == null) return false;
                    LocalDateTime ts = n.getCreatedAt();
                    return !ts.isBefore(startToday) && ts.isBefore(endToday);
                })
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            // For "today", if there are no notes today, just say "no best note".
            // (We do NOT fall back to old notes.)
            return null;
        }

        // One embedding call for the question
        float[] qVecFloat = embeddingService.generateEmbedding(question);
        double[] qVec = toDoubleArray(qVecFloat);

        Note best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (Note n : candidates) {
            double[] noteVec = parseEmbeddingVector(n.getEmbedding());
            if (noteVec.length == 0) continue;

            double sim = cosineSimilarity(qVec, noteVec);

            long daysOld = 0;
            if (n.getCreatedAt() != null) {
                daysOld = ChronoUnit.DAYS.between(n.getCreatedAt().toLocalDate(), today);
            }

            // For generic questions, push very old notes down.
            // For "today" questions all dates are today, so weight = 1.
            double recencyWeight = aboutToday
                    ? 1.0
                    : 1.0 / (1.0 + Math.max(0, daysOld) / 7.0);

            double score = sim * recencyWeight;

            if (score > bestScore) {
                bestScore = score;
                best = n;
            }
        }

        return best;
    }


    /* ---------- Main QA entry point ---------- */



    /**
     * Generic Q&A entry point used by POST /ask.
     */
    public String answerQuestion(String question) {
        if (question == null || question.isBlank()) {
            return "Please type a question.";
        }

        List<Note> allNotes = repo.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        if (allNotes.isEmpty()) {
            return "You don't have any notes yet, so I can't answer from your history.";
        }

        String qLower = question.toLowerCase(Locale.ROOT);
        boolean aboutToday = qLower.contains("today");

        LocalDate today = LocalDate.now();
        LocalDateTime startToday = today.atStartOfDay();
        LocalDateTime endToday = startToday.plusDays(1);

        // 1) Filter notes that have embeddings
        List<Note> candidates = allNotes.stream()
                .filter(n -> n.getEmbedding() != null && !n.getEmbedding().isBlank())
                .filter(n -> {
                    if (!aboutToday) {
                        // Generic question -> allow everything, recency will be handled in scoring
                        return true;
                    }
                    // Question explicitly about "today" -> ONLY notes from today
                    if (n.getCreatedAt() == null) return false;
                    LocalDateTime ts = n.getCreatedAt();
                    return !ts.isBefore(startToday) && ts.isBefore(endToday);
                })
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            // If user asked about "today" and we have no notes for today,
            // do NOT pull in old stuff. Tell the model there is no context.
            if (aboutToday) {
                return callChatModel(question, Collections.emptyList());
            }
            // Generic question but no embedded notes at all
            return "I couldn't find any notes with embeddings yet. Try adding some recent notes first.";
        }

        // 2) Embed the question once
        float[] qVecFloat = embeddingService.generateEmbedding(question);
        double[] qVec = toDoubleArray(qVecFloat);

        // 3) Score notes by similarity * recency weight
        LocalDateTime now = LocalDateTime.now();

        class Scored {
            final Note note;
            final double score;
            Scored(Note n, double s) { this.note = n; this.score = s; }
        }

        List<Scored> scored = candidates.stream()
                .map(n -> {
                    double[] noteVec = parseEmbeddingVector(n.getEmbedding());
                    if (noteVec.length == 0) return null;

                    double sim = cosineSimilarity(qVec, noteVec);

                    long daysOld = 0;
                    if (n.getCreatedAt() != null) {
                        daysOld = ChronoUnit.DAYS.between(n.getCreatedAt().toLocalDate(), today);
                    }

                    double recencyWeight = aboutToday
                            ? 1.0
                            : 1.0 / (1.0 + Math.max(0, daysOld) / 7.0);

                    double score = sim * recencyWeight;
                    return new Scored(n, score);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble((Scored s) -> s.score).reversed())
                .collect(Collectors.toList());

        if (scored.isEmpty()) {
            // Should be rare – everything had empty/invalid embedding
            return "I couldn't match your question to any of your notes yet.";
        }

        // 4) Take top-K as context for the chat model
        int topK = Math.min(8, scored.size());
        List<Note> topNotes = scored.subList(0, topK).stream()
                .map(s -> s.note)
                .collect(Collectors.toList());

        return callChatModel(question, topNotes);
    }








    /* ---------- OpenAI Chat call (multi-note context) ---------- */

    private String callChatModel(String question, List<Note> contextNotes) {
        try {
            String systemPrompt =
                    "You are Thynkah, a personal memory and planning assistant. "
                            + "You ONLY know what is written in the notes I give you. "
                            + "Never invent facts, events, or tasks that are not clearly implied by those notes.\n\n"
                            + "When the user asks what to do today (or a similar planning question):\n"
                            + "- Suggest only actions the user can realistically do themselves.\n"
                            + "- Do NOT tell them to clean, fix, or change things they do not own or control "
                            + "  (for example, a corporate or public shower, company facilities, other people's property).\n"
                            + "- Prefer concrete, next-step actions over vague advice.\n"
                            + "- If a note describes something that already happened or is clearly outside their control, "
                            + "  you may mention it as context but must not turn it into a todo item.\n\n"
                            + "If the provided notes are empty or clearly unrelated to the question, "
                            + "say explicitly that there is nothing relevant in their notes yet instead of guessing.";

            StringBuilder sb = new StringBuilder();
            if (contextNotes != null && !contextNotes.isEmpty()) {
                sb.append("Here are the user's most relevant notes (most recent / relevant first):\n\n");
                for (Note n : contextNotes) {
                    sb.append("- Note from ");
                    if (n.getCreatedAt() != null) {
                        sb.append(n.getCreatedAt());
                    } else {
                        sb.append("an unknown date");
                    }
                    sb.append(":\n");
                    sb.append(n.getText()).append("\n\n");
                }
            } else {
                sb.append("There are NO relevant notes for this query.\n");
            }

            String userPrompt =
                    "User question:\n" + question + "\n\n"
                            + "Use ONLY the notes below to answer. "
                            + "If they don't contain enough information, say so explicitly.\n\n"
                            + sb;

            Map<String, Object> body = new HashMap<>();
            body.put("model", "gpt-4.1-mini"); // or whatever model you are using

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of(
                    "role", "system",
                    "content", systemPrompt
            ));
            messages.add(Map.of(
                    "role", "user",
                    "content", userPrompt
            ));
            body.put("messages", messages);
            body.put("temperature", 0.2);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openAiApiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(CHAT_URL, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return "I couldn't reach the AI service right now.";
            }

            JsonNode root = mapper.readTree(response.getBody());
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).path("message");
                JsonNode content = message.path("content");
                if (!content.isMissingNode()) {
                    return content.asText().trim();
                }
            }

            return "I couldn't get a meaningful answer from the AI.";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error while contacting AI: " + e.getMessage();
        }
    }


    // inside NoteService
    public List<Note> findNotesForDate(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end   = date.plusDays(1).atStartOfDay().minusNanos(1);
        return repo.findByCreatedAtBetween(start, end);
    }



    public String answerQuestionForDate(String question, LocalDate date) {
        List<Note> contextNotes = findNotesForDate(date);

        if (contextNotes == null || contextNotes.isEmpty()) {
            // no notes that day – fall back to normal behaviour
            return answerQuestion(question);
        }

        String q = question;
        if (q == null || q.trim().isEmpty()) {
            // default used by your “Ask about this day” button
            q = "Summarize everything important I did, thought or felt on this date. "
                    + "Describe it as a narrative summary, not a to-do list.";
        }

        // Add safety rails so it does NOT invent tasks for you
        q = q + "\n\n"
                + "Very important rules:\n"
                + "- Describe what happened and how I felt.\n"
                + "- Do NOT turn complaints or observations into tasks unless I explicitly said I plan to act on them.\n"
                + "- If I complain about something that is someone else's responsibility "
                + "  (for example, a dirty corporate shower), mention it only as part of the story, "
                + "  and do NOT say that I should clean or fix it.\n"
                + "- Only list concrete tasks if I clearly wrote that I need or intend to do them.";

        return callChatModel(q, contextNotes);
    }


    public String answerQuestionForNote(String question, Long noteId) {
        Note note = repo.findById(noteId)
                .orElseThrow(() -> new IllegalArgumentException("Note not found: " + noteId));

        String q = question;
        if (q == null || q.trim().isEmpty()) {
            q = "Summarize this note in a few sentences. "
                    + "Capture what I did, thought or felt, not a list of orders for me.";
        }

        q = q + "\n\n"
                + "Rules:\n"
                + "- If I describe something unpleasant (e.g. a dirty shower), treat it as an observation.\n"
                + "- Only turn something into a task if I clearly wrote it as a plan, intention or reminder.";

        return callChatModel(q, Collections.singletonList(note));
    }




    public List<Note> findAllForCurrentUser() {
        // single-user for now; later you can filter by userId
        return repo.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

}
