package com.thynkah.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class EmbeddingService {

  @Value("${openai.api.key}")
  private String OPENAI_API_KEY;

  private final RestTemplate restTemplate = new RestTemplate();
  private final ObjectMapper mapper = new ObjectMapper();
  private final String EMBEDDING_URL = "https://api.openai.com/v1/embeddings";

  public float[] generateEmbedding(String text) {
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
        return new float[0];
      }

      JsonNode embeddingArray = data.get(0).get("embedding");
      float[] embedding = new float[embeddingArray.size()];
      for (int i = 0; i < embeddingArray.size(); i++) {
        embedding[i] = (float) embeddingArray.get(i).asDouble();
      }

      return embedding;

    } catch (Exception e) {
      e.printStackTrace();
      return new float[0];
    }
  }
}
