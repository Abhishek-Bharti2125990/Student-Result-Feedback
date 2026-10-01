import type {
    CategorySegment,
    StudentCategoryCard,
    TeacherDashboard,
} from '@/types/teacher';
import { api } from './client';

/**
 * Both filters are optional. After a single upload neither has to be supplied -
 * the backend resolves the only class with data and its most recent exam.
 */
export interface TeacherQuery {
    className?: string;
    examId?: number;
}

function params(query: TeacherQuery) {
    return {
        params: {
            ...(query.className ? { className: query.className } : {}),
            ...(query.examId !== undefined ? { examId: query.examId } : {}),
        },
    };
}

export const teacherApi = {
    dashboard(query: TeacherQuery = {}): Promise<TeacherDashboard> {
        return api
            .get<TeacherDashboard>('/teacher/dashboard', params(query))
            .then((r) => r.data);
    },

    /** One score bucket on its own, worst score first. */
    studentsInCategory(
        segment: CategorySegment,
        query: TeacherQuery = {},
    ): Promise<StudentCategoryCard[]> {
        return api
            .get<StudentCategoryCard[]>(`/teacher/students/${segment}`, params(query))
            .then((r) => r.data);
    },
};
