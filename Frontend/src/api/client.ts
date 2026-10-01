import axios, {
    AxiosError,
    type AxiosRequestConfig,
    type InternalAxiosRequestConfig,
} from 'axios';
import type { ApiErrorBody } from '@/types/common';
import type { TokenResponse } from '@/types/auth';
import { tokenStore } from './tokenStore';

/**
 * The single Axios instance every API module uses.
 *
 * Base URL defaults to `/api`, a relative path proxied to the backend by Vite in
 * development. Nothing in the app hard-codes a host, so the same build works
 * behind any reverse proxy; override with `VITE_API_BASE_URL` if the API is on
 * another origin.
 */
export const api = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
    headers: { 'Content-Type': 'application/json' },
    timeout: 30_000,
});

/** Endpoints that must never carry a token or trigger a refresh. */
const PUBLIC_PATHS = ['/auth/login', '/auth/refresh'];

function isPublic(url: string | undefined): boolean {
    if (!url) return false;
    return PUBLIC_PATHS.some((path) => url.includes(path));
}

// -- Request: attach the access token ---------------------------------------

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    const token = tokenStore.accessToken();
    if (token && !isPublic(config.url)) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

// -- Response: refresh once on 401, then replay -----------------------------

/**
 * Called when the session is gone for good, so the app can route to /login.
 * A callback rather than a direct store import, which would be a cycle.
 */
type SessionExpiredHandler = () => void;

let onSessionExpired: SessionExpiredHandler = () => {};

export function setSessionExpiredHandler(handler: SessionExpiredHandler): void {
    onSessionExpired = handler;
}

/**
 * The in-flight refresh, if any.
 *
 * A dashboard fires several requests at once, so an expired token produces
 * several 401s within milliseconds. Without this, each would start its own
 * refresh; because the backend *rotates* refresh tokens, the second call would
 * present an already-spent token - which the backend treats as a replay and
 * answers by revoking every session for the account. Sharing one promise is
 * what stops a page load from logging the user out.
 */
let refreshInFlight: Promise<string> | null = null;

/** Marks a request we have already retried, so a failure cannot loop. */
interface RetriableConfig extends AxiosRequestConfig {
    _retried?: boolean;
}

async function refreshAccessToken(): Promise<string> {
    const refreshToken = tokenStore.refreshToken();
    if (!refreshToken) {
        throw new Error('No refresh token stored');
    }

    // A bare axios call, not `api`: this must not pass back through the
    // interceptor that is currently handling a 401.
    const response = await axios.post<TokenResponse>(
        `${api.defaults.baseURL ?? '/api'}/auth/refresh`,
        { refreshToken },
        { headers: { 'Content-Type': 'application/json' } },
    );

    const { accessToken, refreshToken: rotated } = response.data;
    tokenStore.saveTokens(accessToken, rotated);
    return accessToken;
}

api.interceptors.response.use(
    (response) => response,
    async (error: AxiosError<ApiErrorBody>) => {
        const config = error.config as (RetriableConfig & InternalAxiosRequestConfig) | undefined;

        const canRetry =
            error.response?.status === 401 &&
            config !== undefined &&
            !config._retried &&
            !isPublic(config.url) &&
            tokenStore.refreshToken() !== null;

        if (!canRetry || !config) {
            // A 401 on login is a wrong password, not an expired session, so it
            // must surface to the form rather than bounce the user anywhere.
            if (error.response?.status === 401 && config && !isPublic(config.url)) {
                tokenStore.clear();
                onSessionExpired();
            }
            return Promise.reject(error);
        }

        config._retried = true;

        try {
            refreshInFlight = refreshInFlight ?? refreshAccessToken();
            const accessToken = await refreshInFlight;
            config.headers.Authorization = `Bearer ${accessToken}`;
            return api.request(config);
        } catch (refreshError) {
            tokenStore.clear();
            onSessionExpired();
            return Promise.reject(refreshError);
        } finally {
            refreshInFlight = null;
        }
    },
);

/**
 * Turns any thrown value into a sentence worth putting on screen.
 *
 * The backend sends `{ message }` on every handled error, so that is preferred
 * over Axios's own "Request failed with status code 500", which tells a user
 * nothing they can act on.
 */
export function toErrorMessage(error: unknown, fallback = 'Something went wrong'): string {
    if (axios.isAxiosError<ApiErrorBody>(error)) {
        if (error.code === 'ECONNABORTED') {
            return 'The server took too long to respond. Is the backend running?';
        }
        if (!error.response) {
            return 'Could not reach the server. Check that the backend is running on port 8080.';
        }

        const { status, statusText, data } = error.response;

        // A gateway error is the dev proxy (or a reverse proxy in production)
        // reporting that it could not reach the API at all. Showing the raw
        // "502 Bad Gateway" would send someone hunting for a bug in the app
        // when the actual problem is that the backend is not running.
        if (status === 502 || status === 503 || status === 504) {
            return 'The API is not responding. Check that the backend is running on port 8080.';
        }

        // The backend sends `{ message }` on every handled error.
        if (data?.message) {
            return data.details?.length ? `${data.message}: ${data.details.join(', ')}` : data.message;
        }
        return `${status} ${statusText}`;
    }
    if (error instanceof Error && error.message) {
        return error.message;
    }
    return fallback;
}

/** True when the server answered 404 - "nothing uploaded yet", not a failure. */
export function isNotFound(error: unknown): boolean {
    return axios.isAxiosError(error) && error.response?.status === 404;
}
