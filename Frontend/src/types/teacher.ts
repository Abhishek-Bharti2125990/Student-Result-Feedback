import type { CategoryCount, ClassTopicWeakness, ScoreCategory } from './common';
import type { FeedbackEnvelope } from './student';

/**
 * One student as the teacher dashboard lists them.
 *
 * The score, the weak topics and a suggested action are on the card itself:
 * the teacher's first question is "who needs what, and what do I do on
 * Monday?", and making them open a profile to find out answers nothing.
 */
export interface StudentCategoryCard {
    studentId: number;
    admissionNo?: string;
    studentName: string;
    className: string;
    section?: string;
    percentage: number;
    grade: string;
    category: ScoreCategory;
    rankInClass: number;
    weakTopics: string[];
    weakSubjects: string[];
    strongTopics: string[];
    suggestedAction: string;
}

export interface CategoryBucket {
    category: ScoreCategory;
    label: string;
    studentCount: number;
    students: StudentCategoryCard[];
}

export interface TeacherWeakStudentNote {
    studentName: string;
    concern: string;
    suggestedAction: string;
    priority: string;
}

export interface TeacherFeedback {
    classSummary: string;
    weakStudents: TeacherWeakStudentNote[];
    interventionSuggestions: string[];
    remedialRecommendations: string[];
    topicsToReteach: string[];
}

export type TeacherFeedbackEnvelope = FeedbackEnvelope<TeacherFeedback>;

/** `GET /api/teacher/dashboard` */
export interface TeacherDashboard {
    className: string;
    examId: number;
    examName: string;
    totalStudents: number;
    classAveragePercentage: number;
    highestPercentage: number;
    lowestPercentage: number;
    categoryCounts: CategoryCount[];
    buckets: CategoryBucket[];
    weakestTopics: ClassTopicWeakness[];
    /** Absent until the import job has written a document for this class. */
    aiGuidance?: TeacherFeedbackEnvelope;
    generatedAt: string;
}

/** Path segment of `/api/teacher/students/{segment}`. */
export type CategorySegment = 'critical' | 'average' | 'good' | 'excellent';

export const CATEGORY_SEGMENTS: Record<ScoreCategory, CategorySegment> = {
    CRITICAL: 'critical',
    AVERAGE: 'average',
    GOOD: 'good',
    EXCELLENT: 'excellent',
};
