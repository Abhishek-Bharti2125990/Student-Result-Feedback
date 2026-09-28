package com.srip.repository;

import com.srip.analytics.ScoreCategory;
import com.srip.domain.StudentAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudentAnalyticsRepository extends JpaRepository<StudentAnalytics, Long> {

    /** How many students of a class fell into each category, counted in SQL. */
    interface CategoryTally {
        ScoreCategory getCategory();

        Long getStudents();
    }

    Optional<StudentAnalytics> findByStudentIdAndExamId(Long studentId, Long examId);

    /** The student's most recent computed standing, newest exam first. */
    @Query("""
            select a from StudentAnalytics a
            where a.studentId = :studentId
            order by a.computedAt desc
            """)
    List<StudentAnalytics> findLatestForStudent(@Param("studentId") Long studentId);

    List<StudentAnalytics> findByExamIdAndClassNameOrderByRankInClassAsc(Long examId, String className);

    /** One bucket of the teacher dashboard: worst score first, since that is the
     *  student the teacher should look at first. */
    List<StudentAnalytics> findByExamIdAndClassNameAndCategoryOrderByPercentageAsc(
            Long examId, String className, ScoreCategory category);

    @Query("""
            select a.category as category, count(a) as students
            from StudentAnalytics a
            where a.examId = :examId and a.className = :className
            group by a.category
            """)
    List<CategoryTally> tallyCategories(@Param("examId") Long examId,
                                        @Param("className") String className);

    /** The most recently computed exam for a class, used when no exam is named. */
    @Query("""
            select a.examId from StudentAnalytics a
            where a.className = :className
            group by a.examId
            order by max(a.computedAt) desc
            """)
    List<Long> findExamIdsForClassByRecency(@Param("className") String className);

    /** Every class that has computed analytics, so a teacher can be offered a
     *  sensible default without first picking one. */
    @Query("select distinct a.className from StudentAnalytics a order by a.className asc")
    List<String> findDistinctClassNames();
}
