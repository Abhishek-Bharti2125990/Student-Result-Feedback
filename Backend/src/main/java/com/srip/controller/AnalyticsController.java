package com.srip.controller;

import com.srip.dto.analytics.AnalyticsDtos.ClassAnalytics;
import com.srip.dto.analytics.AnalyticsDtos.ExamReport;
import com.srip.dto.analytics.AnalyticsDtos.PerformanceTrend;
import com.srip.dto.analytics.AnalyticsDtos.RankingEntry;
import com.srip.dto.analytics.AnalyticsDtos.WeakSubject;
import com.srip.dto.analytics.AnalyticsDtos.WeakTopic;
import com.srip.dto.result.ResultDtos.ExamResultView;
import com.srip.service.AccessGuard;
import com.srip.service.AnalyticsService;
import com.srip.service.ClassInsightService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read endpoints for the analytics engine.
 *
 * <p>Every student-scoped route calls {@link AccessGuard} before doing any
 * work. The URL rules in {@code SecurityConfig} can say "an authenticated user
 * may call this", but only the guard can say "<em>this</em> parent may read
 * <em>this</em> child".
 *
 * <p>The {@code /me/**} variants exist so a student or parent client never has
 * to know its own numeric id, which also removes the temptation to let the
 * client pass one.
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;
    private final ClassInsightService classInsights;
    private final AccessGuard accessGuard;

    public AnalyticsController(AnalyticsService analytics,
                               ClassInsightService classInsights,
                               AccessGuard accessGuard) {
        this.analytics = analytics;
        this.classInsights = classInsights;
        this.accessGuard = accessGuard;
    }

    // -- One student ---------------------------------------------------------

    @GetMapping("/students/{studentId}/exams/{examId}/report")
    public ExamReport report(@PathVariable Long studentId, @PathVariable Long examId) {
        accessGuard.assertCanReadStudent(studentId);
        return analytics.examReport(studentId, examId);
    }

    @GetMapping("/students/{studentId}/results")
    public List<ExamResultView> results(@PathVariable Long studentId) {
        accessGuard.assertCanReadStudent(studentId);
        return analytics.resultsOf(studentId);
    }

    @GetMapping("/students/{studentId}/trend")
    public PerformanceTrend trend(@PathVariable Long studentId) {
        accessGuard.assertCanReadStudent(studentId);
        return analytics.trendOf(studentId);
    }

    @GetMapping("/students/{studentId}/weak-subjects")
    public List<WeakSubject> weakSubjects(@PathVariable Long studentId) {
        accessGuard.assertCanReadStudent(studentId);
        return analytics.weakSubjectsOf(studentId);
    }

    @GetMapping("/students/{studentId}/weak-topics")
    public List<WeakTopic> weakTopics(@PathVariable Long studentId) {
        accessGuard.assertCanReadStudent(studentId);
        return analytics.weakTopicsOf(studentId);
    }

    // -- The caller's own data -----------------------------------------------

    @GetMapping("/me/exams/{examId}/report")
    public ExamReport myReport(@PathVariable Long examId) {
        return analytics.examReport(accessGuard.currentStudentIdOrFail(), examId);
    }

    @GetMapping("/me/results")
    public List<ExamResultView> myResults() {
        return analytics.resultsOf(accessGuard.currentStudentIdOrFail());
    }

    @GetMapping("/me/trend")
    public PerformanceTrend myTrend() {
        return analytics.trendOf(accessGuard.currentStudentIdOrFail());
    }

    // -- Class level (TEACHER and ADMIN only, enforced by URL rule) ----------

    @GetMapping("/class/{className}/exams/{examId}")
    public ClassAnalytics classReport(@PathVariable String className, @PathVariable Long examId) {
        return classInsights.classAnalytics(examId, className);
    }

    @GetMapping("/class/{className}/exams/{examId}/rankings")
    public List<RankingEntry> rankings(@PathVariable String className, @PathVariable Long examId) {
        return classInsights.rankings(examId, className);
    }

    /**
     * Rankings for a class, with the size of the leaderboard capped.
     *
     * @param top how many entries to return; useful for a dashboard widget
     */
    @GetMapping("/class/{className}/exams/{examId}/toppers")
    public List<RankingEntry> toppers(@PathVariable String className,
                                      @PathVariable Long examId,
                                      @RequestParam(defaultValue = "5") int top) {
        List<RankingEntry> rankings = classInsights.rankings(examId, className);
        return rankings.subList(0, Math.min(Math.max(top, 0), rankings.size()));
    }
}
