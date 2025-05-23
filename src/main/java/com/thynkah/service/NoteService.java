package com.thynkah.service;

import com.thynkah.model.Note;
import com.thynkah.repository.NoteRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NoteService {
  private final NoteRepository repo;

  public NoteService(NoteRepository repo) {
    this.repo = repo;
  }

  public void save(Note note) {
    note.setCreatedAt(note.getCreatedAt() == null ? java.time.LocalDateTime.now() : note.getCreatedAt());
    repo.save(note);
  }

  public List<Note> findAll() {
    return repo.findAll();
  }

  public void delete(Long id) {
    repo.deleteById(id);
  }
}
