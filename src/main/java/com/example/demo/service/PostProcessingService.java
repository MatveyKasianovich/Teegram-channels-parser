package com.example.demo.service;

import com.example.demo.dto.DTOtoFRONT;
import com.example.demo.dto.StudentInfoDto;
import com.example.demo.entity.EventEntity;
import com.example.demo.entity.PostEntity;
import com.example.demo.repository.EventRepository;
import com.example.demo.repository.PostRepository;
import com.example.demo.service.AiClassificationService.AiResult;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class PostProcessingService {

    private static final Logger log = Logger.getLogger(PostProcessingService.class.getName());

    private final PostRepository postRepository;
    private final EventRepository eventRepository;
    private final AiClassificationService aiService;

    public PostProcessingService(PostRepository postRepository,
                                 EventRepository eventRepository,
                                 AiClassificationService aiService) {
        this.postRepository = postRepository;
        this.eventRepository = eventRepository;
        this.aiService = aiService;
    }

    @Transactional
    public int processUnprocessedPosts() {
        var unprocessed = postRepository.findByIsProcessedFalse();
        int count = 0;

        for (PostEntity post : unprocessed) {
            try {
                processPost(post);
                count++;
            } catch (Exception e) {
                log.severe("Failed to process post " + post.getId() + ": " + e.getMessage());
            }
        }

        log.info("Processed " + count + " posts with AI");
        return count;
    }

    // В PostProcessingService.java - замените метод getLastProcessedEvents на этот:
    public List<DTOtoFRONT> getLastProcessedEvents(int days) {
        LocalDate fromDate = LocalDate.now().minusDays(days);

        // Просто берем последние 30 событий без всяких фильтров
        var events = eventRepository.findAll(PageRequest.of(0, 30)).getContent();

        return events.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private void processPost(PostEntity post) {
        String text = post.getText();
        if (text == null || text.trim().isEmpty()) {
            post.setProcessed(true);
            postRepository.save(post);
            return;
        }

        AiResult result = aiService.classify(text);

        if (eventRepository.existsByPostId(post.getId())) {
            post.setProcessed(true);
            postRepository.save(post);
            return;
        }

        EventEntity event = new EventEntity();
        event.setPostId(post.getId());
        event.setCategory(result.getCategory());
        event.setDescription(truncateDescription(text, 500));
        event.setConfidence(BigDecimal.valueOf(result.getConfidence()));
        event.setCreatedAt(LocalDateTime.now());

        String postLink = String.format("https://t.me/%s/%d",
                post.getChannel().getName(),
                post.getTelegramId());
        event.setPostLink(postLink);

        if (result.getEventDate() != null) {
            try {
                event.setEventDate(LocalDate.parse(result.getEventDate()));
            } catch (Exception e) {
                event.setEventDate(post.getPublishedAt().toLocalDate());
            }
        } else {
            event.setEventDate(post.getPublishedAt().toLocalDate());
        }

        event.setStudents(result.getStudents());
        eventRepository.save(event);

        post.setProcessed(true);
        postRepository.save(post);

        log.info("Post " + post.getId() + " classified as " + result.getCategory() +
                " with confidence " + result.getConfidence() +
                ", students: " + result.getStudents().size());
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

    private String truncateDescription(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}