package com.thynkah.controller;

import com.thynkah.model.Note;
import com.thynkah.repository.NoteRepository;
import com.thynkah.service.NoteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
@CrossOrigin(origins = "*")
public class NoteController {

  private final NoteService noteService;
  private final NoteRepository noteRepository;

  @Autowired
  public NoteController(NoteService noteService, NoteRepository noteRepository) {
    this.noteService = noteService;
    this.noteRepository = noteRepository;
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
      return noteService.findAllForCurrentUser();
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

  @GetMapping("/tags")
  @ResponseBody
  public List<String> getAllTags() {
    return noteRepository.findAll().stream()
          .flatMap(note -> {
            if (note.getTag() != null)
              return Arrays.stream(note.getTag().split(",")).map(String::trim);
            return Stream.empty();
          })
          .filter(tag -> !tag.isBlank())
          .distinct()
          .sorted()
          .collect(Collectors.toList());
  }

    // Ask a question -> AI answer (using your notes)
    @PostMapping(value = "/ask", consumes = "application/json", produces = "application/json")
    @ResponseBody
    public Map<String, Object> ask(@RequestBody Map<String, String> body) {
        String question = body.get("question");

        // AI-generated answer, with "today first, else recent" logic inside
        String answer = noteService.answerQuestion(question);

        // Optional: expose the note that was used, for "Most relevant note" display
        Note bestNote = noteService.findMostRelevantNote(question);

        Map<String, Object> result = new HashMap<>();
        result.put("answer", answer);

        if (bestNote != null) {
            result.put("noteId", bestNote.getId());
            result.put("noteText", bestNote.getText());
            result.put("noteCreatedAt", bestNote.getCreatedAt());
            result.put("noteTag", bestNote.getTag());
        } else {
            result.put("noteId", null);
            result.put("noteText", null);
        }

        return result;
    }

    // inside NoteController
    @GetMapping("/notes/by-date")
    @ResponseBody
    public List<Note> getNotesByDate(
            @RequestParam("date")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return noteService.findNotesForDate(date);
    }

    @PostMapping(value = "/ask/day", consumes = "application/json", produces = "application/json")
    @ResponseBody
    public Map<String, Object> askForDay(@RequestBody Map<String, String> body) {

        String dateStr  = body.get("date");
        String question = body.get("question"); // optional

        if (dateStr == null || dateStr.isBlank()) {
            throw new IllegalArgumentException("date is required (YYYY-MM-DD)");
        }

        LocalDate date = LocalDate.parse(dateStr); // assumes ISO (YYYY-MM-DD)

        if (question == null || question.isBlank()) {
            question = "Summarize everything important I did, thought, or noted on " + dateStr + ".";
        }

        String answer = noteService.answerQuestionForDate(question, date);

        // Optional: also return the top “most relevant note” for that specific day
        List<Note> notesForDay = noteService.findNotesForDate(date);
        Note best = null;
        if (!notesForDay.isEmpty()) {
            // simple heuristic: latest note on that day
            best = notesForDay.stream()
                    .max(Comparator.comparing(Note::getCreatedAt))
                    .orElse(null);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("answer", answer);

        if (best != null) {
            result.put("noteId", best.getId());
            result.put("noteText", best.getText());
            result.put("noteCreatedAt", best.getCreatedAt());
            result.put("noteTag", best.getTag());
        } else {
            result.put("noteId", null);
            result.put("noteText", null);
        }

        return result;
    }

    @PostMapping(value = "/ask/note/{id}", consumes = "application/json", produces = "application/json")
    @ResponseBody
    public Map<String, Object> askForNote(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {

        String question = (body != null) ? body.get("question") : null;

        String answer = noteService.answerQuestionForNote(question, id);

        Map<String, Object> result = new HashMap<>();
        result.put("answer", answer);
        return result;
    }

    @GetMapping("/")
    public String home(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Model model
    ) {
        var notesPage = noteService.getNotesPage(page, size);

        model.addAttribute("notesPage", notesPage);
        model.addAttribute("notes", notesPage.getContent());
        model.addAttribute("page", page);
        model.addAttribute("size", size);

        return "index";
    }

    @GetMapping("/notes/view")
    public String notesView(
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="10") int size,
            Model model
    ) {
        Page<Note> notesPage = noteService.getNotesPage(page, size);

        model.addAttribute("noteForm", new Note());
        model.addAttribute("notesPage", notesPage);
        model.addAttribute("notes", notesPage.getContent());
        model.addAttribute("page", page);
        model.addAttribute("size", size);

        return "notes";
    }

    @PostMapping("/notes/view")
    public String saveNoteFromForm(@ModelAttribute("noteForm") Note noteForm) {
        noteService.save(noteForm);
        return "redirect:/notes/view";
    }

    @GetMapping(value = "/notes/page", produces = "application/json")
    @ResponseBody
    public Page<Note> getNotesPageJson(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return noteService.getNotesPage(page, size);
    }


    @PostMapping("/notes/delete/{id}")
    public String deleteFromNotesView(
            @PathVariable Long id,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="10") int size
    ) {
        noteService.delete(id);
        return "redirect:/notes/view?page=" + page + "&size=" + size;
    }


}
