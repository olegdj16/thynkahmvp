package com.thynkah.controller;

import com.thynkah.model.Note;
import com.thynkah.service.NoteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api") // ✅ All chat endpoints under /api/*
@CrossOrigin(origins = "*")
public class ChatController {

  private final NoteService noteService;

  public ChatController(NoteService noteService) {
    this.noteService = noteService;
  }

  // ✅ Semantic search via POST
  @PostMapping("/chat")
  public ResponseEntity<Map<String, String>> chat(@RequestBody Map<String, String> body) {
    String question = body.get("question");
    Map<String, String> response = new HashMap<>();

    Note bestMatch = noteService.findMostRelevantNote(question);

    if (bestMatch != null) {
      response.put("reply", "🧠 Most relevant note:\n" + bestMatch.getText());
      return ResponseEntity.ok(response);
    } else {
      response.put("reply", "I couldn't find a matching note based on meaning. Try rephrasing?");
      return ResponseEntity.ok(response);
    }
  }

  // ✅ Quick semantic search via GET
  @GetMapping("/ask")
  public ResponseEntity<Map<String, Object>> ask(@RequestParam String query) {
    Note bestMatch = noteService.findMostRelevantNote(query);
    Map<String, Object> result = new HashMap<>();

    if (bestMatch != null) {
      result.put("id", bestMatch.getId());
      result.put("text", bestMatch.getText());
      result.put("tag", bestMatch.getTag());
      result.put("createdAt", bestMatch.getCreatedAt());
      return ResponseEntity.ok(result);
    } else {
      result.put("message", "No matching note found.");
      return ResponseEntity.ok(result);
    }
  }
}
