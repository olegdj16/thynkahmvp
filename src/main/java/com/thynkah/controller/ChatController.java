package com.thynkah.controller;

import com.thynkah.model.Note;
import com.thynkah.service.NoteService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
public class ChatController {

  private final NoteService noteService;

  public ChatController(NoteService noteService) {
    this.noteService = noteService;
  }

  @PostMapping("/chat")
  public Map<String, String> chat(@RequestBody Map<String, String> body) {
    String question = body.get("question");
    Map<String, String> response = new HashMap<>();

    Note bestMatch = noteService.findMostRelevantNote(question);

    if (bestMatch != null) {
      response.put("reply", "🧠 Most relevant note:\n" + bestMatch.getText());
    } else {
      response.put("reply", "I couldn't find a matching note based on meaning. Try rephrasing?");
    }

    return response;
  }

  @GetMapping("/ask")
  @ResponseBody
  public Note ask(@RequestParam String query) {
    return noteService.findMostRelevantNote(query);
  }


}


