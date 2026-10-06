package com.srip.repository;

import com.srip.domain.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    Optional<Teacher> findByUserId(Long userId);

    Optional<Teacher> findByStaffNo(String staffNo);

    /** Loaded in one query by the admin user list, to avoid a lookup per row. */
    List<Teacher> findByUserIdIn(List<Long> userIds);
}
