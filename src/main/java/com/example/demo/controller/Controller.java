package com.example.demo.controller;

import com.example.demo.dto.DTOtoFRONT;
import com.example.demo.dto.StudentInfoDto;
import com.example.demo.entity.Category;
import com.example.demo.entity.EventEntity;
import com.example.demo.entity.StudentEntity;
import com.example.demo.repository.EventRepository;
import com.example.demo.repository.StudentRepository;
import com.example.demo.service.PostService;
import com.example.demo.service.TelegramOrchestratorService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class Controller {

    private static final Logger log = Logger.getLogger(Controller.class.getName());

    private final StudentRepository studentRepository;
    private final PostService postService;
    private final TelegramOrchestratorService orchestratorService;
    private final EventRepository eventRepository;

    public Controller(PostService postService,StudentRepository studentRepository,
                      TelegramOrchestratorService orchestratorService,
                      EventRepository eventRepository) {
        this.postService = postService;
        this.orchestratorService = orchestratorService;
        this.eventRepository = eventRepository;
        this.studentRepository=studentRepository;
    }

    @GetMapping("/events")
    public ResponseEntity<List<DTOtoFRONT>> getEvents(
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        log.info("Getting events with filters");
        var events = postService.searchEvents(category, studentId, dateFrom, dateTo, page, size);
        return ResponseEntity.ok(events);
    }

    @PostMapping("/parse")
    public ResponseEntity<Map<String, Object>> parseAndProcess() {
        log.info("Starting parse and AI process");

        var result = orchestratorService.parseAndProcess();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("parsedCount", result.getParsedCount());
        response.put("processedCount", result.getProcessedCount());
        response.put("durationMs", result.getDurationMs());
        response.put("events", result.getEvents());
        response.put("message", String.format("✅ Parsed %d posts, processed %d with AI in %d ms",
                result.getParsedCount(),
                result.getProcessedCount(),
                result.getDurationMs()));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {

        if (dateFrom == null) dateFrom = LocalDate.now().minusMonths(1);
        if (dateTo == null) dateTo = LocalDate.now();

        // Просто берем все события из БД
        var allEvents = eventRepository.findAll();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEvents", allEvents.size());
        stats.put("activeChannels", 3);

        // Распределение по категориям
        Map<String, Integer> categoryStats = new HashMap<>();
        for (Category cat : Category.values()) {
            categoryStats.put(cat.getDisplayName(), 0);
        }

        for (EventEntity event : allEvents) {
            String catName = event.getCategory().getDisplayName();
            categoryStats.put(catName, categoryStats.getOrDefault(catName, 0) + 1);
        }
        stats.put("categories", categoryStats);

        // Топ студентов
        Map<String, Integer> studentStats = new HashMap<>();
        for (EventEntity event : allEvents) {
            for (var student : event.getStudents()) {
                studentStats.put(student.getFullName(), studentStats.getOrDefault(student.getFullName(), 0) + 1);
            }
        }

        List<Map<String, Object>> topStudents = studentStats.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(entry -> {
                    Map<String, Object> student = new HashMap<>();
                    student.put("name", entry.getKey());
                    student.put("events", entry.getValue());
                    return student;
                })
                .collect(Collectors.toList());
        stats.put("topStudents", topStudents);

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/events/{eventId}/students")
    public ResponseEntity<List<StudentInfoDto>> getEventStudents(@PathVariable Long eventId) {
        var event = eventRepository.findById(eventId);
        if (event.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<StudentInfoDto> students = event.get().getStudents().stream()
                .map(s -> new StudentInfoDto(s.getFullName(), s.getGroupName()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(students);
    }

    @GetMapping("/students")
    public ResponseEntity<List<StudentEntity>> getAllStudents() {
        return ResponseEntity.ok(studentRepository.findAll());
    }

    @GetMapping("/students/by-name")
    public ResponseEntity<List<StudentEntity>> findStudentsByName(@RequestParam String name) {
        var students = studentRepository.findByFullNameContainingIgnoreCase(name);
        return ResponseEntity.ok(students.map(List::of).orElse(List.of()));
    }

    @GetMapping("/events/by-student-name")
    public ResponseEntity<List<DTOtoFRONT>> getEventsByStudentName(@RequestParam String lastName) {
        log.info("Searching events for student with lastName: " + lastName);

        // Ищем студента по фамилии
        var students = studentRepository.findByLastNameContainingIgnoreCase(lastName);

        if (students.isEmpty()) {
            return ResponseEntity.ok(List.of()); // Студент не найден, возвращаем пустой список
        }

        // Собираем все события, где участвует этот студент
        List<EventEntity> studentEvents = new ArrayList<>();
        for (StudentEntity student : students) {
            var events = eventRepository.findAll().stream()
                    .filter(e -> e.getStudents().contains(student))
                    .collect(Collectors.toList());
            studentEvents.addAll(events);
        }

        // Преобразуем в DTO и сортируем по дате (новые сверху)
        List<DTOtoFRONT> result = studentEvents.stream()
                .map(this::mapToDto)
                .sorted((a, b) -> b.date().compareTo(a.date()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    private DTOtoFRONT mapToDto(EventEntity entity) {
        return new DTOtoFRONT(
                entity.getEventDate(),
                entity.getCategory(),
                entity.getDescription(),
                entity.getPostLink(),
                entity.getStudents().stream()
                        .map(s -> new StudentInfoDto(s.getFullName(), s.getGroupName()))
                        .collect(Collectors.toList())
        );
    }
}