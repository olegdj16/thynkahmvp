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
    if (note.getCreatedAt() == null) {
      note.setCreatedAt(LocalDateTime.now());
    }
    return repo.save(note);
  }

  public List<Note> findAll() {
    return repo.findAll();
  }

  public void delete(Long id) {
    repo.deleteById(id);
  }

  public Note updateText(Long id, String newText) {
    Optional<Note> optional = repo.findById(id);
    if (optional.isPresent()) {
      Note note = optional.get();
      note.setText(newText);
      return repo.save(note);
    } else {
      throw new RuntimeException("Note not found with ID: " + id);
    }
  }

  public Note updateTag(Long id, String newTag) {
    Optional<Note> optional = repo.findById(id);
    if (optional.isPresent()) {
      Note note = optional.get();
      note.setTag(newTag);
      return repo.save(note);
    } else {
      throw new RuntimeException("Note not found with ID: " + id);
    }
  }
}
