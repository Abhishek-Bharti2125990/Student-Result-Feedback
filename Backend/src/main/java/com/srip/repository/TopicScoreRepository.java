package com.srip.repository;

import com.srip.domain.TopicScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TopicScoreRepository extends JpaRepository<TopicScore, Long> {

    /** Marks and maximum summed over one subject paper's topic rows. */
    interface Totals {
        BigDecimal getTotalMarks();

        BigDecimal getTotalMax();
    }

    /** Class-wide picture of one topic, for "25 students weak on this". */
    interface TopicTally {
        Long getTopicId();

        Double getAveragePercentage();

        Long getWeakStudents();
    }

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

    Optional<TopicScore> findByExamResultIdAndTopicId(Long examResultId, Long topicId);

    /**
     * The subject total, recomputed from its topic rows.
     *
     * <p>Summed in SQL rather than over the entity's collection because the
     * import writer calls this once per subject per chunk, and the rows it needs
     * may have been written by an earlier chunk that is no longer in the
     * persistence context.
     */
    @Query("""
            select sum(ts.marksObtained) as totalMarks, sum(ts.maxMarks) as totalMax
            from TopicScore ts
            where ts.examResult.id = :examResultId
            """)
    Totals sumForExamResult(@Param("examResultId") Long examResultId);

    /**
     * Per-topic class averages and weak-student counts for one exam.
     *
     * @param weakThreshold a student at or below this percentage counts as weak
     */
    @Query("""
            select t.id as topicId,
                   avg(ts.percentage) as averagePercentage,
                   sum(case when ts.percentage <= :weakThreshold then 1 else 0 end) as weakStudents
            from TopicScore ts
            join ts.topic t
            join ts.examResult r
            where r.exam.id = :examId and r.student.className = :className
            group by t.id
            """)
    List<TopicTally> tallyTopicsForExamAndClass(@Param("examId") Long examId,
                                                @Param("className") String className,
                                                @Param("weakThreshold") BigDecimal weakThreshold);

    /**
     * Clears the breakdown for a set of subject papers before an import writes
     * the new one. Without this, re-uploading a corrected file that drops a
     * topic would leave the old topic row behind and the subject total would
     * never agree with the file.
     */
    @Modifying
    @Query("delete from TopicScore ts where ts.examResult.id in :examResultIds")
    void deleteByExamResultIds(@Param("examResultIds") Collection<Long> examResultIds);
}
