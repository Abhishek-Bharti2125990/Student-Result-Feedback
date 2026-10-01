import type { Role } from './common';

/**
 * `POST /api/auth/login` and `/refresh` both return this.
 *
 * Note the backend sends `fullName`, not `name`. Use {@link displayName} rather
 * than reaching for a `name` field that does not exist.
 */
export interface UserProfile {
    id: number;
    username: string;
    email: string;
    fullName: string;
    role: Role;
    /** Present only for a STUDENT login: the student record they own. */
    studentId?: number;
}

export interface TokenResponse {
    accessToken: string;
    refreshToken: string;
    tokenType: string;
    expiresInSeconds: number;
    user: UserProfile;
}

export interface LoginRequest {
    /** Either the username or the e-mail address is accepted. */
    username: string;
    password: string;
}

/** What to show for a user, tolerating a profile that predates a field. */
export function displayName(user: UserProfile | null): string {
    if (!user) return '';
    return user.fullName?.trim() || user.username;
}

/** Initials for the avatar chip, e.g. "Ayushman Sharma" -> "AS". */
export function initials(user: UserProfile | null): string {
    const name = displayName(user);
    if (!name) return '?';
    const parts = name.split(/\s+/).filter(Boolean);
    const letters = parts.length > 1
        ? [parts[0], parts[parts.length - 1]]
        : [parts[0]];
    return letters.map((part) => part.charAt(0).toUpperCase()).join('');
}
