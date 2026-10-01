import type { ResourceSuggestion } from '@/types/common';
import type { StudentDashboard, StudentFeedbackEnvelope } from '@/types/student';
import { api } from './client';

/**
 * `studentId` and `examId` are both optional on every route.
 *
 * Omitting `examId` means "the latest exam", which is what a student asking
 * "how did I do?" means. `studentId` is only for staff viewing one student's
 * card; a student passing someone else's is rejected with 403 by the backend.
 */
export interface StudentQuery {
    studentId?: number;
    examId?: number;
}

function params(query: StudentQuery, extra: Record<string, unknown> = {}) {
    return {
        params: {
            ...(query.studentId !== undefined ? { studentId: query.studentId } : {}),
            ...(query.examId !== undefined ? { examId: query.examId } : {}),
            ...extra,
        },
    };
}

export const studentApi = {
    dashboard(query: StudentQuery = {}): Promise<StudentDashboard> {
        return api
            .get<StudentDashboard>('/student/dashboard', params(query))
            .then((r) => r.data);
    },

    /**
     * @param refresh regenerates the document - a billed model call, so it is
     *                only ever sent when the user explicitly asks for it
     */
    feedback(query: StudentQuery = {}, refresh = false): Promise<StudentFeedbackEnvelope> {
        return api
            .get<StudentFeedbackEnvelope>('/student/feedback', params(query, { refresh }))
            .then((r) => r.data);
    },

    resources(query: StudentQuery = {}): Promise<ResourceSuggestion[]> {
        return api
            .get<ResourceSuggestion[]>('/student/resources', params(query))
            .then((r) => r.data);
    },
};
