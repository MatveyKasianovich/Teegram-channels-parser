package com.example.demo.service;

import com.example.demo.entity.Category;
import com.example.demo.entity.StudentEntity;
import com.example.demo.repository.StudentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class AiClassificationService {

    private static final Logger log = Logger.getLogger(AiClassificationService.class.getName());

    @Value("${openai.api.key:}")
    private String openAiApiKey;

    @Value("${openai.model:gpt-3.5-turbo}")
    private String model;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final StudentRepository studentRepository;

    private final Map<Category, List<String>> keywords = Map.of(
            Category.RESERCH_WORK, List.of("конференци", "статья", "грант", "олимпиад", "хакатон",
                    "исследовани", "иннастарт", "иннофест", "100 идей", "научн"),
            Category.SPORT, List.of("соревновани", "турнир", "матч", "первенств", "кросс", "футбол",
                    "баскетбол", "волейбол", "спартакиад"),
            Category.SOCIAL_ACTIVITY, List.of("волонтер", "концерт", "фестиваль", "субботник", "экскурси"),
            Category.IDEOLOGICAL_EDUCATION, List.of("урок мужеств", "день победы", "возложение цветов",
                    "митинг", "ветеран", "память", "герой", "воинска слав",
                    "знамя", "гимн", "независимости", "народного единства",
                    "9 мая", "23 февраля", "флаг", "герб")
    );

    public AiClassificationService(StudentRepository studentRepository) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.studentRepository = studentRepository;
    }

    public AiResult classify(String text) {
        if (openAiApiKey != null && !openAiApiKey.isEmpty() && !openAiApiKey.equals("sk-your-api-key-here")) {
            try {
                return classifyWithOpenAI(text);
            } catch (Exception e) {
                log.warning("OpenAI failed, using fallback: " + e.getMessage());
            }
        }
        return classifyWithRules(text);
    }

    private AiResult classifyWithOpenAI(String text) {
        String prompt = buildPrompt(text);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiApiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", "Ты классификатор мероприятий и экстрактор имен. Отвечай строго в формате JSON."),
                Map.of("role", "user", "content", prompt)
        ));
        requestBody.put("temperature", 0.2);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    "https://api.openai.com/v1/chat/completions",
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            JsonNode root = objectMapper.readTree(response.getBody());
            String content = root.path("choices").get(0).path("message").path("content").asText();

            return parseAiResponse(content, text);
        } catch (Exception e) {
            throw new RuntimeException("OpenAI API error: " + e.getMessage(), e);
        }
    }

    private String buildPrompt(String text) {
        return String.format("""
            Проанализируй следующий текст из Telegram-канала университета:
            
            "%s"
            
            Задачи:
            1. Определи категорию мероприятия из списка: НИРС, СПОРТ, ОБЩЕСТВЕННАЯ_ДЕЯТЕЛЬНОСТЬ, ИДЕОЛОГИЧЕСКОЕ_ВОСПИТАНИЕ, ПРОЧЕЕ.
            2. Найди ФИО студентов (в формате "Иванов Иван Иванович", "Иванов И.И.", "Иван Иванов" или "Полина Болбат").
            3. Определи дату мероприятия (если указана, иначе null).
            
            Ответь строго в формате JSON:
            {
                "category": "НИРС",
                "students": ["Иванов И.И.", "Петров П.П."],
                "eventDate": "2026-03-25",
                "confidence": 0.95
            }
            
            Если категория не определена, поставь "ПРОЧЕЕ".
            Если студентов нет, верни пустой массив.
            Если даты нет, верни null.
            """, text);
    }

    private AiResult parseAiResponse(String jsonResponse, String originalText) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);

            String categoryStr = root.path("category").asText("ПРОЧЕЕ");
            Category category = mapCategory(categoryStr);

            List<String> studentNames = new ArrayList<>();
            JsonNode studentsNode = root.path("students");
            if (studentsNode.isArray()) {
                for (JsonNode node : studentsNode) {
                    studentNames.add(node.asText());
                }
            }

            String eventDate = root.path("eventDate").asText(null);
            double confidence = root.path("confidence").asDouble(0.5);

            List<StudentEntity> foundStudents = findStudentsByName(studentNames);

            return new AiResult(category, foundStudents, eventDate, confidence);

        } catch (Exception e) {
            log.warning("Failed to parse AI response, using fallback: " + e.getMessage());
            return classifyWithRules(originalText);
        }
    }

    private AiResult classifyWithRules(String text) {
        String lowerText = text.toLowerCase();

        Map<Category, Integer> scores = new HashMap<>();
        for (Category cat : Category.values()) {
            scores.put(cat, 0);
        }

        for (Map.Entry<Category, List<String>> entry : keywords.entrySet()) {
            int score = 0;
            for (String keyword : entry.getValue()) {
                if (lowerText.contains(keyword.toLowerCase())) {
                    score++;
                }
            }
            scores.put(entry.getKey(), score);
        }

        Category bestCategory = Category.OTHER;
        int maxScore = 0;
        for (Map.Entry<Category, Integer> entry : scores.entrySet()) {
            if (entry.getValue() > maxScore) {
                maxScore = entry.getValue();
                bestCategory = entry.getKey();
            }
        }

        List<StudentEntity> foundStudents = extractStudentNames(text);
        double confidence = maxScore > 0 ? Math.min(0.5 + (maxScore * 0.1), 0.9) : 0.3;

        return new AiResult(bestCategory, foundStudents, null, confidence);
    }

    private List<StudentEntity> extractStudentNames(String text) {
        List<StudentEntity> found = new ArrayList<>();

        // ===== ВСЕ ПАТТЕРНЫ ДЛЯ ПОИСКА СТУДЕНТОВ =====
        List<Pattern> patterns = List.of(

                // === ФОРМАТ: ФАМИЛИЯ + ИМЯ + ОТЧЕСТВО ===
                // "Иванов Иван Иванович"
                Pattern.compile("[А-Я][а-я]+\\s[А-Я][а-я]+\\s[А-Я][а-я]+"),

                // === ФОРМАТ: ФАМИЛИЯ + ИНИЦИАЛЫ ===
                // "Иванов И.И."
                Pattern.compile("[А-Я][а-я]+\\s[А-Я]\\.[А-Я]\\."),

                // === ФОРМАТ: ФАМИЛИЯ + ИМЯ ===
                // "Иванов Иван"
                Pattern.compile("[А-Я][а-я]+\\s[А-Я][а-я]+"),

                // === ФОРМАТ: ИМЯ + ФАМИЛИЯ ===
                // "Полина Болбат", "Глеб Шадура"
                // === ФОРМАТ: ИМЯ + ФАМИЛИЯ ===
// "Полина Болбат", "Глеб Шадура"
// Используем простое совпадение без lookbehind, затем фильтруем дубликаты
                Pattern.compile("[А-Я][а-я]+\\s[А-Я][а-я]+"),

                // === С ПРИПИСКОЙ "студент" + ФАМИЛИЯ + ИНИЦИАЛЫ ===
                // "студент Иванов И.И."
                Pattern.compile("(?i)студент\\s+[А-Я][а-я]+\\s[А-Я]\\.[А-Я]\\."),

                // === С ПРИПИСКОЙ "студент" + ФАМИЛИЯ + ИМЯ ===
                // "студент Иванов Иван"
                Pattern.compile("(?i)студент\\s+[А-Я][а-я]+\\s[А-Я][а-я]+"),

                // === С ПРИПИСКОЙ "студентка" + ИМЯ + ФАМИЛИЯ ===
                // "студентка Полина Болбат"
                Pattern.compile("(?i)студентка\\s+[А-Я][а-я]+\\s[А-Я][а-я]+"),

                // === С ПРИПИСКОЙ "студент" + ИМЯ + ФАМИЛИЯ ===
                // "студент Глеб Шадура"
                Pattern.compile("(?i)студент\\s+[А-Я][а-я]+\\s[А-Я][а-я]+"),

                // === ИМЯ + ФАМИЛИЯ + ГЛАГОЛ ===
                // "Полина Болбат стала", "Глеб Шадура принял участие"
                Pattern.compile("[А-Я][а-я]+\\s[А-Я][а-я]+\\s(?:стала|стал|приняла|принял|выступила|выступил|получила|получил|победила|победил)")
        );

        List<String> foundNames = new ArrayList<>();

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                String name = matcher.group();

                // Очищаем от слов "студент", "студентка"
                name = name.replaceAll("(?i)студент\\s+", "")
                        .replaceAll("(?i)студентка\\s+", "");

                // Очищаем от глаголов в конце
                name = name.replaceAll("\\s(?:стала|стал|приняла|принял|выступила|выступил|получила|получил|победила|победил).*$", "");

                // Оставляем только два слова (если больше)
                String[] parts = name.split("\\s+");
                if (parts.length >= 2) {
                    name = parts[0] + " " + parts[1];
                }

                foundNames.add(name);
            }
        }

        // Удаляем дубликаты
        foundNames = foundNames.stream().distinct().collect(Collectors.toList());

        return findStudentsByName(foundNames);
    }

    private List<StudentEntity> findStudentsByName(List<String> names) {
        List<StudentEntity> result = new ArrayList<>();

        for (String name : names) {
            StudentEntity student = null;
            String[] parts = name.split("\\s+");

            if (parts.length == 2) {
                String part1 = parts[0];
                String part2 = parts[1];

                // Вариант 1: "Иванов Иван" (Фамилия + Имя)
                student = studentRepository.findByLastNameAndFirstName(part1, part2).orElse(null);

                // Вариант 2: "Иван Иванов" (Имя + Фамилия)
                if (student == null) {
                    student = studentRepository.findByFirstNameAndLastName(part1, part2).orElse(null);
                }

                // Вариант 3: "Иванов И.И." (Фамилия + Инициалы)
                if (student == null && part2.matches("[А-Я]\\.[А-Я]\\.")) {
                    String initial = part2.substring(0, 1);
                    student = studentRepository.findByLastNameAndFirstNameStartingWith(part1, initial).orElse(null);
                }

                // Вариант 4: Поиск по полному имени (содержит)
                if (student == null) {
                    student = studentRepository.findByFullNameContainingIgnoreCase(name).orElse(null);
                }
            }

            if (parts.length == 3) {
                // "Иванов Иван Иванович" (Фамилия + Имя + Отчество)
                student = studentRepository.findByLastNameAndFirstName(parts[0], parts[1]).orElse(null);
            }

            if (student != null && !result.contains(student)) {
                result.add(student);
                log.info("✅ Found student: " + student.getFullName() + " from name: " + name);
            }
        }

        return result;
    }

    private Category mapCategory(String categoryStr) {
        return switch (categoryStr.toUpperCase()) {
            case "НИРС" -> Category.RESERCH_WORK;
            case "СПОРТ" -> Category.SPORT;
            case "ОБЩЕСТВЕННАЯ_ДЕЯТЕЛЬНОСТЬ" -> Category.SOCIAL_ACTIVITY;
            case "ИДЕОЛОГИЧЕСКОЕ_ВОСПИТАНИЕ" -> Category.IDEOLOGICAL_EDUCATION;
            default -> Category.OTHER;
        };
    }

    public static class AiResult {
        private final Category category;
        private final List<StudentEntity> students;
        private final String eventDate;
        private final double confidence;

        public AiResult(Category category, List<StudentEntity> students, String eventDate, double confidence) {
            this.category = category;
            this.students = students;
            this.eventDate = eventDate;
            this.confidence = confidence;
        }

        public Category getCategory() { return category; }
        public List<StudentEntity> getStudents() { return students; }
        public String getEventDate() { return eventDate; }
        public double getConfidence() { return confidence; }
    }
}