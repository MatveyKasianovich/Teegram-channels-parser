package com.example.demo.dto;

import com.example.demo.entity.Category;
import java.time.LocalDate;
import java.util.List;

public record DTOtoFRONT(
        LocalDate date,
        Category category,
        String description,
        String postLink,
        List<StudentInfoDto> students
) {}