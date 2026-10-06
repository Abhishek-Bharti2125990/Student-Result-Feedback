import { useEffect, useState } from 'react';
import {
    GraduationCap,
    Pencil,
    RefreshCw,
    Search,
    Shield,
    Trash2,
    ToggleLeft,
    ToggleRight,
    UserPlus,
    Users,
} from 'lucide-react';
import { PageHeader } from '@/components/layout/AppLayout';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { DataTable, type Column } from '@/components/ui/DataTable';
import { ErrorState } from '@/components/ui/ErrorState';
import { CardSkeleton } from '@/components/ui/Skeleton';
import { cn } from '@/lib/cn';
import { dateOnly, plural } from '@/lib/format';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import { notify } from '@/store/slices/uiSlice';
import {
    createUser,
    deleteUser,
    fetchRoster,
    fetchUsers,
    setRoleFilter,
    setQuery,
    setUserEnabled,
    updateUser,
} from '@/store/slices/usersSlice';
import type { Role } from '@/types/common';
import {
    ROLES,
    ROLE_LABELS,
    companionSummary,
    type CreateUserRequest,
    type UpdateUserRequest,
    type UserView,
} from '@/types/users';
import { UserFormDialog } from './UserFormDialog';

const ROLE_TONES: Record<Role, 'amber' | 'violet' | 'blue'> = {
    ADMIN: 'amber',
    TEACHER: 'violet',
    STUDENT: 'blue',
};

const ROLE_ICONS: Record<Role, typeof Shield> = {
    ADMIN: Shield,
    TEACHER: Users,
    STUDENT: GraduationCap,
};

/** Which row the confirmation dialog is about, and what it would do. */
type Pending =
    | { kind: 'delete'; user: UserView }
    | { kind: 'deactivate'; user: UserView }
    | null;

/**
 * Account administration.
 *
 * The list is the screen; everything else is a dialog over it. Two things are
 * deliberate:
 *
 * **Deactivate sits next to delete, and reads as the safer one.** Switching an
 * account off ends its sessions within one request and can be undone, which is
 * the right answer to "this person has left". Deleting is offered too, but the
 * confirmation spells out what goes with it.
 *
 * **The row knows why a delete would fail.** An account that has imported
 * results carries a non-null foreign key from `upload_jobs`, so the backend
 * refuses to delete it. That is reported on the row rather than discovered after
 * clicking.
 */
