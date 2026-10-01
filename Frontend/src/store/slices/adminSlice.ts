import { createAsyncThunk, createSlice, type PayloadAction } from '@reduxjs/toolkit';
import { adminApi } from '@/api/adminApi';
import { toErrorMessage } from '@/api/client';
import type { UploadAccepted, UploadJobView } from '@/types/admin';

interface AdminState {
    /** 0..1 of the file sent to the server. Not the import's progress. */
    uploadFraction: number;
    uploading: boolean;
    uploadError: string | null;

    /** The job the upload page is currently watching. */
    activeJob: UploadJobView | null;
    activeJobId: number | null;
    jobLoading: boolean;
    jobError: string | null;

    history: UploadJobView[];
    historyLoading: boolean;
    historyError: string | null;
}

const initialState: AdminState = {
    uploadFraction: 0,
    uploading: false,
    uploadError: null,
    activeJob: null,
    activeJobId: null,
    jobLoading: false,
    jobError: null,
    history: [],
    historyLoading: false,
    historyError: null,
};

export const uploadCsv = createAsyncThunk<
    UploadAccepted,
    File,
    { rejectValue: string }
>('admin/upload', async (file, { dispatch, rejectWithValue }) => {
    try {
        return await adminApi.upload(file, (fraction) => {
            dispatch(adminSlice.actions.setUploadFraction(fraction));
        });
    } catch (error) {
        return rejectWithValue(toErrorMessage(error, 'The upload was rejected'));
    }
});

export const fetchUploadJob = createAsyncThunk<
    UploadJobView,
    number,
    { rejectValue: string }
>('admin/job', async (uploadJobId, { rejectWithValue }) => {
    try {
        return await adminApi.job(uploadJobId);
    } catch (error) {
        return rejectWithValue(toErrorMessage(error, 'Could not read the import status'));
    }
});

export const fetchUploadHistory = createAsyncThunk<
    UploadJobView[],
    void,
    { rejectValue: string }
>('admin/history', async (_, { rejectWithValue }) => {
    try {
        return await adminApi.history();
    } catch (error) {
        return rejectWithValue(toErrorMessage(error, 'Could not load the upload history'));
    }
});

export const adminSlice = createSlice({
    name: 'admin',
    initialState,
    reducers: {
        setUploadFraction: (state, action: PayloadAction<number>) => {
            state.uploadFraction = action.payload;
        },
        watchJob: (state, action: PayloadAction<number>) => {
            state.activeJobId = action.payload;
        },
        clearUpload: (state) => {
            state.uploadFraction = 0;
            state.uploading = false;
            state.uploadError = null;
            state.activeJob = null;
            state.activeJobId = null;
            state.jobError = null;
        },
    },
    extraReducers: (builder) => {
        builder
            .addCase(uploadCsv.pending, (state) => {
                state.uploading = true;
                state.uploadError = null;
                state.uploadFraction = 0;
                state.activeJob = null;
                state.activeJobId = null;
            })
            .addCase(uploadCsv.fulfilled, (state, action) => {
                state.uploading = false;
                state.uploadFraction = 1;
                state.activeJobId = action.payload.uploadJobId;
            })
            .addCase(uploadCsv.rejected, (state, action) => {
                state.uploading = false;
                state.uploadFraction = 0;
                state.uploadError = action.payload ?? 'The upload was rejected';
            })

            // Deliberately no loading flag flip on a poll: the status panel is
            // already on screen, and toggling a spinner every second would make
            // a working import look broken.
            .addCase(fetchUploadJob.pending, (state) => {
                state.jobLoading = state.activeJob === null;
                state.jobError = null;
            })
            .addCase(fetchUploadJob.fulfilled, (state, action) => {
                state.jobLoading = false;
                state.activeJob = action.payload;
            })
            .addCase(fetchUploadJob.rejected, (state, action) => {
                state.jobLoading = false;
                state.jobError = action.payload ?? 'Could not read the import status';
            })

            .addCase(fetchUploadHistory.pending, (state) => {
                state.historyLoading = true;
                state.historyError = null;
            })
            .addCase(fetchUploadHistory.fulfilled, (state, action) => {
                state.historyLoading = false;
                state.history = action.payload;
            })
            .addCase(fetchUploadHistory.rejected, (state, action) => {
                state.historyLoading = false;
                state.historyError = action.payload ?? 'Could not load the upload history';
            });
    },
});

export const { setUploadFraction, watchJob, clearUpload } = adminSlice.actions;
export default adminSlice.reducer;
