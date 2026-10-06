import type {
    CreateUserRequest,
    RosterStudent,
    UpdateUserRequest,
    UserQuery,
    UserView,
} from '@/types/users';
import { api } from './client';

/**
 * Admin user management, `/api/admin/users`.
 *
 * Separate from `adminApi`, which is the CSV import. Both are admin-only but
 * they share no state, and keeping them apart means the upload page does not
 * pull user types into its chunk.
 */
export const usersApi = {
    /**
     * The account list.
     *
     * Filtering is a server-side query rather than a client-side `.filter()`,
     * so the search keeps working once a school has more logins than one
     * response should reasonably carry.
     */
    list(filters: UserQuery = {}): Promise<UserView[]> {
        return api
            .get<UserView[]>('/admin/users', {
                // Omit empty values entirely: `?query=` would be sent as a
                // blank filter rather than no filter.
                params: {
                    role: filters.role || undefined,
                    query: filters.query?.trim() || undefined,
                },
            })
            .then((r) => r.data);
    },

    get(userId: number): Promise<UserView> {
        return api.get<UserView>(`/admin/users/${userId}`).then((r) => r.data);
    },

    create(payload: CreateUserRequest): Promise<UserView> {
        return api.post<UserView>('/admin/users', payload).then((r) => r.data);
    },

    update(userId: number, payload: UpdateUserRequest): Promise<UserView> {
        return api.put<UserView>(`/admin/users/${userId}`, payload).then((r) => r.data);
    },

    /** Activates or deactivates in one call, without re-sending the whole form. */
    setEnabled(userId: number, enabled: boolean): Promise<UserView> {
        return api
            .patch<UserView>(`/admin/users/${userId}/status`, { enabled })
            .then((r) => r.data);
    },

    delete(userId: number): Promise<void> {
        return api.delete(`/admin/users/${userId}`).then(() => undefined);
    },

    /**
     * The roster, used by the create form to say whether an admission number
     * will link to an existing child or create a new record - the difference
     * between inheriting a term of marks and starting from nothing.
     */
    students(): Promise<RosterStudent[]> {
        return api.get<RosterStudent[]>('/admin/students').then((r) => r.data);
    },
};
