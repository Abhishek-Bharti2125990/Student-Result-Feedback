import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { isNotFound, toErrorMessage } from '@/api/client';
import { studentApi, type StudentQuery } from '@/api/studentApi';
import type { ResourceSuggestion } from '@/types/common';
import type { StudentDashboard, StudentFeedbackEnvelope } from '@/types/student';

interface StudentState {
    dashboard: StudentDashboard | null;
    feedback: StudentFeedbackEnvelope | null;
    resources: ResourceSuggestion[];

    dashboardLoading: boolean;
    feedbackLoading: boolean;
    resourcesLoading: boolean;

    /** True while an explicit, user-requested regeneration is in flight. */
    regenerating: boolean;

    dashboardError: string | null;
    feedbackError: string | null;
    resourcesError: string | null;

    /**
     * Set when the backend answered 404 - no results have been uploaded for
     * this student yet. That is an empty state, not an error, and the two must
     * not look the same on screen.
     */
    noData: boolean;
}

const initialState: StudentState = {
    dashboard: null,
    feedback: null,
    resources: [],
    dashboardLoading: false,
    feedbackLoading: false,
    resourcesLoading: false,
    regenerating: false,
    dashboardError: null,
    feedbackError: null,
    resourcesError: null,
    noData: false,
};

export const fetchStudentDashboard = createAsyncThunk<
    StudentDashboard,
    StudentQuery | undefined,
    { rejectValue: { message: string; noData: boolean } }
>('student/dashboard', async (query, { rejectWithValue }) => {
    try {
        return await studentApi.dashboard(query ?? {});
    } catch (error) {
        return rejectWithValue({
            message: toErrorMessage(error, 'Could not load your dashboard'),
            noData: isNotFound(error),
        });
    }
});

export const fetchStudentFeedback = createAsyncThunk<
    StudentFeedbackEnvelope,
    { query?: StudentQuery; refresh?: boolean } | undefined,
    { rejectValue: string }
>('student/feedback', async (args, { rejectWithValue }) => {
    try {
        return await studentApi.feedback(args?.query ?? {}, args?.refresh ?? false);
    } catch (error) {
        return rejectWithValue(toErrorMessage(error, 'Could not load your feedback'));
    }
});

export const fetchStudentResources = createAsyncThunk<
    ResourceSuggestion[],
    StudentQuery | undefined,
    { rejectValue: string }
>('student/resources', async (query, { rejectWithValue }) => {
    try {
        return await studentApi.resources(query ?? {});
    } catch (error) {
        return rejectWithValue(toErrorMessage(error, 'Could not load your resources'));
    }
});

export const studentSlice = createSlice({
    name: 'student',
    initialState,
    reducers: {
        resetStudentData: () => initialState,
    },
    extraReducers: (builder) => {
        builder
            .addCase(fetchStudentDashboard.pending, (state) => {
                state.dashboardLoading = true;
                state.dashboardError = null;
                state.noData = false;
            })
            .addCase(fetchStudentDashboard.fulfilled, (state, action) => {
                state.dashboardLoading = false;
                state.dashboard = action.payload;
            })
            .addCase(fetchStudentDashboard.rejected, (state, action) => {
                state.dashboardLoading = false;
                state.dashboardError = action.payload?.message ?? 'Could not load your dashboard';
                state.noData = action.payload?.noData ?? false;
            })

            .addCase(fetchStudentFeedback.pending, (state, action) => {
                // A regeneration keeps the previous document on screen while it
                // runs; a first load has nothing to keep, so it shows a skeleton.
                if (action.meta.arg?.refresh) {
                    state.regenerating = true;
                } else {
                    state.feedbackLoading = true;
                }
                state.feedbackError = null;
            })
            .addCase(fetchStudentFeedback.fulfilled, (state, action) => {
                state.feedbackLoading = false;
                state.regenerating = false;
                state.feedback = action.payload;
            })
            .addCase(fetchStudentFeedback.rejected, (state, action) => {
                state.feedbackLoading = false;
                state.regenerating = false;
                state.feedbackError = action.payload ?? 'Could not load your feedback';
            })

            .addCase(fetchStudentResources.pending, (state) => {
                state.resourcesLoading = true;
                state.resourcesError = null;
            })
            .addCase(fetchStudentResources.fulfilled, (state, action) => {
                state.resourcesLoading = false;
                state.resources = action.payload;
            })
            .addCase(fetchStudentResources.rejected, (state, action) => {
                state.resourcesLoading = false;
                state.resourcesError = action.payload ?? 'Could not load your resources';
            });
    },
});

export const { resetStudentData } = studentSlice.actions;
export default studentSlice.reducer;
