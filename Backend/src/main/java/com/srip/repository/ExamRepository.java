package com.srip.repository;

import com.srip.domain.Exam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExamRepository extends JpaRepository<Exam, Long> {

    Optional<Exam> findByCode(String code);

    List<Exam> findByClassNameOrderByExamDateAsc(String className);

    List<Exam> findAllByOrderByExamDateAsc();
}
