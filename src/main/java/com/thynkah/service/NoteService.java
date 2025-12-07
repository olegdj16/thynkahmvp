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

@Service
public class NoteService {

  @Value("${openai.api.key}")
  private String OPENAI_API_KEY;

  private final NoteRepository repo;
  private final EmbeddingService embeddingService;
  private final RestTemplate restTemplate;
  private final ObjectMapper mapper = new ObjectMapper();
  private final String EMBEDDING_URL = "https://api.openai.com/v1/embeddings";

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

      HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
      ResponseEntity<String> response = restTemplate.postForEntity(EMBEDDING_URL, request, String.class);

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
    for (int i = 0; i < a.length; i++) {
      dot += a[i] * b[i];
      normA += a[i] * a[i];
      normB += b[i] * b[i];
    }
    return dot / (Math.sqrt(normA) * Math.sqrt(normB));
  }

    public Note findMostRelevantNote(String query) {
        // 1) Build query embedding
        float[] queryEmbedding = embeddingService.generateEmbedding(query);
        double[] queryVector = toDoubleArray(queryEmbedding);

        // 2) Figure out "today" in system timezone
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime startOfTomorrow = today.plusDays(1).atStartOfDay();

        // 3) First try: notes from today
        List<Note> candidates =
                repo.findByCreatedAtBetween(startOfDay, startOfTomorrow);

        // 4) Fallback: if no notes today, use most recent notes overall
        if (candidates.isEmpty()) {
            candidates = repo.findTop50ByOrderByCreatedAtDesc();
        }

        if (candidates.isEmpty()) {
            // no notes at all in the system
            return null;
        }

        // 5) Among the candidate set, pick the one with highest cosine similarity
        return candidates.stream()
                .filter(n -> n.getEmbedding() != null && !n.getEmbedding().isBlank())
                .max(Comparator.comparingDouble(note ->
                        cosineSimilarity(
                                queryVector,
                                parseEmbeddingVector(note.getEmbedding())
                        )))
                .orElse(null);
    }

  private double[] toDoubleArray(float[] arr) {
    double[] out = new double[arr.length];
    for (int i = 0; i < arr.length; i++) {
      out[i] = arr[i];
    }
    return out;
  }

  // ANSWER QUESTION
    public String answerQuestion(String question) {
        // 1) Embed the question
        float[] queryEmbedding = embeddingService.generateEmbedding(question);
        double[] queryVector = toDoubleArray(queryEmbedding);

        // 2) Build a candidate set: today’s notes first
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        var startOfDay = today.atStartOfDay();
        var startOfTomorrow = today.plusDays(1).atStartOfDay();

        List<Note> candidates = repo.findByCreatedAtBetween(startOfDay, startOfTomorrow);

        if (candidates.isEmpty()) {
            // No notes today → fallback to latest notes
            candidates = repo.findTop50ByOrderByCreatedAtDesc();
        }

        if (candidates.isEmpty()) {
            // No notes at all → just let the model answer without notes
            return callChatModel(question, null);
        }

        // 3) Pick the most relevant note among candidates
        Note best = candidates.stream()
                .filter(n -> n.getEmbedding() != null && !n.getEmbedding().isBlank())
                .max(Comparator.comparingDouble(note ->
                        cosineSimilarity(
                                queryVector,
                                parseEmbeddingVector(note.getEmbedding())
                        )))
                .orElse(null);

        // 4) Ask OpenAI to answer based on that note
        return callChatModel(question, best);
    }

    private static final String CHAT_URL = "https://api.openai.com/v1/chat/completions";

    private String callChatModel(String question, Note contextNote) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + OPENAI_API_KEY);
            headers.set("Content-Type", "application/json");

            // Build system + user messages
            Map<String, Object> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content",
                    "You are Thynkah, a personal second-brain. " +
                    "You answer concisely based only on the user's notes. " +
                    "If the notes do not contain the answer, say that clearly.");

            StringBuilder userContent = new StringBuilder();
            if (contextNote != null) {
                userContent.append("Here is a note from the user:\n\n");
                userContent.append(contextNote.getText()).append("\n\n");
                if (contextNote.getCreatedAt() != null) {
                    userContent.append("Note date: ").append(contextNote.getCreatedAt()).append("\n\n");
                }
            } else {
                userContent.append("The user has no saved notes yet.\n\n");
            }

            userContent.append("User question: ").append(question);

            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userContent.toString());

            List<Map<String, Object>> messages = List.of(systemMsg, userMsg);

            Map<String, Object> body = new HashMap<>();
            body.put("model", "gpt-4.1-mini");
            body.put("messages", messages);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
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
