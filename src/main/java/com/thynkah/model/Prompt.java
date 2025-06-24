package com.thynkah.model;

import javax.persistence.*;

@Entity
@Table(name = "prompts")  // explicitly set table name
public class Prompt {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "prompt") // match column name in the DB
  private String prompt;

  @Column(name = "createdat") // match column name in the DB
  private String createdAt;

  // Getters and setters
  public Long getId() { return id; }

  public String getPrompt() { return prompt; }
  public void setPrompt(String prompt) { this.prompt = prompt; }

  public String getCreatedAt() { return createdAt; }
  public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
