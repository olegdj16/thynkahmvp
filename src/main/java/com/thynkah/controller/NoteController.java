package com.thynkah.controller;

import com.thynkah.model.Note;
import com.thynkah.repository.NoteRepository;
import com.thynkah.service.NoteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller // ✅ changed from @RestController
@CrossOrigin(origins = "*")
public class NoteController {

  private final NoteService noteService;
  private final NoteRepository noteRepository;

  @Autowired
  public NoteController(NoteService noteService, NoteRepository noteRepository) {
    this.noteService = noteService;
    this.noteRepository = noteRepository;
  }

  // ✅ Serve index.html with model attributes
  @GetMapping({"/", "/index"})
  public String index(Model model) {
    model.addAttribute("notes", noteService.findAll());
    model.addAttribute("noteForm", new Note());
    return "index"; // looks up templates/index.html
  }

  // ✅ Save a new note (used by frontend JavaScript)
  @PostMapping(value = "/notes", consumes = "application/json", produces = "application/json")
  @ResponseBody
  public Note saveNoteFromJson(@RequestBody Note note) {
    return noteService.save(note);
  }

  // ✅ Fetch all notes as JSON
  @GetMapping("/notes")
  @ResponseBody
  public List<Note> getAllNotes() {
    return noteRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
  }

  // ✅ Delete a note by ID
  @DeleteMapping("/notes/{id}")
  @ResponseBody
  public void deleteNote(@PathVariable Long id) {
    noteService.delete(id);
  }

  // ✅ Update note text
  @PatchMapping("/notes/{id}/text")
  @ResponseBody
  public Note updateText(@PathVariable Long id, @RequestBody Map<String, String> body) {
    return noteService.updateText(id, body.get("text"));
  }

  // ✅ Update note tag
  @PatchMapping("/notes/{id}/tag")
  @ResponseBody
  public Note updateTag(@PathVariable Long id, @RequestBody Map<String, String> body) {
    return noteService.updateTag(id, body.get("tag"));
  }
}
