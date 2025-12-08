package com.thynkah.repository;

import com.thynkah.model.Note;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface NoteRepository extends JpaRepository<Note, Long> {
    // All notes for a date range (today)
    List<Note> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // Fallback: last N notes, newest first
    List<Note> findTop50ByOrderByCreatedAtDesc();
}
