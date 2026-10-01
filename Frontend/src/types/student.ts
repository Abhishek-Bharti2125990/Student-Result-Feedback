import type {
    FeedbackSource,
    ResourceSuggestion,
    ScoreCategory,
    SubjectPerformance,
    TopicHighlight,
} from './common';

/** `GET /api/student/dashboard` */
export interface StudentDashboard {
    studentId: number;
    admissionNo: string;
    studentName: string;
    className: string;
    section?: string;
    examId: number;
    examName: string;
    percentage: number;
    grade: string;
    category: ScoreCategory;
    categoryLabel: string;
    passed: boolean;
    rankInClass: number;
    classSize: number;
    classAveragePercentage: number;
    subjects: SubjectPerformance[];
    strongTopics: TopicHighlight[];
    weakTopics: TopicHighlight[];
    recommendedResources: ResourceSuggestion[];
}

/** One item of the AI study plan. */
export interface StudyPlanItem {
    subject: string;
    topic: string;
    action: string;
    timeframe: string;
    dailyMinutes: number;
    /** A title from the school's vetted library, or absent if none covers it. */
    resource?: string;
}

export interface StudentFeedback {
    summary: string;
    strengths: string[];
    weaknesses: string[];
    studyPlan: StudyPlanItem[];
    motivationalNote: string;
}

/**
 * Wraps a generated document with its provenance.
 *
 * `feedback` is typed by the caller: the student endpoint returns
 * {@link StudentFeedback}, the teacher one returns `TeacherFeedback`.
 */
export interface FeedbackEnvelope<T> {
    audience: string;
    model: string;
    source: FeedbackSource;
    generatedAt: string;
    /** True when this was read back from storage rather than generated now. */
    cached: boolean;
    feedback: T;
}

export type StudentFeedbackEnvelope = FeedbackEnvelope<StudentFeedback>;

export type { ResourceSuggestion };
