package com.srip.repository;

import com.srip.domain.AiFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiFeedbackRepository extends JpaRepository<AiFeedback, Long> {

    /** Most recent student- or parent-facing document, or empty if never generated. */
    Optional<AiFeedback> findFirstByStudentIdAndExamIdAndAudienceOrderByGeneratedAtDesc(
            Long studentId, Long examId, AiFeedback.Audience audience);

    /** Most recent teacher-facing document for a class. */
    Optional<AiFeedback> findFirstByClassNameAndExamIdAndAudienceOrderByGeneratedAtDesc(
            String className, Long examId, AiFeedback.Audience audience);

    List<AiFeedback> findByStudentIdOrderByGeneratedAtDesc(Long studentId);
}
