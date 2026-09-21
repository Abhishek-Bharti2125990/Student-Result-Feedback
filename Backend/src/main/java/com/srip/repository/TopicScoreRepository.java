package com.srip.repository;

import com.srip.domain.TopicScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TopicScoreRepository extends JpaRepository<TopicScore, Long> {

    /**
     * Every topic score a student has ever recorded, with subject and exam
     * eagerly joined. Weak-topic detection needs all of it at once, so one
     * query with fetch joins beats walking lazy associations per row.
     */
    @Query("""
            select ts from TopicScore ts
            join fetch ts.topic t
            join fetch t.subject
            join fetch ts.examResult r
            join fetch r.exam
            where r.student.id = :studentId
            """)
    List<TopicScore> findAllForStudent(@Param("studentId") Long studentId);

    @Query("""
            select ts from TopicScore ts
            join fetch ts.topic t
            join fetch t.subject
            join fetch ts.examResult r
            join fetch r.exam
            where r.student.id = :studentId and r.exam.id = :examId
            """)
    List<TopicScore> findForStudentAndExam(@Param("studentId") Long studentId, @Param("examId") Long examId);
}
