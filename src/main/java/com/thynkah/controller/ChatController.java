package com.thynkah.controller;

import com.thynkah.model.Note;
import com.thynkah.repository.NoteRepository;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class ChatController {

  private final NoteRepository noteRepository;

  public ChatController(NoteRepository noteRepository) {
    this.noteRepository = noteRepository;
  }

  @PostMapping("/chat")
  public Map<String, String> chat(@RequestBody Map<String, String> body) {
    String question = body.get("question").toLowerCase();

    List<Note> notes = noteRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));

    String[] keywords = question.split("\\W+");
    Note bestMatch = null;
    int bestScore = 0;

    for (Note note : notes) {
      int score = 0;
      String noteText = note.getText().toLowerCase();

      for (String keyword : keywords) {
        if (noteText.contains(keyword)) {
          score++;
        }
      }

      if (score > bestScore) {
        bestScore = score;
        bestMatch = note;
      }
    }

    Map<String, String> response = new HashMap<>();
    if (bestMatch != null && bestScore > 0) {
      response.put("reply", "This note might help:\n" + bestMatch.getText());
    } else {
      response.put("reply", "I looked through your notes, but couldn't find a clear answer.");
    }

    return response;
  }


}
