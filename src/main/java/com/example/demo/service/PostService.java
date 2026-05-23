package com.example.demo.service;

import com.example.demo.dto.DTOtoFRONT;
import com.example.demo.dto.StudentInfoDto;
import com.example.demo.entity.Category;
import com.example.demo.entity.EventEntity;
import com.example.demo.repository.EventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PostService {

    private final EventRepository eventRepository;

    public PostService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public List<DTOtoFRONT> searchEvents(Category category, Long studentId,
                                         LocalDate dateFrom, LocalDate dateTo,
                                         int page, int size) {
        // Временно игнорируем studentId
        var events = eventRepository.findWithFilters(
                category, dateFrom, dateTo, PageRequest.of(page, size));

        return events.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
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