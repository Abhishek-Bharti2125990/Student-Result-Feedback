import type { SubjectPerformance } from '@/types/common';

/**
 * Strong and weak subjects, derived on the client from `dashboard.subjects[]`.
 *
 * The backend's dashboard payload carries per-subject percentages but not a
 * strong/weak split at subject level, so the thresholds live here. They are
 * named constants rather than inline numbers because they appear in the UI copy
 * too - a card that says "at or above 75%" has to agree with the filter that
 * populated it.
 */
export const STRONG_SUBJECT_THRESHOLD = 75;
export const WEAK_SUBJECT_THRESHOLD = 50;

/** Subjects at or above {@link STRONG_SUBJECT_THRESHOLD}, best first. */
export function strongSubjects(subjects: SubjectPerformance[]): SubjectPerformance[] {
    return subjects
        .filter((subject) => subject.percentage >= STRONG_SUBJECT_THRESHOLD)
        .slice()
        .sort((a, b) => b.percentage - a.percentage);
}

/** Subjects below {@link WEAK_SUBJECT_THRESHOLD}, worst first. */
export function weakSubjects(subjects: SubjectPerformance[]): SubjectPerformance[] {
    return subjects
        .filter((subject) => subject.percentage < WEAK_SUBJECT_THRESHOLD)
        .slice()
        .sort((a, b) => a.percentage - b.percentage);
}

/**
 * Where a topic sits in the queue of things to fix.
 *
 * Severity, not an arbitrary label: a topic under 40% is losing most of its
 * marks and should be worked on before one at 48%.
 */
export type TopicPriority = 'HIGH' | 'MEDIUM' | 'LOW';

export function topicPriority(percentage: number): TopicPriority {
    if (percentage < 40) return 'HIGH';
    if (percentage < 50) return 'MEDIUM';
    return 'LOW';
}

export const PRIORITY_STYLES: Record<TopicPriority, string> = {
    HIGH: 'bg-red-50 text-red-700 ring-red-200',
    MEDIUM: 'bg-amber-50 text-amber-700 ring-amber-200',
    LOW: 'bg-slate-100 text-slate-600 ring-slate-200',
};
