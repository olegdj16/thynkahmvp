package com.thynkah.controller;

import com.thynkah.model.Prompt;
import com.thynkah.repository.PromptRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/prompts") // ✅ now under /api
@CrossOrigin(origins = "*")
public class PromptController {

  private final PromptRepository promptRepository;

  public PromptController(PromptRepository promptRepository) {
    this.promptRepository = promptRepository;
  }

  // ✅ Get all prompts
  @GetMapping
  public ResponseEntity<List<Prompt>> getAllPrompts() {
    List<Prompt> prompts = promptRepository.findAll();
    return ResponseEntity.ok(prompts);
  }

  // ✅ Save a new prompt
  @PostMapping
  public ResponseEntity<Prompt> savePrompt(@RequestBody Prompt prompt) {
    Prompt saved = promptRepository.save(prompt);
    return ResponseEntity.status(201).body(saved); // return HTTP 201
  }
}
