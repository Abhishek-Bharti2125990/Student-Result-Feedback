import type { LoginRequest, TokenResponse, UserProfile } from '@/types/auth';
import { api } from './client';

export const authApi = {
    login(payload: LoginRequest): Promise<TokenResponse> {
        return api.post<TokenResponse>('/auth/login', payload).then((r) => r.data);
    },

    refresh(refreshToken: string): Promise<TokenResponse> {
        return api.post<TokenResponse>('/auth/refresh', { refreshToken }).then((r) => r.data);
    },

    /**
     * Best-effort: the session is cleared locally whether or not this succeeds,
     * so a network failure on the way out must never trap the user in the app.
     */
    logout(refreshToken: string): Promise<void> {
        return api.post('/auth/logout', { refreshToken }).then(() => undefined);
    },

    me(): Promise<UserProfile> {
        return api.get<UserProfile>('/auth/me').then((r) => r.data);
    },
};
