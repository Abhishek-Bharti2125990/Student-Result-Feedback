/**
 * CSV upload and import monitoring.
 *
 * The backend's status vocabulary is richer than the brief's four states, and
 * the extra one is worth keeping: `COMPLETED_WITH_ERRORS` means the marks were
 * imported *and* some rows were rejected. Collapsing it into COMPLETED would
 * hide the error report; collapsing it into FAILED would wrongly suggest
 * nothing was saved.
 */
export type UploadStatus =
    | 'PENDING'
    | 'RUNNING'
    | 'COMPLETED'
    | 'COMPLETED_WITH_ERRORS'
    | 'FAILED';

/** Returned by `POST /api/admin/upload` with HTTP 202. */
export interface UploadAccepted {
    uploadJobId: number;
    filename: string;
    dataRowCount: number;
    status: UploadStatus;
    message: string;
}

/** One rejected row, quoted back so the source file can be fixed. */
export interface UploadError {
    lineNumber: number;
    rawLine?: string;
    message: string;
}

/** `GET /api/admin/upload/{id}` and, with an empty `errors`, the history list. */
export interface UploadJobView {
    id: number;
    filename: string;
    status: UploadStatus;
    totalRecords: number;
    validRecords: number;
    invalidRecords: number;
    jobExecutionId?: number;
    failureMessage?: string;
    createdAt: string;
    completedAt?: string;
    errors: UploadError[];
}

/** True while the job is still worth polling. */
export function isInFlight(status: UploadStatus): boolean {
    return status === 'PENDING' || status === 'RUNNING';
}

/** The brief's vocabulary, for the status badge. */
export function statusLabel(status: UploadStatus): string {
    switch (status) {
        case 'PENDING':
            return 'QUEUED';
        case 'RUNNING':
            return 'PROCESSING';
        case 'COMPLETED':
            return 'COMPLETED';
        case 'COMPLETED_WITH_ERRORS':
            return 'COMPLETED WITH ERRORS';
        case 'FAILED':
            return 'FAILED';
    }
}

/** The ten columns the backend's header-validation step requires, in order. */
export const EXPECTED_CSV_HEADER = [
    'student_id',
    'student_name',
    'class_name',
    'section',
    'exam_name',
    'subject',
    'chapter_name',
    'topic_name',
    'marks_obtained',
    'maximum_marks',
] as const;
