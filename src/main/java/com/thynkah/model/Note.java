package com.thynkah.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Note {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String text;

  @Column(name = "createdat")
  private LocalDateTime createdAt;

  public Long getId() {
    return id;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
