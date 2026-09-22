package com.srip.repository;

import com.srip.domain.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByAdmissionNo(String admissionNo);

    Optional<Student> findByUserId(Long userId);

    List<Student> findByClassName(String className);

    List<Student> findByClassNameAndSection(String className, String section);
}
