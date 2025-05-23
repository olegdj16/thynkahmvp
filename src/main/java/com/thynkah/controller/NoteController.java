package com.thynkah.controller;

import com.thynkah.model.Note;
import com.thynkah.service.NoteService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class NoteController {

  private final NoteService noteService;

  public NoteController(NoteService noteService) {
    this.noteService = noteService;
  }

  // This will serve index.html at root URL
  @GetMapping({"/", "/index"})
  public String index(Model model) {
    model.addAttribute("notes", noteService.findAll());
    model.addAttribute("noteForm", new Note());
    return "index"; // renders templates/index.html
  }

  @PostMapping("/notes")
  public String saveNote(@ModelAttribute("noteForm") Note note) {
    noteService.save(note);
    return "redirect:/";
  }

  @PostMapping("/notes/delete/{id}")
  public String deleteNote(@PathVariable Long id) {
    noteService.delete(id);
    return "redirect:/";
  }
}
