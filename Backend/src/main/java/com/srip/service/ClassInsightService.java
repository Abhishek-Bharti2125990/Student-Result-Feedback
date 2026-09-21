package com.srip.service;

import com.srip.analytics.GradingService;
import com.srip.analytics.RankingService;
import com.srip.analytics.WeaknessDetector;
import com.srip.config.CacheConfig;
import com.srip.domain.Exam;
import com.srip.domain.ExamResult;
import com.srip.domain.Student;
import com.srip.dto.analytics.AnalyticsDtos.ClassAnalytics;
import com.srip.dto.analytics.AnalyticsDtos.RankingEntry;
import com.srip.dto.analytics.AnalyticsDtos.StrugglingStudent;
import com.srip.dto.analytics.AnalyticsDtos.SubjectStat;
import com.srip.dto.analytics.AnalyticsDtos.WeakSubject;
import com.srip.exception.ApiExceptions;
import com.srip.repository.ExamRepository;
import com.srip.repository.ExamResultRepository;
import com.srip.repository.StudentRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Class-wide analytics: rankings and the teacher-facing exam overview.
 *
 * <p>These live in their own bean rather than on {@code AnalyticsService} for a
 * concrete reason: Spring's caching works through a proxy, so a cached method
 * called from a sibling method of the same class would silently bypass the
 * cache. {@code AnalyticsService} needs rankings while building a single
 * student's report, so that call has to cross a bean boundary to be cached.
 *
 * <p>A ranking table is identical for everyone in the class who asks for it,
 * which is what makes it worth caching at all.
 */
@Service
public class ClassInsightService {

    private final StudentRepository students;
    private final ExamRepository exams;
    private final ExamResultRepository examResults;
    private final GradingService grading;
    private final RankingService ranking;
    private final WeaknessDetector weaknessDetector;

    public ClassInsightService(StudentRepository students,
                               ExamRepository exams,
                               ExamResultRepository examResults,
                               GradingService grading,
                               RankingService ranking,
                               WeaknessDetector weaknessDetector) {
        this.students = students;
        this.exams = exams;
        this.examResults = examResults;
        this.grading = grading;
        this.ranking = ranking;
        this.weaknessDetector = weaknessDetector;
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_RANKINGS, key = "#examId + ':' + #className")
    @Transactional(readOnly = true)
    public List<RankingEntry> rankings(Long examId, String className) {
        List<ExamResultRepository.StudentTotals> totals =
                examResults.aggregateTotalsForExamAndClass(examId, className);
        if (totals.isEmpty()) {
            return List.of();
        }
        Map<Long, Student> studentsById = students.findAllById(
                        totals.stream().map(ExamResultRepository.StudentTotals::getStudentId).toList())
                .stream()
                .collect(Collectors.toMap(Student::getId, Function.identity()));
        return ranking.rank(totals, studentsById);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_CLASS_ANALYTICS, key = "#examId + ':' + #className")
    @Transactional(readOnly = true)
    public ClassAnalytics classAnalytics(Long examId, String className) {
        Exam exam = exams.findById(examId)
                .orElseThrow(() -> ApiExceptions.NotFoundException.of("Exam", examId));

        List<ExamResult> results = examResults.findForExamAndClass(examId, className);
        if (results.isEmpty()) {
            throw new ApiExceptions.NotFoundException(
                    "No results recorded for class " + className + " in exam " + exam.getCode());
        }

        List<RankingEntry> rankings = rankings(examId, className);
        List<SubjectStat> subjectStats = subjectStats(results);
        List<StrugglingStudent> struggling = strugglingStudents(results, rankings);

        BigDecimal classAverage = average(rankings.stream().map(RankingEntry::percentage).toList());
        BigDecimal highest = rankings.isEmpty() ? BigDecimal.ZERO : rankings.get(0).percentage();
        BigDecimal lowest = rankings.isEmpty()
                ? BigDecimal.ZERO
                : rankings.get(rankings.size() - 1).percentage();

        return new ClassAnalytics(
                exam.getId(),
                exam.getCode(),
                exam.getName(),
                className,
                rankings.size(),
                classAverage,
                highest,
                lowest,
                rankings,
                subjectStats,
                struggling);
    }

    /** Class-wide mean per subject for one exam, keyed by subject id. */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> subjectAverages(Long examId, String className) {
        Map<Long, BigDecimal> averages = new HashMap<>();
        for (ExamResultRepository.SubjectAverage row : examResults.classSubjectAverages(examId, className)) {
            double value = row.getAveragePercentage() == null ? 0d : row.getAveragePercentage();
            averages.put(row.getSubjectId(), BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP));
        }
        return averages;
    }

    /**
     * Called after a CSV import changes the marks behind these views. Without
     * it, a freshly uploaded exam would keep serving the previous rankings.
     */
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.CACHE_RANKINGS, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.CACHE_CLASS_ANALYTICS, allEntries = true)
    })
    public void invalidateCaches() {
        // The annotations do the work; this method is the hook they hang on.
    }

    private List<SubjectStat> subjectStats(List<ExamResult> results) {
        Map<String, List<ExamResult>> bySubject = results.stream()
                .collect(Collectors.groupingBy(result -> result.getSubject().getCode(),
                        LinkedHashMap::new, Collectors.toList()));

        List<SubjectStat> stats = new ArrayList<>(bySubject.size());
        for (Map.Entry<String, List<ExamResult>> entry : bySubject.entrySet()) {
            List<BigDecimal> percentages = entry.getValue().stream()
                    .map(ExamResult::getPercentage)
                    .toList();
            long passCount = percentages.stream().filter(grading::isPass).count();
            stats.add(new SubjectStat(
                    entry.getKey(),
                    entry.getValue().get(0).getSubject().getName(),
                    average(percentages),
                    percentages.stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO),
                    percentages.stream().min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO),
                    passCount,
                    percentages.size() - passCount));
        }
        // Weakest subject first: that is the one needing a decision.
        stats.sort(Comparator.comparing(SubjectStat::average));
        return stats;
    }

    private List<StrugglingStudent> strugglingStudents(List<ExamResult> results, List<RankingEntry> rankings) {
        Map<Long, List<ExamResult>> byStudent = results.stream()
                .collect(Collectors.groupingBy(result -> result.getStudent().getId()));
        Map<Long, RankingEntry> rankByStudent = rankings.stream()
                .collect(Collectors.toMap(RankingEntry::studentId, Function.identity()));

        List<StrugglingStudent> struggling = new ArrayList<>();
        for (Map.Entry<Long, List<ExamResult>> entry : byStudent.entrySet()) {
            RankingEntry rankEntry = rankByStudent.get(entry.getKey());
            if (rankEntry == null) {
                continue;
            }
            List<WeakSubject> weak = weaknessDetector.detectWeakSubjects(entry.getValue());
            boolean failing = !grading.isPass(rankEntry.percentage());
            if (!failing && weak.isEmpty()) {
                continue;
            }
            Student student = entry.getValue().get(0).getStudent();
            struggling.add(new StrugglingStudent(
                    student.getId(),
                    student.getAdmissionNo(),
                    student.getFullName(),
                    rankEntry.percentage(),
                    rankEntry.rank(),
                    weak.stream().map(WeakSubject::subjectCode).toList()));
        }
        struggling.sort(Comparator.comparing(StrugglingStudent::overallPercentage));
        return struggling;
    }

    static BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }
}
