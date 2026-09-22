package com.srip.repository;

import com.srip.domain.TeacherSubject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TeacherSubjectRepository extends JpaRepository<TeacherSubject, Long> {

    @Query("select ts from TeacherSubject ts join fetch ts.subject where ts.teacher.id = :teacherId")
    List<TeacherSubject> findByTeacherId(@Param("teacherId") Long teacherId);
}
