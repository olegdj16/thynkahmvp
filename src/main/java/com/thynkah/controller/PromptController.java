package com.thynkah.controller;

import com.thynkah.model.Prompt;
import com.thynkah.repository.PromptRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/prompts")
public class PromptController {

  private final PromptRepository promptRepository;

  public PromptController(PromptRepository promptRepository) {
    this.promptRepository = promptRepository;
  }

  @GetMapping
  public List<Prompt> getAllPrompts() {
    return promptRepository.findAll();
  }

  @PostMapping
  public Prompt savePrompt(@RequestBody Prompt prompt) {
    return promptRepository.save(prompt);
  }
}
