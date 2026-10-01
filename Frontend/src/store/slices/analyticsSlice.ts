import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { analyticsApi } from '@/api/analyticsApi';
import { isNotFound, toErrorMessage } from '@/api/client';
import type { ClassAnalytics, PerformanceTrend } from '@/types/analytics';

interface AnalyticsState {
    trend: PerformanceTrend | null;
    trendLoading: boolean;
    trendError: string | null;
    /** No exams sat yet - an empty state, not a failure. */
    noData: boolean;

    /** Class-wide subject figures, for the teacher's subject chart. */
    classReport: ClassAnalytics | null;
    classReportLoading: boolean;
    /**
     * No error field on purpose. This is a secondary call behind one chart; if
     * it fails the chart shows its empty state and the rest of the teacher
     * dashboard, which came from a different request, stays usable.
     */
}

const initialState: AnalyticsState = {
    trend: null,
    trendLoading: false,
    trendError: null,
    noData: false,
    classReport: null,
    classReportLoading: false,
};

export const fetchMyTrend = createAsyncThunk<
    PerformanceTrend,
    void,
    { rejectValue: { message: string; noData: boolean } }
>('analytics/myTrend', async (_, { rejectWithValue }) => {
    try {
        return await analyticsApi.myTrend();
    } catch (error) {
        return rejectWithValue({
            message: toErrorMessage(error, 'Could not load your trend'),
            noData: isNotFound(error),
        });
    }
});

export const fetchClassReport = createAsyncThunk<
    ClassAnalytics | null,
    { className: string; examId: number }
>('analytics/classReport', async ({ className, examId }) => {
    try {
        return await analyticsApi.classReport(className, examId);
    } catch {
        // Swallowed deliberately: see the note on `classReport` in the state.
        return null;
    }
});

export const analyticsSlice = createSlice({
    name: 'analytics',
    initialState,
    reducers: {
        resetAnalytics: () => initialState,
    },
    extraReducers: (builder) => {
        builder
            .addCase(fetchClassReport.pending, (state) => {
                state.classReportLoading = true;
            })
            .addCase(fetchClassReport.fulfilled, (state, action) => {
                state.classReportLoading = false;
                state.classReport = action.payload;
            })
            .addCase(fetchClassReport.rejected, (state) => {
                state.classReportLoading = false;
                state.classReport = null;
            })

            .addCase(fetchMyTrend.pending, (state) => {
                state.trendLoading = true;
                state.trendError = null;
                state.noData = false;
            })
            .addCase(fetchMyTrend.fulfilled, (state, action) => {
                state.trendLoading = false;
                state.trend = action.payload;
            })
            .addCase(fetchMyTrend.rejected, (state, action) => {
                state.trendLoading = false;
                state.trendError = action.payload?.message ?? 'Could not load your trend';
                state.noData = action.payload?.noData ?? false;
            });
    },
});

export const { resetAnalytics } = analyticsSlice.actions;
export default analyticsSlice.reducer;
