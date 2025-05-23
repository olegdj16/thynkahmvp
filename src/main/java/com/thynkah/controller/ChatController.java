package com.thynkah.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ChatController {

  @PostMapping("/chat")
  public Map<String, String> chat(@RequestBody Map<String, String> body) {
    String question = body.get("question");
    String response = generateFakeAnswer(question); // Simulate or connect to LLM/logic

    return Map.of("reply", response); // ✅ Ensure the key is 'reply'
  }

  private String generateFakeAnswer(String q) {
    // For now, simulate:
    return "Here's a thought: " + q;
  }
}
