# Thynkah

Thynkah is an **AI-powered second brain** for creatives and people with memory struggles.  
It helps you capture notes, tag them, and then use semantic search + GPT to get clear,
personalized answers from your own knowledge.

Built with **Spring Boot**, **Thymeleaf**, and **MySQL**.

---

## Features

- ✏️ Create and edit notes in a simple web UI
- 🏷 Tag notes by topic (e.g. `painting`, `career`, `health`)
- 🔎 Semantic search over notes using OpenAI embeddings
- 🤖 Question-answering: ask a question and Thynkah answers using your notes
- 🗑 Delete notes from the UI
- 📱 Clean, minimal layout optimized for future mobile support

---

## Tech stack

- **Backend:** Spring Boot 2.7, Java 17+
- **Views:** Thymeleaf templates + CSS (no frontend framework)
- **Database:** MySQL
- **AI:** OpenAI embeddings (`text-embedding-3-small`) + GPT completions
- **Build tool:** Maven

---

## Architecture overview

- `src/main/java/com/thynkah/...`
  - `controller` – HTTP controllers and view endpoints
  - `service` – business logic (note management, embedding calls, search)
  - `repository` – JPA repositories for notes, tags, and embeddings
  - `model` – JPA entities (`Note`, `Tag`, `NoteTag`, etc.)
- `src/main/resources/templates` – Thymeleaf HTML templates
- `src/main/resources/static` – CSS and assets

---

## Getting started

### Prerequisites

- Java 17+
- Maven
- MySQL running locally (or in Docker)
- An OpenAI API key

### MySQL setup

Create a database, for example:

```sql
CREATE DATABASE thynkah CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
