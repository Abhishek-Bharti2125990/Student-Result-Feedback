import { configureStore } from '@reduxjs/toolkit';
import { setSessionExpiredHandler } from '@/api/client';
import adminReducer from './slices/adminSlice';
import analyticsReducer from './slices/analyticsSlice';
import authReducer, { sessionExpired } from './slices/authSlice';
import studentReducer from './slices/studentSlice';
import teacherReducer from './slices/teacherSlice';
import uiReducer from './slices/uiSlice';

export const store = configureStore({
    reducer: {
        auth: authReducer,
        student: studentReducer,
        teacher: teacherReducer,
        admin: adminReducer,
        analytics: analyticsReducer,
        ui: uiReducer,
    },
});

/**
 * Lets the Axios interceptor tear the session down when a refresh fails.
 *
 * Wired here, after the store exists, rather than by importing the store into
 * the API layer - that direction would be a cycle, because the slices import
 * the API modules.
 */
setSessionExpiredHandler(() => {
    store.dispatch(sessionExpired());
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
