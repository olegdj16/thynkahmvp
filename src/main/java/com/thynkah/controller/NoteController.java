package com.thynkah.controller;

import com.thynkah.model.Note;
import com.thynkah.repository.NoteRepository;
import com.thynkah.service.NoteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
@CrossOrigin(origins = "*")
@RequestMapping("/api") // ✅ all JSON endpoints under /api/*
public class NoteController {

  private final NoteService noteService;
  private final NoteRepository noteRepository;

  @Autowired
  public NoteController(NoteService noteService, NoteRepository noteRepository) {
    this.noteService = noteService;
    this.noteRepository = noteRepository;
  }

  // ✅ Render Thymeleaf template (HTML)
  @GetMapping({"/", "/index"})
  public String index(Model model) {
    model.addAttribute("notes", noteService.findAll());
    model.addAttribute("noteForm", new Note());
    return "index"; // looks up templates/index.html
  }

  // ✅ Create a new note (JSON)
  @PostMapping(value = "/notes", consumes = "application/json", produces = "application/json")
  @ResponseBody
  public ResponseEntity<Note> saveNoteFromJson(@RequestBody Note note) {
    Note saved = noteService.save(note);
    return ResponseEntity.ok(saved);
  }

  // ✅ Fetch all notes as JSON
  @GetMapping("/notes")
  @ResponseBody
  public ResponseEntity<List<Note>> getAllNotes() {
    List<Note> notes = noteRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    return ResponseEntity.ok(notes);
  }

  // ✅ Delete a note
  @DeleteMapping("/notes/{id}")
  @ResponseBody
  public ResponseEntity<Void> deleteNote(@PathVariable Long id) {
    noteService.delete(id);
    return ResponseEntity.noContent().build();
  }

  // ✅ Update note text
  @PatchMapping("/notes/{id}/text")
  @ResponseBody
  public ResponseEntity<Note> updateText(@PathVariable Long id, @RequestBody Map<String, String> body) {
    Note updated = noteService.updateText(id, body.get("text"));
    return ResponseEntity.ok(updated);
  }

  // ✅ Update note tag
  @PatchMapping("/notes/{id}/tag")
  @ResponseBody
  public ResponseEntity<Note> updateTag(@PathVariable Long id, @RequestBody Map<String, String> body) {
    Note updated = noteService.updateTag(id, body.get("tag"));
    return ResponseEntity.ok(updated);
  }

  // ✅ Get distinct tags from all notes
  @GetMapping("/tags")
  @ResponseBody
  public ResponseEntity<List<String>> getAllTags() {
    List<String> tags = noteRepository.findAll().stream()
          .flatMap(note -> {
            if (note.getTag() != null)
              return Arrays.stream(note.getTag().split(",")).map(String::trim);
            return Stream.empty();
          })
          .filter(tag -> !tag.isBlank())
          .distinct()
          .sorted()
          .collect(Collectors.toList());

    return ResponseEntity.ok(tags);
  }
}
