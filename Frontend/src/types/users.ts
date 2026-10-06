/**
 * Admin user management.
 *
 * Mirrors `UserAdminDtos` in the backend. As everywhere in `src/types`, nullable
 * fields are **absent** rather than null: Jackson omits them, so an admin row
 * carries no `admissionNo` key at all rather than `admissionNo: null`.
 */
import type { Role } from './common';

/**
 * One account with its companion record flattened in.
 *
 * A user is never just a login: a STUDENT owns a row in `students` and a TEACHER
 * one in `teachers`. Which half of this shape is populated follows from `role`.
 */
export interface UserView {
    id: number;
    username: string;
    email: string;
    fullName: string;
    role: Role;
    enabled: boolean;
    createdAt: string;
    updatedAt: string;

    /** STUDENT only. */
    studentId?: number;
    admissionNo?: string;
    className?: string;
    section?: string;
    academicYear?: string;

    /** TEACHER only. */
    teacherId?: number;
    staffNo?: string;
    department?: string;
    /** Class/subject pairs this teacher holds; they go when the account does. */
    subjectAssignments?: number;

    /**
     * Result imports started by this account. Non-zero means the backend will
     * refuse a delete, because `upload_jobs.uploaded_by` is a non-null foreign
     * key and removing the login would remove the record of who loaded the
     * marks. The UI uses this to offer deactivation instead of a doomed delete.
     */
    uploadJobs?: number;
}

export interface CreateUserRequest {
    username: string;
    email: string;
    password: string;
    fullName: string;
    role: Role;
    enabled?: boolean;

    /** STUDENT: links to this child if the number exists, creates it if not. */
    admissionNo?: string;
    className?: string;
    section?: string;
    academicYear?: string;

    /** TEACHER: required, and creates the teacher record. */
    staffNo?: string;
    department?: string;
}

/**
 * The edit body. No `role`: moving an account between roles would strand the
 * companion record it already owns, so the backend treats that as a delete and
 * a create rather than an edit.
 *
 * `password` omitted or empty leaves the current one alone.
 */
export interface UpdateUserRequest {
    username: string;
    email: string;
    fullName: string;
    password?: string;

    className?: string;
    section?: string;
    academicYear?: string;

    staffNo?: string;
    department?: string;
}

/**
 * A row of `GET /api/admin/students`, the roster.
 *
 * The create form reads this to tell an admin whether an admission number will
 * *link* to a child who already has marks or *create* a fresh record - which is
 * the difference between inheriting a term of results and starting from empty.
 */
export interface RosterStudent {
    id: number;
    admissionNo: string;
    fullName: string;
    className: string;
    section?: string;
    academicYear: string;
    hasLogin: boolean;
}

/** Filters for `GET /api/admin/users`, applied server-side. */
export interface UserQuery {
    role?: Role;
    /** Substring of username, e-mail or full name. */
    query?: string;
}

export const ROLE_LABELS: Record<Role, string> = {
    ADMIN: 'Admin',
    TEACHER: 'Teacher',
    STUDENT: 'Student',
};

/** Every role, in the order the list and the filter present them. */
export const ROLES: Role[] = ['ADMIN', 'TEACHER', 'STUDENT'];

/**
 * The one-line summary of what a row's companion record says, for the list.
 * Returns undefined for an admin, which has no companion record to describe.
 */
export function companionSummary(user: UserView): string | undefined {
    if (user.role === 'STUDENT' && user.admissionNo) {
        const where = [user.className, user.section].filter(Boolean).join('-');
        return where ? `${user.admissionNo} · Class ${where}` : user.admissionNo;
    }
    if (user.role === 'TEACHER' && user.staffNo) {
        return user.department ? `${user.staffNo} · ${user.department}` : user.staffNo;
    }
    return undefined;
}
