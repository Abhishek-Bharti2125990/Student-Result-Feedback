import { createSlice, nanoid, type PayloadAction } from '@reduxjs/toolkit';

export type NotificationKind = 'success' | 'error' | 'info';

export interface Notification {
    id: string;
    kind: NotificationKind;
    title: string;
    message?: string;
}

interface UiState {
    /** Mobile sidebar drawer. Ignored at `lg` and above, where it is always on. */
    sidebarOpen: boolean;
    notifications: Notification[];
    /**
     * Study-plan items the student has ticked off, keyed `examId:index`.
     *
     * Local only - the backend has no endpoint for plan progress, and inventing
     * one client-side that silently vanishes on another device would be worse
     * than being honest that this is a local checklist.
     */
    completedPlanItems: Record<string, boolean>;
}

const initialState: UiState = {
    sidebarOpen: false,
    notifications: [],
    completedPlanItems: {},
};

export const uiSlice = createSlice({
    name: 'ui',
    initialState,
    reducers: {
        toggleSidebar: (state) => {
            state.sidebarOpen = !state.sidebarOpen;
        },
        setSidebarOpen: (state, action: PayloadAction<boolean>) => {
            state.sidebarOpen = action.payload;
        },
        notify: {
            reducer: (state, action: PayloadAction<Notification>) => {
                state.notifications.push(action.payload);
            },
            prepare: (input: Omit<Notification, 'id'>) => ({
                payload: { ...input, id: nanoid() },
            }),
        },
        dismissNotification: (state, action: PayloadAction<string>) => {
            state.notifications = state.notifications.filter((n) => n.id !== action.payload);
        },
        togglePlanItem: (state, action: PayloadAction<string>) => {
            const key = action.payload;
            if (state.completedPlanItems[key]) {
                delete state.completedPlanItems[key];
            } else {
                state.completedPlanItems[key] = true;
            }
        },
        resetPlanProgress: (state) => {
            state.completedPlanItems = {};
        },
    },
});

export const {
    toggleSidebar,
    setSidebarOpen,
    notify,
    dismissNotification,
    togglePlanItem,
    resetPlanProgress,
} = uiSlice.actions;
export default uiSlice.reducer;
