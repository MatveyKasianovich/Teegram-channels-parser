package com.example.demo.repository;

import com.example.demo.entity.StudentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface StudentRepository extends JpaRepository<StudentEntity, Long> {
    Optional<StudentEntity> findByFullNameContainingIgnoreCase(String fullName);
    Optional<StudentEntity> findByLastNameAndFirstName(String lastName, String firstName);
    Optional<StudentEntity> findByFirstNameAndLastName(String firstName, String lastName);
    Optional<StudentEntity> findByLastNameAndFirstNameStartingWith(String lastName, String firstNameInitial);
    List<StudentEntity> findByLastNameContainingIgnoreCase(String lastName);
    List<StudentEntity> findByFirstNameContainingIgnoreCase(String firstName);
}