export function AdminUsersPage() {
    const dispatch = useAppDispatch();
    const { list, listLoading, listError, roleFilter, query, pendingId, saving, roster } =
        useAppSelector((state) => state.users);
    const currentUserId = useAppSelector((state) => state.auth.user?.id);

    const [formOpen, setFormOpen] = useState(false);
    const [editing, setEditing] = useState<UserView | null>(null);
    const [formError, setFormError] = useState<string | null>(null);
    const [pending, setPending] = useState<Pending>(null);

    // The roster only changes when a CSV is imported, so it is fetched once and
    // reused by the create form's admission-number hint.
    useEffect(() => {
        void dispatch(fetchRoster());
    }, [dispatch]);

    // Re-runs on a filter change because the filtering is server-side; the
    // search box is debounced below so this is not one request per keystroke.
    useEffect(() => {
        const timer = setTimeout(() => {
            void dispatch(fetchUsers());
        }, query ? 300 : 0);
        return () => clearTimeout(timer);
    }, [dispatch, roleFilter, query]);

    const openCreate = () => {
        setEditing(null);
        setFormError(null);
        setFormOpen(true);
    };

    const openEdit = (user: UserView) => {
        setEditing(user);
        setFormError(null);
        setFormOpen(true);
    };

    const submitForm = (payload: CreateUserRequest | UpdateUserRequest) => {
        setFormError(null);

        const action = editing
            ? dispatch(updateUser({ userId: editing.id, payload: payload as UpdateUserRequest }))
            : dispatch(createUser(payload as CreateUserRequest));

        void action
            .unwrap()
            .then((user) => {
                setFormOpen(false);
                dispatch(
                    notify({
                        kind: 'success',
                        title: editing ? 'Account updated' : 'Account created',
                        message: editing
                            ? `${user.username} saved.`
                            : `${user.username} can sign in as ${ROLE_LABELS[user.role].toLowerCase()}.`,
                    }),
                );
                // An edit is patched into the row it came from, but a new
                // account has to come back from the server to land in the right
                // place in the role ordering - and to be absent if the active
                // filter excludes it.
                if (!editing) {
                    void dispatch(fetchUsers());
                }
            })
            // Kept inside the dialog rather than fired as a toast: a taken
            // username is something to fix in the form that is still open.
            .catch((message: string) => setFormError(message));
    };

    const toggleEnabled = (user: UserView) => {
        if (user.enabled) {
            setPending({ kind: 'deactivate', user });
            return;
        }
        void dispatch(setUserEnabled({ userId: user.id, enabled: true }))
            .unwrap()
            .then(() =>
                dispatch(notify({ kind: 'success', title: `${user.username} activated` })),
            )
            .catch((message: string) =>
                dispatch(notify({ kind: 'error', title: 'Could not activate', message })),
            );
    };

    const confirmPending = () => {
        if (!pending) return;
        const { kind, user } = pending;

        const action =
            kind === 'delete'
                ? dispatch(deleteUser(user.id))
                : dispatch(setUserEnabled({ userId: user.id, enabled: false }));

        void action
            .unwrap()
            .then(() => {
                setPending(null);
                dispatch(
                    notify({
                        kind: 'success',
                        title: kind === 'delete' ? 'Account deleted' : `${user.username} deactivated`,
                        message:
                            kind === 'delete' && user.role === 'STUDENT'
                                ? 'The student record and its results were kept.'
                                : undefined,
                    }),
                );
            })
            .catch((message: string) => {
                setPending(null);
                dispatch(
                    notify({
                        kind: 'error',
                        title: kind === 'delete' ? 'Could not delete' : 'Could not deactivate',
                        message,
                    }),
                );
            });
    };

    const columns: Array<Column<UserView>> = [
        {
            key: 'user',
            header: 'User',
            render: (user) => {
                const RoleIcon = ROLE_ICONS[user.role];
                const summary = companionSummary(user);
                return (
                    <div className="flex items-start gap-3">
                        <span
                            className={cn(
                                'mt-0.5 grid size-8 shrink-0 place-items-center rounded-lg',
                                user.enabled ? 'bg-slate-100 text-slate-500' : 'bg-slate-100 text-slate-300',
                            )}
                        >
                            <RoleIcon className="size-4" aria-hidden />
                        </span>
                        <div className="min-w-0">
                            <p
                                className={cn(
                                    'font-medium',
                                    user.enabled ? 'text-slate-900' : 'text-slate-400',
                                )}
                            >
                                {user.fullName}
                            </p>
                            <p className="truncate text-xs text-slate-500">
                                <span className="font-mono">{user.username}</span> · {user.email}
                            </p>
                            {summary && (
                                <p className="mt-0.5 truncate text-xs text-slate-400">{summary}</p>
                            )}
                            {user.id === currentUserId && (
                                <span className="text-xs font-medium text-blue-600">You</span>
                            )}
                        </div>
                    </div>
                );
            },
        },
        {
            key: 'role',
            header: 'Role',
            className: 'w-28',
            render: (user) => <Badge tone={ROLE_TONES[user.role]}>{ROLE_LABELS[user.role]}</Badge>,
        },
        {
            key: 'status',
            header: 'Status',
            className: 'w-28',
            render: (user) => (
                <Badge tone={user.enabled ? 'green' : 'slate'}>
                    {user.enabled ? 'Active' : 'Inactive'}
                </Badge>
            ),
        },
        {
            key: 'created',
            header: 'Added',
            hideOnMobile: true,
            className: 'w-32 text-xs text-slate-500 whitespace-nowrap',
            render: (user) => dateOnly(user.createdAt),
        },
        {
            key: 'actions',
            header: <span className="sr-only">Actions</span>,
            className: 'w-44 text-right',
            render: (user) => {
                const isSelf = user.id === currentUserId;
                const busy = pendingId === user.id;
                return (
                    <div className="flex justify-end gap-1">
                        <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => openEdit(user)}
                            aria-label={`Edit ${user.username}`}
                            title="Edit"
                        >
                            <Pencil className="size-3.5" aria-hidden />
                        </Button>
                        <Button
                            variant="ghost"
                            size="sm"
                            loading={busy}
                            // An admin switching off their own account would
                            // lock themselves out of this very screen, and the
                            // backend refuses it - so the button says so here.
                            disabled={isSelf && user.enabled}
                            onClick={() => toggleEnabled(user)}
                            aria-label={`${user.enabled ? 'Deactivate' : 'Activate'} ${user.username}`}
                            title={
                                isSelf && user.enabled
                                    ? 'You cannot deactivate your own account'
                                    : user.enabled
                                      ? 'Deactivate'
                                      : 'Activate'
                            }
                        >
                            {user.enabled ? (
                                <ToggleRight className="size-4 text-emerald-600" aria-hidden />
                            ) : (
                                <ToggleLeft className="size-4 text-slate-400" aria-hidden />
                            )}
                        </Button>
                        <Button
                            variant="ghost"
                            size="sm"
                            loading={busy}
                            disabled={isSelf || (user.uploadJobs ?? 0) > 0}
                            onClick={() => setPending({ kind: 'delete', user })}
                            aria-label={`Delete ${user.username}`}
                            title={
                                isSelf
                                    ? 'You cannot delete your own account'
                                    : (user.uploadJobs ?? 0) > 0
                                      ? `Has ${plural(user.uploadJobs ?? 0, 'result import')} on record — deactivate instead`
                                      : 'Delete'
                            }
                        >
                            <Trash2 className="size-3.5 text-red-500" aria-hidden />
                        </Button>
                    </div>
                );
            },
        },
    ];

    const refresh = () => void dispatch(fetchUsers());

    if (listLoading && list.length === 0) {
        return (
            <>
                <PageHeader title="Users" description="Loading accounts…" />
                <CardSkeleton lines={8} />
            </>
        );
    }

    return (
        <>
            <PageHeader
                title="Users"
                description="Every login, with the student or teacher record it owns"
                action={
                    <div className="flex gap-2">
                        <Button
                            variant="outline"
                            size="sm"
                            onClick={refresh}
                            loading={listLoading}
                            leftIcon={<RefreshCw className="size-3.5" aria-hidden />}
                        >
                            Refresh
                        </Button>
                        <Button
                            size="sm"
                            onClick={openCreate}
                            leftIcon={<UserPlus className="size-3.5" aria-hidden />}
                        >
                            New user
                        </Button>
                    </div>
                }
            />

            {listError ? (
                <ErrorState message={listError} onRetry={refresh} retrying={listLoading} />
            ) : (
                <Card flush>
                    <div className="p-5 pb-0">
                        <CardHeader
                            title="Accounts"
                            subtitle={
                                list.length > 0
                                    ? `${plural(list.length, 'account')} — grouped by role`
                                    : undefined
                            }
                            icon={<Users className="size-4" aria-hidden />}
                        />

                        <div className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-center">
                            <div className="relative flex-1">
                                <Search
                                    className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400"
                                    aria-hidden
                                />
                                <input
                                    type="search"
                                    value={query}
                                    onChange={(event) => dispatch(setQuery(event.target.value))}
                                    placeholder="Search name, username or e-mail"
                                    aria-label="Search accounts"
                                    className="block w-full rounded-lg border-0 py-2 pl-9 pr-3 text-sm text-slate-900 ring-1 ring-inset ring-slate-300 placeholder:text-slate-400 focus:ring-2 focus:ring-inset focus:ring-blue-600"
                                />
                            </div>

                            <div className="flex gap-1.5" role="group" aria-label="Filter by role">
                                {(['ALL', ...ROLES] as Array<Role | 'ALL'>).map((option) => (
                                    <button
                                        key={option}
                                        type="button"
                                        onClick={() => dispatch(setRoleFilter(option))}
                                        aria-pressed={roleFilter === option}
                                        className={cn(
                                            'rounded-lg px-3 py-1.5 text-xs font-medium transition',
                                            roleFilter === option
                                                ? 'bg-slate-900 text-white'
                                                : 'bg-white text-slate-600 ring-1 ring-slate-200 hover:bg-slate-50',
                                        )}
                                    >
                                        {option === 'ALL' ? 'All' : ROLE_LABELS[option]}
                                    </button>
                                ))}
                            </div>
                        </div>
                    </div>

                    <div className="mt-4 px-5 pb-1">
                        <DataTable
                            columns={columns}
                            rows={list}
                            rowKey={(user) => user.id}
                            emptyTitle={
                                query || roleFilter !== 'ALL'
                                    ? 'No accounts match'
                                    : 'No accounts yet'
                            }
                            emptyDescription={
                                query || roleFilter !== 'ALL'
                                    ? 'Clear the search or the role filter to see every account.'
                                    : 'Create the first login to get started.'
                            }
                        />
                    </div>
                </Card>
            )}

            <UserFormDialog
                open={formOpen}
                editing={editing}
                roster={roster}
                saving={saving}
                error={formError}
                onSubmit={submitForm}
                onClose={() => setFormOpen(false)}
            />

            <ConfirmDialog
                open={pending !== null}
                destructive
                loading={pending !== null && pendingId === pending.user.id}
                title={
                    pending?.kind === 'delete'
                        ? `Delete ${pending.user.username}?`
                        : `Deactivate ${pending?.user.username}?`
                }
                confirmLabel={pending?.kind === 'delete' ? 'Delete account' : 'Deactivate'}
                body={pending ? consequencesOf(pending) : null}
                onConfirm={confirmPending}
                onCancel={() => setPending(null)}
            />
        </>
    );
}

