package com.srip.service;

import com.srip.analytics.GradingService;
import com.srip.analytics.RankingService;
import com.srip.analytics.ScoreCategory;
import com.srip.analytics.WeaknessDetector;
import com.srip.config.AnalyticsProperties;
import com.srip.config.CacheConfig;
import com.srip.domain.Exam;
import com.srip.domain.ExamResult;
import com.srip.domain.Student;
import com.srip.domain.Topic;
import com.srip.dto.analytics.AnalyticsDtos.CategoryCount;
import com.srip.dto.analytics.AnalyticsDtos.ClassAnalytics;
import com.srip.dto.analytics.AnalyticsDtos.ClassTopicWeakness;
import com.srip.dto.analytics.AnalyticsDtos.RankingEntry;
import com.srip.dto.analytics.AnalyticsDtos.StrugglingStudent;
import com.srip.dto.analytics.AnalyticsDtos.SubjectStat;
import com.srip.dto.analytics.AnalyticsDtos.WeakSubject;
import com.srip.exception.ApiExceptions;
import com.srip.repository.ExamRepository;
import com.srip.repository.ExamResultRepository;
import com.srip.repository.StudentRepository;
import com.srip.repository.TopicRepository;
import com.srip.repository.TopicScoreRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Class-wide analytics: rankings, score-category counts, the topics the whole
 * class is losing marks on, and the teacher-facing exam overview.
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

    /** How many class-wide weak topics to surface; a longer list is not a plan. */
    private static final int MAX_WEAK_TOPICS = 10;

    private final StudentRepository students;
    private final ExamRepository exams;
    private final ExamResultRepository examResults;
    private final TopicScoreRepository topicScores;
    private final TopicRepository topics;
    private final GradingService grading;
    private final RankingService ranking;
    private final WeaknessDetector weaknessDetector;
    private final AnalyticsProperties analyticsProperties;

    public ClassInsightService(StudentRepository students,
                               ExamRepository exams,
                               ExamResultRepository examResults,
                               TopicScoreRepository topicScores,
                               TopicRepository topics,
                               GradingService grading,
                               RankingService ranking,
                               WeaknessDetector weaknessDetector,
                               AnalyticsProperties analyticsProperties) {
        this.students = students;
        this.exams = exams;
        this.examResults = examResults;
        this.topicScores = topicScores;
        this.topics = topics;
        this.grading = grading;
        this.ranking = ranking;
        this.weaknessDetector = weaknessDetector;
        this.analyticsProperties = analyticsProperties;
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
        List<CategoryCount> categoryCounts = categoryCounts(rankings);
        List<ClassTopicWeakness> weakestTopics = weakestTopics(examId, className);

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
                categoryCounts,
                rankings,
                subjectStats,
                weakestTopics,
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
     * The topics most of the class is losing marks on, worst first.
     *
     * <p>Ordered by how many students were weak on it rather than by the class
     * average, because the number of affected students is what turns a private
     * gap into a teaching issue: a topic 25 students failed needs a lesson, one
     * that two students failed needs two conversations.
     */
    @Transactional(readOnly = true)
    public List<ClassTopicWeakness> weakestTopics(Long examId, String className) {
        List<TopicScoreRepository.TopicTally> tallies = topicScores.tallyTopicsForExamAndClass(
                examId, className, analyticsProperties.weakTopicThreshold());
        if (tallies.isEmpty()) {
            return List.of();
        }

        Map<Long, Topic> topicsById = topics.findAllWithSubjectByIdIn(
                        tallies.stream().map(TopicScoreRepository.TopicTally::getTopicId).toList())
                .stream()
                .collect(Collectors.toMap(Topic::getId, Function.identity()));

        List<ClassTopicWeakness> weaknesses = new ArrayList<>();
        for (TopicScoreRepository.TopicTally tally : tallies) {
            long weakStudents = tally.getWeakStudents() == null ? 0L : tally.getWeakStudents();
            if (weakStudents == 0) {
                continue;
            }
            Topic topic = topicsById.get(tally.getTopicId());
            if (topic == null) {
                continue;
            }
            double average = tally.getAveragePercentage() == null ? 0d : tally.getAveragePercentage();
            weaknesses.add(new ClassTopicWeakness(
                    topic.getSubject().getCode(),
                    topic.getSubject().getName(),
                    topic.getChapterName(),
                    topic.getName(),
                    BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP),
                    weakStudents));
        }

        weaknesses.sort(Comparator.comparingLong(ClassTopicWeakness::weakStudents).reversed()
                .thenComparing(ClassTopicWeakness::classAveragePercentage));
        return weaknesses.size() > MAX_WEAK_TOPICS
                ? List.copyOf(weaknesses.subList(0, MAX_WEAK_TOPICS))
                : List.copyOf(weaknesses);
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

    /**
     * Counts per score category, in the enum's own order.
     *
     * <p>Every category is present even when empty: a dashboard that silently
     * omits "Students Below 50%" when there are none looks broken, and a teacher
     * cannot tell the difference between "nobody is failing" and "that panel did
     * not load".
     */
    private List<CategoryCount> categoryCounts(List<RankingEntry> rankings) {
        Map<ScoreCategory, Long> counts = new EnumMap<>(ScoreCategory.class);
        for (ScoreCategory category : ScoreCategory.values()) {
            counts.put(category, 0L);
        }
        for (RankingEntry entry : rankings) {
            counts.merge(grading.category(entry.percentage()), 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .map(entry -> new CategoryCount(entry.getKey(), entry.getKey().label(), entry.getValue()))
                .toList();
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
                    weak.stream().map(WeakSubject::subjectName).toList()));
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
