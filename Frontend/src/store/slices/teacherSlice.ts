import { createAsyncThunk, createSlice, type PayloadAction } from '@reduxjs/toolkit';
import { isNotFound, toErrorMessage } from '@/api/client';
import { teacherApi, type TeacherQuery } from '@/api/teacherApi';
import type { ScoreCategory } from '@/types/common';
import type { StudentCategoryCard, TeacherDashboard } from '@/types/teacher';
import { CATEGORY_SEGMENTS } from '@/types/teacher';

interface TeacherState {
    dashboard: TeacherDashboard | null;
    loading: boolean;
    error: string | null;
    noData: boolean;

    /** Which bucket is expanded. The dashboard opens on the one that matters. */
    selectedCategory: ScoreCategory;

    /**
     * Students fetched for the selected bucket, keyed by category.
     *
     * The dashboard payload already embeds every bucket's students, so this is
     * only populated when a filter changes and a single bucket is re-fetched.
     * Falling back to the embedded list keeps the first paint free of requests.
     */
    categoryStudents: Partial<Record<ScoreCategory, StudentCategoryCard[]>>;
    categoryLoading: boolean;
    categoryError: string | null;

    filters: TeacherQuery;
}

const initialState: TeacherState = {
    dashboard: null,
    loading: false,
    error: null,
    noData: false,
    selectedCategory: 'CRITICAL',
    categoryStudents: {},
    categoryLoading: false,
    categoryError: null,
    filters: {},
};

export const fetchTeacherDashboard = createAsyncThunk<
    TeacherDashboard,
    TeacherQuery | undefined,
    { rejectValue: { message: string; noData: boolean } }
>('teacher/dashboard', async (query, { rejectWithValue }) => {
    try {
        return await teacherApi.dashboard(query ?? {});
    } catch (error) {
        return rejectWithValue({
            message: toErrorMessage(error, 'Could not load the class dashboard'),
            noData: isNotFound(error),
        });
    }
});

export const fetchCategoryStudents = createAsyncThunk<
    { category: ScoreCategory; students: StudentCategoryCard[] },
    { category: ScoreCategory; query?: TeacherQuery },
    { rejectValue: string }
>('teacher/categoryStudents', async ({ category, query }, { rejectWithValue }) => {
    try {
        const students = await teacherApi.studentsInCategory(
            CATEGORY_SEGMENTS[category],
            query ?? {},
        );
        return { category, students };
    } catch (error) {
        return rejectWithValue(toErrorMessage(error, 'Could not load that student list'));
    }
});

export const teacherSlice = createSlice({
    name: 'teacher',
    initialState,
    reducers: {
        selectCategory: (state, action: PayloadAction<ScoreCategory>) => {
            state.selectedCategory = action.payload;
        },
        setTeacherFilters: (state, action: PayloadAction<TeacherQuery>) => {
            state.filters = action.payload;
            // The cached per-bucket lists belong to the previous filter.
            state.categoryStudents = {};
        },
        resetTeacherData: () => initialState,
    },
    extraReducers: (builder) => {
        builder
            .addCase(fetchTeacherDashboard.pending, (state) => {
                state.loading = true;
                state.error = null;
                state.noData = false;
            })
            .addCase(fetchTeacherDashboard.fulfilled, (state, action) => {
                state.loading = false;
                state.dashboard = action.payload;

                // Open on the worst non-empty bucket: a teacher wants the
                // students in trouble first, and landing on an empty CRITICAL
                // panel looks like the page failed to load.
                const firstPopulated = action.payload.buckets.find((bucket) => bucket.studentCount > 0);
                if (firstPopulated) {
                    state.selectedCategory = firstPopulated.category;
                }
            })
            .addCase(fetchTeacherDashboard.rejected, (state, action) => {
                state.loading = false;
                state.error = action.payload?.message ?? 'Could not load the class dashboard';
                state.noData = action.payload?.noData ?? false;
            })

            .addCase(fetchCategoryStudents.pending, (state) => {
                state.categoryLoading = true;
                state.categoryError = null;
            })
            .addCase(fetchCategoryStudents.fulfilled, (state, action) => {
                state.categoryLoading = false;
                state.categoryStudents[action.payload.category] = action.payload.students;
            })
            .addCase(fetchCategoryStudents.rejected, (state, action) => {
                state.categoryLoading = false;
                state.categoryError = action.payload ?? 'Could not load that student list';
            });
    },
});

export const { selectCategory, setTeacherFilters, resetTeacherData } = teacherSlice.actions;
export default teacherSlice.reducer;