/**
 * Spells out what the action actually does to this row.
 *
 * The honest detail matters most for a student: the login goes, the child's
 * record and marks stay. An admin who believes delete erases a term of results
 * will avoid the button they should be using.
 */
function consequencesOf({ kind, user }: NonNullable<Pending>) {
    if (kind === 'deactivate') {
        return (
            <>
                <strong className="font-semibold text-slate-900">{user.fullName}</strong> will be signed
                out and unable to log in. Nothing is deleted, and you can switch the account back on at
                any time.
            </>
        );
    }

    return (
        <>
            <p>
                The login for{' '}
                <strong className="font-semibold text-slate-900">{user.fullName}</strong> will be
                removed permanently.
            </p>
            {user.role === 'STUDENT' && (
                <p className="mt-2">
                    The student record{user.admissionNo ? ` (${user.admissionNo})` : ''} and every mark
                    against it are <strong className="font-semibold text-slate-900">kept</strong>, and
                    can be given a new login later.
                </p>
            )}
            {user.role === 'TEACHER' && (
                <p className="mt-2">
                    The teacher record
                    {(user.subjectAssignments ?? 0) > 0
                        ? ` and its ${plural(user.subjectAssignments ?? 0, 'subject assignment')}`
                        : ''}{' '}
                    will be removed with it. Imported marks are unaffected.
                </p>
            )}
            <p className="mt-2 text-slate-500">
                Deactivating instead keeps the account and is reversible.
            </p>
        </>
    );
}
