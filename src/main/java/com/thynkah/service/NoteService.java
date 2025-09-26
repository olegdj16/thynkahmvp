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
import java.time.LocalDateTime;
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
    float[] queryEmbedding = embeddingService.generateEmbedding(query);
    double[] queryVector = toDoubleArray(queryEmbedding);

    List<Note> allNotes = repo.findAll();
    return allNotes.stream()
          .filter(n -> n.getEmbedding() != null && !n.getEmbedding().isBlank())
          .max(Comparator.comparingDouble(note -> cosineSimilarity(
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
}
