import type { ClassAnalytics, PerformanceTrend } from '@/types/analytics';
import { api } from './client';

export const analyticsApi = {
    /**
     * The calling student's own trend across every exam they have sat.
     *
     * `/me/**` resolves the student from the token, so there is no id to pass
     * and no id a client could tamper with. Only a STUDENT login can use it -
     * the backend rejects a staff token here with 400, because a teacher has no
     * trend of their own.
     */
    myTrend(): Promise<PerformanceTrend> {
        return api.get<PerformanceTrend>('/analytics/me/trend').then((r) => r.data);
    },

    /**
     * One student's trend, for staff. Used for the "Trend" line on each card in
     * the teacher's category lists; the backend checks the caller may read them.
     */
    studentTrend(studentId: number): Promise<PerformanceTrend> {
        return api.get<PerformanceTrend>(`/analytics/students/${studentId}/trend`).then((r) => r.data);
    },

    /**
     * Class-wide figures for one exam. Staff only.
     *
     * Fetched alongside the teacher dashboard for one reason: per-subject class
     * averages (`subjectStats`) are not part of the dashboard payload.
     */
    classReport(className: string, examId: number): Promise<ClassAnalytics> {
        return api
            .get<ClassAnalytics>(`/analytics/class/${encodeURIComponent(className)}/exams/${examId}`)
            .then((r) => r.data);
    },
};
