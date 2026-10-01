/**
 * Shapes shared by more than one API.
 *
 * Every type in `src/types` mirrors a record in the Spring backend. Jackson is
 * configured with `default-property-inclusion: non_null`, so **any nullable
 * field is absent from the JSON rather than null** - which is why so much here
 * is marked optional. Treating an optional field as guaranteed is the single
 * easiest way to crash a page in this app.
 */

/** The three roles the backend supports. There is deliberately no PARENT. */
export type Role = 'STUDENT' | 'TEACHER' | 'ADMIN';

/** Score bands the dashboards are organised around. */
export type ScoreCategory = 'CRITICAL' | 'AVERAGE' | 'GOOD' | 'EXCELLENT';

/** Who produced a feedback document: the model, or the local rule-based writer. */
export type FeedbackSource = 'CLAUDE' | 'FALLBACK';

export type ResourceType = 'BOOK' | 'VIDEO';

/** The error body produced by the backend's `GlobalExceptionHandler`. */
export interface ApiErrorBody {
    timestamp: string;
    status: number;
    error: string;
    message: string;
    details?: string[];
}

/** One subject's result inside one exam. */
export interface SubjectPerformance {
    subjectId: number;
    subjectCode: string;
    subjectName: string;
    marksObtained: number;
    maxMarks: number;
    percentage: number;
    grade: string;
    /** Absent when the class average could not be computed. */
    classAverage?: number;
    /** This student minus the class average; negative means below peers. */
    deltaVsClass?: number;
}

export interface ResourceSuggestion {
    subject: string;
    topic: string;
    resourceType: ResourceType;
    title: string;
    /** Absent for a printed book, which has no link. Never render a bare `#`. */
    url?: string;
}

/** A strength or weakness as the student dashboard returns it. */
export interface TopicHighlight {
    /** Absent when the topic has no mark in the exam being shown. */
    subject?: string;
    chapter?: string;
    topic: string;
    percentage: number;
    /** Glyph chosen by the backend, so a tick never lands on a weakness. */
    marker: string;
}

export interface CategoryCount {
    category: ScoreCategory;
    label: string;
    students: number;
}

/** A topic the class as a whole lost marks on. */
export interface ClassTopicWeakness {
    subjectCode: string;
    subjectName: string;
    chapterName?: string;
    topicName: string;
    classAveragePercentage: number;
    weakStudents: number;
}

export interface WeakTopic {
    subjectCode: string;
    topicName: string;
    averagePercentage: number;
    /** How many papers it was weak in; a repeat gap is a different problem. */
    occurrences: number;
}

export interface StrongTopic {
    subjectCode: string;
    topicName: string;
    averagePercentage: number;
}
