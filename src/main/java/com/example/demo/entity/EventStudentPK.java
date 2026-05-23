package com.example.demo.entity;

import java.io.Serializable;
import java.util.Objects;

public class EventStudentPK implements Serializable {
    private Long eventId;
    private Long studentId;

    public EventStudentPK() {}

    public EventStudentPK(Long eventId, Long studentId) {
        this.eventId = eventId;
        this.studentId = studentId;
    }

    // Геттеры, сеттеры, equals, hashCode
    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventStudentPK that = (EventStudentPK) o;
        return Objects.equals(eventId, that.eventId) && Objects.equals(studentId, that.studentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, studentId);
    }
}