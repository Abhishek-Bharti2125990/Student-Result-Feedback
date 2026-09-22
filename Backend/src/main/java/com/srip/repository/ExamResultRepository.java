package com.srip.repository;

import com.srip.domain.ExamResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ExamResultRepository extends JpaRepository<ExamResult, Long> {

    /**
     * Per-student totals for one exam, aggregated in the database.
     *
     * <p>Ranking a class of 40 over 5 subjects means 200 rows; summing them in
     * SQL and returning 40 is cheaper than loading all 200 into memory, and it
     * keeps the cost flat as classes grow.
     */
    interface StudentTotals {
        Long getStudentId();

        BigDecimal getTotalMarks();

        BigDecimal getTotalMax();

        Long getSubjectCount();
    }

    /** Class-wide mean per subject, used to spot a student below their peers. */
    interface SubjectAverage {
        Long getSubjectId();

        Double getAveragePercentage();
    }

    Optional<ExamResult> findByStudentIdAndExamIdAndSubjectId(Long studentId, Long examId, Long subjectId);

    @Query("""
            select r from ExamResult r
            join fetch r.subject
            join fetch r.exam
            where r.student.id = :studentId and r.exam.id = :examId
            order by r.subject.name asc
            """)
    List<ExamResult> findForStudentAndExam(@Param("studentId") Long studentId, @Param("examId") Long examId);

    @Query("""
            select r from ExamResult r
            join fetch r.subject
            join fetch r.exam e
            where r.student.id = :studentId
            order by e.examDate asc, r.subject.name asc
            """)
    List<ExamResult> findAllForStudent(@Param("studentId") Long studentId);

    @Query("""
            select r from ExamResult r
            join fetch r.subject
            join fetch r.student
            where r.exam.id = :examId and r.student.className = :className
            """)
    List<ExamResult> findForExamAndClass(@Param("examId") Long examId, @Param("className") String className);

    @Query("""
            select r.student.id as studentId,
                   sum(r.marksObtained) as totalMarks,
                   sum(r.maxMarks) as totalMax,
                   count(r) as subjectCount
            from ExamResult r
            where r.exam.id = :examId and r.student.className = :className
            group by r.student.id
            """)
    List<StudentTotals> aggregateTotalsForExamAndClass(@Param("examId") Long examId,
                                                       @Param("className") String className);

    @Query("""
            select r.subject.id as subjectId,
                   avg(r.percentage) as averagePercentage
            from ExamResult r
            where r.exam.id = :examId and r.student.className = :className
            group by r.subject.id
            """)
    List<SubjectAverage> classSubjectAverages(@Param("examId") Long examId,
                                              @Param("className") String className);

    boolean existsByExamIdAndStudentClassName(Long examId, String className);

    long countByUploadJobId(Long uploadJobId);
}
