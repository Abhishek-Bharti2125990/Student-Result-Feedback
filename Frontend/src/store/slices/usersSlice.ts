import { createAsyncThunk, createSlice, type PayloadAction } from '@reduxjs/toolkit';
import { toErrorMessage } from '@/api/client';
import { usersApi } from '@/api/usersApi';
import type { Role } from '@/types/common';
import type {
    CreateUserRequest,
    RosterStudent,
    UpdateUserRequest,
    UserView,
} from '@/types/users';

interface UsersState {
    list: UserView[];
    listLoading: boolean;
    listError: string | null;

    /** Filters, held here so a refresh after a mutation reuses them. */
    roleFilter: Role | 'ALL';
    query: string;

    /**
     * One id at a time, not a boolean.
     *
     * A single `saving` flag would grey out every row's buttons while one row
     * was being switched off, which reads as the whole table having frozen.
     */
    pendingId: number | null;
    /** Create and edit share the dialog, so they share one flag. */
    saving: boolean;

    /** The roster, loaded once for the admission-number hint. */
    roster: RosterStudent[];
    rosterLoaded: boolean;
}

const initialState: UsersState = {
    list: [],
    listLoading: false,
    listError: null,
    roleFilter: 'ALL',
    query: '',
    pendingId: null,
    saving: false,
    roster: [],
    rosterLoaded: false,
};

/** Reads the filters out of state so every caller cannot forget to pass them. */
export const fetchUsers = createAsyncThunk<
    UserView[],
    void,
    { state: { users: UsersState }; rejectValue: string }
>('users/list', async (_, { getState, rejectWithValue }) => {
    const { roleFilter, query } = getState().users;
    try {
        return await usersApi.list({
            role: roleFilter === 'ALL' ? undefined : roleFilter,
            query,
        });
    } catch (error) {
        return rejectWithValue(toErrorMessage(error, 'Could not load the user list'));
    }
});

export const createUser = createAsyncThunk<
    UserView,
    CreateUserRequest,
    { rejectValue: string }
>('users/create', async (payload, { rejectWithValue }) => {
    try {
        return await usersApi.create(payload);
    } catch (error) {
        return rejectWithValue(toErrorMessage(error, 'Could not create the account'));
    }
});

export const updateUser = createAsyncThunk<
    UserView,
    { userId: number; payload: UpdateUserRequest },
    { rejectValue: string }
>('users/update', async ({ userId, payload }, { rejectWithValue }) => {
    try {
        return await usersApi.update(userId, payload);
    } catch (error) {
        return rejectWithValue(toErrorMessage(error, 'Could not save the changes'));
    }
});

export const setUserEnabled = createAsyncThunk<
    UserView,
    { userId: number; enabled: boolean },
    { rejectValue: string }
>('users/setEnabled', async ({ userId, enabled }, { rejectWithValue }) => {
    try {
        return await usersApi.setEnabled(userId, enabled);
    } catch (error) {
        return rejectWithValue(
            toErrorMessage(error, enabled ? 'Could not activate the account' : 'Could not deactivate the account'),
        );
    }
});

export const deleteUser = createAsyncThunk<
    number,
    number,
    { rejectValue: string }
>('users/delete', async (userId, { rejectWithValue }) => {
    try {
        await usersApi.delete(userId);
        return userId;
    } catch (error) {
        return rejectWithValue(toErrorMessage(error, 'Could not delete the account'));
    }
});

/** Loaded once per session; the roster only changes when a CSV is imported. */
export const fetchRoster = createAsyncThunk<
    RosterStudent[],
    void,
    { rejectValue: string }
>('users/roster', async (_, { rejectWithValue }) => {
    try {
        return await usersApi.students();
    } catch (error) {
        return rejectWithValue(toErrorMessage(error, 'Could not load the roster'));
    }
});

/** Replaces a row in place so the list does not jump after a status change. */
function replaceRow(state: UsersState, updated: UserView): void {
    const index = state.list.findIndex((user) => user.id === updated.id);
    if (index >= 0) {
        state.list[index] = updated;
    }
}

export const usersSlice = createSlice({
    name: 'users',
    initialState,
    reducers: {
        setRoleFilter: (state, action: PayloadAction<Role | 'ALL'>) => {
            state.roleFilter = action.payload;
        },
        setQuery: (state, action: PayloadAction<string>) => {
            state.query = action.payload;
        },
        clearUsersError: (state) => {
            state.listError = null;
        },
    },
    extraReducers: (builder) => {
        builder
            .addCase(fetchUsers.pending, (state) => {
                state.listLoading = true;
                state.listError = null;
            })
            .addCase(fetchUsers.fulfilled, (state, action) => {
                state.listLoading = false;
                state.list = action.payload;
            })
            .addCase(fetchUsers.rejected, (state, action) => {
                state.listLoading = false;
                state.listError = action.payload ?? 'Could not load the user list';
            })

            // The new row is not spliced into the list here. The list is a
            // server-filtered, role-ordered view, and inserting locally would
            // show a student account while the Teacher filter is on, in the
            // wrong position. The page refetches instead.
            .addCase(createUser.pending, (state) => {
                state.saving = true;
            })
            .addCase(createUser.fulfilled, (state) => {
                state.saving = false;
            })
            .addCase(createUser.rejected, (state) => {
                state.saving = false;
            })

            .addCase(updateUser.pending, (state) => {
                state.saving = true;
            })
            .addCase(updateUser.fulfilled, (state, action) => {
                state.saving = false;
                replaceRow(state, action.payload);
            })
            .addCase(updateUser.rejected, (state) => {
                state.saving = false;
            })

            .addCase(setUserEnabled.pending, (state, action) => {
                state.pendingId = action.meta.arg.userId;
            })
            .addCase(setUserEnabled.fulfilled, (state, action) => {
                state.pendingId = null;
                replaceRow(state, action.payload);
            })
            .addCase(setUserEnabled.rejected, (state) => {
                state.pendingId = null;
            })

            .addCase(deleteUser.pending, (state, action) => {
                state.pendingId = action.meta.arg;
            })
            .addCase(deleteUser.fulfilled, (state, action) => {
                state.pendingId = null;
                state.list = state.list.filter((user) => user.id !== action.payload);
            })
            .addCase(deleteUser.rejected, (state) => {
                state.pendingId = null;
            })

            .addCase(fetchRoster.fulfilled, (state, action) => {
                state.roster = action.payload;
                state.rosterLoaded = true;
            })
            // A failed roster load is not worth surfacing: it only powers a
            // hint, and the create form works without it.
            .addCase(fetchRoster.rejected, (state) => {
                state.rosterLoaded = true;
            });
    },
});

export const { setRoleFilter, setQuery, clearUsersError } = usersSlice.actions;
export default usersSlice.reducer;
