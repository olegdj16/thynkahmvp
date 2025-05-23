package com.thynkah.controller;

import com.thynkah.model.Note;
import com.thynkah.model.Prompt;
import com.thynkah.repository.NoteRepository;
import com.thynkah.repository.PromptRepository;
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
    String question = body.get("question");

    // Load previous notes from DB
    List<Note> notes = noteRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));

    // Join the text of all notes as context
    StringBuilder context = new StringBuilder("Previous thoughts:\n");
    for (Note note : notes) {
      context.append("- ").append(note.getText()).append("\n");
    }

    // Append current question
    context.append("\nNow, here's what I think about your question: ").append(question);

    // You can later call GPT/OpenAI or return mock response
    Map<String, String> response = new HashMap<>();
    response.put("reply", context.toString());

    return response;
  }
}
