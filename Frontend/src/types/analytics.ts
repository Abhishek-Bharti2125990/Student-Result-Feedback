/** `GET /api/analytics/me/trend` and the per-student detail views. */

export type TrendDirection =
    | 'IMPROVING'
    | 'DECLINING'
    | 'STABLE'
    | 'INSUFFICIENT_DATA';

export interface TrendPoint {
    examCode: string;
    examName: string;
    examDate: string;
    percentage: number;
}

export interface SubjectTrend {
    subjectCode: string;
    subjectName: string;
    points: TrendPoint[];
    change: number;
    direction: TrendDirection;
}

export interface PerformanceTrend {
    /** Ordered by exam date, not by upload order. */
    overall: TrendPoint[];
    bySubject: SubjectTrend[];
    overallChange: number;
    overallDirection: TrendDirection;
}

/** One subject across a whole class, from `GET /api/analytics/class/…`. */
export interface SubjectStat {
    subjectCode: string;
    subjectName: string;
    average: number;
    highest: number;
    lowest: number;
    passCount: number;
    failCount: number;
}

/**
 * The teacher's class report.
 *
 * Only `subjectStats` is read by the UI - everything else the teacher dashboard
 * needs is already on `/api/teacher/dashboard`, and this call exists purely
 * because per-subject class figures are not on that payload.
 */
export interface ClassAnalytics {
    examId: number;
    examCode: string;
    examName: string;
    className: string;
    classSize: number;
    classAveragePercentage: number;
    highestPercentage: number;
    lowestPercentage: number;
    subjectStats: SubjectStat[];
}
