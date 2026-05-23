package com.example.demo.repository;

import com.example.demo.entity.Category;
import com.example.demo.entity.EventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<EventEntity, Long> {

    boolean existsByPostId(Long postId);

    // Упрощенный метод - без фильтрации
    default List<EventEntity> findWithFilters(Category category, LocalDate dateFrom, LocalDate dateTo, Pageable pageable) {
        return findAll(pageable).getContent();
    }

    @Query("SELECT e.category, COUNT(e) FROM EventEntity e GROUP BY e.category")
    List<Object[]> countByCategory();

    @Query("SELECT s, COUNT(e) FROM EventEntity e JOIN e.students s GROUP BY s.id ORDER BY COUNT(e) DESC")
    List<Object[]> findTopStudents(Pageable pageable);
}