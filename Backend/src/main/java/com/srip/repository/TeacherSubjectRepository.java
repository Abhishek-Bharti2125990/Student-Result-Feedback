package com.srip.repository;

import com.srip.domain.TeacherSubject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TeacherSubjectRepository extends JpaRepository<TeacherSubject, Long> {

    @Query("select ts from TeacherSubject ts join fetch ts.subject where ts.teacher.id = :teacherId")
    List<TeacherSubject> findByTeacherId(@Param("teacherId") Long teacherId);

    long countByTeacherId(Long teacherId);

    /**
     * Clears a teacher's assignments so the teacher row itself can be deleted:
     * {@code teacher_subjects} holds a foreign key onto it.
     */
    @Modifying
    @Query("delete from TeacherSubject ts where ts.teacher.id = :teacherId")
    int deleteByTeacherId(@Param("teacherId") Long teacherId);
}
