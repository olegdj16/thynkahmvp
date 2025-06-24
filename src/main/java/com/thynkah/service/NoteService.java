package com.thynkah.service;

import com.thynkah.model.Note;
import com.thynkah.repository.NoteRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class NoteService {

  private final NoteRepository repo;

  public NoteService(NoteRepository repo) {
    this.repo = repo;
  }

  public Note save(Note note) {
    // Prevent saving if ID already exists in DB
    if (note.getId() != null && repo.existsById(note.getId())) {
      throw new IllegalArgumentException("Note with ID " + note.getId() + " already exists.");
    }

    if (note.getCreatedAt() == null) {
      note.setCreatedAt(LocalDateTime.now());
    }

    return repo.save(note);
  }

  public List<Note> findAll() {
    return repo.findAll();
  }

  public void delete(Long id) {
    if (!repo.existsById(id)) {
      throw new IllegalArgumentException("Cannot delete — Note not found with ID: " + id);
    }
    repo.deleteById(id);
  }

  public Note updateText(Long id, String newText) {
    Note note = repo.findById(id)
          .orElseThrow(() -> new RuntimeException("Note not found with ID: " + id));
    note.setText(newText);
    return repo.save(note);
  }

  public Note updateTag(Long id, String newTag) {
    Note note = repo.findById(id)
          .orElseThrow(() -> new RuntimeException("Note not found with ID: " + id));
    note.setTag(newTag);
    return repo.save(note);
  }
}
