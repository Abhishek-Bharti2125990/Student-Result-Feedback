import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

export type Role = 'STUDENT' | 'TEACHER';

export interface AuthUser {
    name: string;
    email: string;
    role: Role;
    rollNo?: string;
    semester?: string;
    department?: string;
    employeeId?: string;
    token?: string;
}

interface AuthState {
    user: AuthUser | null;
    isAuthenticated: boolean;
}

// Initial state: Refresh karne par LocalStorage se read karega
const getInitialUser = (): AuthUser | null => {
    try {
        const saved = localStorage.getItem('authUser');
        return saved ? JSON.parse(saved) : null;
    } catch {
        return null;
    }
};

const initialUser = getInitialUser();

const initialState: AuthState = {
    user: initialUser,
    isAuthenticated: !!initialUser,
};

export const authSlice = createSlice({
    name: 'auth',
    initialState,
    reducers: {
        login: (state, action: PayloadAction<AuthUser>) => {
            state.user = action.payload;
            state.isAuthenticated = true;
            localStorage.setItem('authUser', JSON.stringify(action.payload));
            if (action.payload.token) {
                localStorage.setItem('token', action.payload.token);
            }
        },
        logout: (state) => {
            state.user = null;
            state.isAuthenticated = false;
            localStorage.removeItem('authUser');
            localStorage.removeItem('token');
        },
    },
});

export const { login, logout } = authSlice.actions;
export default authSlice.reducer;