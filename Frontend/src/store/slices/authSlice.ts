import { createAsyncThunk, createSlice, type PayloadAction } from '@reduxjs/toolkit';
import { authApi } from '@/api/authApi';
import { toErrorMessage } from '@/api/client';
import { tokenStore } from '@/api/tokenStore';
import type { LoginRequest, UserProfile } from '@/types/auth';

interface AuthState {
    user: UserProfile | null;
    isAuthenticated: boolean;
    loading: boolean;
    error: string | null;
    /** Set when a token expired mid-session, so /login can explain why. */
    sessionExpired: boolean;
}

/**
 * Seeded from storage so a page refresh does not bounce the user to /login.
 * The tokens are validated by the first API call, not here - checking them up
 * front would mean an extra round trip before anything renders.
 */
const restored = tokenStore.restore();

const initialState: AuthState = {
    user: restored?.user ?? null,
    isAuthenticated: restored !== null,
    loading: false,
    error: null,
    sessionExpired: false,
};

export const loginThunk = createAsyncThunk<
    UserProfile,
    LoginRequest,
    { rejectValue: string }
>('auth/login', async (payload, { rejectWithValue }) => {
    try {
        const response = await authApi.login(payload);
        tokenStore.save({
            accessToken: response.accessToken,
            refreshToken: response.refreshToken,
            user: response.user,
        });
        return response.user;
    } catch (error) {
        return rejectWithValue(toErrorMessage(error, 'Could not sign in'));
    }
});

export const logoutThunk = createAsyncThunk('auth/logout', async () => {
    const refreshToken = tokenStore.refreshToken();
    if (refreshToken) {
        // Revoking server-side is best effort: the local session is cleared
        // either way, so a network failure cannot trap the user inside the app.
        await authApi.logout(refreshToken).catch(() => undefined);
    }
    tokenStore.clear();
});

export const authSlice = createSlice({
    name: 'auth',
    initialState,
    reducers: {
        /** Called by the Axios interceptor when a refresh finally fails. */
        sessionExpired: (state) => {
            state.user = null;
            state.isAuthenticated = false;
            state.sessionExpired = true;
        },
        clearAuthError: (state) => {
            state.error = null;
            state.sessionExpired = false;
        },
        userLoaded: (state, action: PayloadAction<UserProfile>) => {
            state.user = action.payload;
            state.isAuthenticated = true;
        },
    },
    extraReducers: (builder) => {
        builder
            .addCase(loginThunk.pending, (state) => {
                state.loading = true;
                state.error = null;
                state.sessionExpired = false;
            })
            .addCase(loginThunk.fulfilled, (state, action) => {
                state.loading = false;
                state.user = action.payload;
                state.isAuthenticated = true;
            })
            .addCase(loginThunk.rejected, (state, action) => {
                state.loading = false;
                state.error = action.payload ?? 'Could not sign in';
                state.isAuthenticated = false;
                state.user = null;
            })
            .addCase(logoutThunk.fulfilled, (state) => {
                state.user = null;
                state.isAuthenticated = false;
                state.error = null;
                state.sessionExpired = false;
            });
    },
});

export const { sessionExpired, clearAuthError, userLoaded } = authSlice.actions;
export default authSlice.reducer;
