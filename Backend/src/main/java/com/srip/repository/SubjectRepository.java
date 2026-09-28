package com.srip.repository;

import com.srip.domain.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    /**
     * Subjects are looked up by the slug of their CSV name, never by the name
     * itself - see {@code CsvSlug}. That keeps "Mathematics" and " mathematics "
     * on the same row without a case-insensitive scan per imported line.
     */
    Optional<Subject> findByCode(String code);
}
