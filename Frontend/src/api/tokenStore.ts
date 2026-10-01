import type { UserProfile } from '@/types/auth';

/**
 * Where the session lives between page loads.
 *
 * Kept out of the Redux slice on purpose. The Axios interceptor needs the
 * current access token on every request and the refresh token when one expires,
 * and importing the store into the API layer would create a cycle - the store's
 * thunks import the API. A tiny module both sides can read breaks it.
 *
 * `localStorage` is a deliberate trade for a hackathon demo: it survives a
 * refresh, which matters when someone is clicking through screens, but it is
 * readable by any script on the page. A production build should move the refresh
 * token to an httpOnly cookie.
 */

const ACCESS_TOKEN_KEY = 'srip.accessToken';
const REFRESH_TOKEN_KEY = 'srip.refreshToken';
const USER_KEY = 'srip.user';

export interface StoredSession {
    accessToken: string;
    refreshToken: string;
    user: UserProfile;
}

function read(key: string): string | null {
    try {
        return localStorage.getItem(key);
    } catch {
        // Private browsing modes can throw on access rather than return null.
        return null;
    }
}

function write(key: string, value: string): void {
    try {
        localStorage.setItem(key, value);
    } catch {
        // A session that cannot be persisted still works for this page load.
    }
}

export const tokenStore = {
    accessToken(): string | null {
        return read(ACCESS_TOKEN_KEY);
    },

    refreshToken(): string | null {
        return read(REFRESH_TOKEN_KEY);
    },

    user(): UserProfile | null {
        const raw = read(USER_KEY);
        if (!raw) return null;
        try {
            return JSON.parse(raw) as UserProfile;
        } catch {
            // A corrupt entry is treated as no session rather than a crash on
            // the very first render.
            return null;
        }
    },

    save(session: StoredSession): void {
        write(ACCESS_TOKEN_KEY, session.accessToken);
        write(REFRESH_TOKEN_KEY, session.refreshToken);
        write(USER_KEY, JSON.stringify(session.user));
    },

    /** Replaces only the tokens, leaving the profile in place, after a refresh. */
    saveTokens(accessToken: string, refreshToken: string): void {
        write(ACCESS_TOKEN_KEY, accessToken);
        write(REFRESH_TOKEN_KEY, refreshToken);
    },

    clear(): void {
        try {
            localStorage.removeItem(ACCESS_TOKEN_KEY);
            localStorage.removeItem(REFRESH_TOKEN_KEY);
            localStorage.removeItem(USER_KEY);
        } catch {
            // Nothing to do; the in-memory state is cleared by the caller.
        }
    },

    restore(): StoredSession | null {
        const accessToken = this.accessToken();
        const refreshToken = this.refreshToken();
        const user = this.user();
        if (!accessToken || !refreshToken || !user) return null;
        return { accessToken, refreshToken, user };
    },
};
