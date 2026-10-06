import { useEffect, useMemo, type ReactNode } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { AlertCircle, Info, Link2, UserPlus } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { cn } from '@/lib/cn';
import type { Role } from '@/types/common';
import {
    ROLES,
    ROLE_LABELS,
    type CreateUserRequest,
    type RosterStudent,
    type UpdateUserRequest,
    type UserView,
} from '@/types/users';

/** Matches the backend's own minimum, so the form rejects what the API would. */
const MIN_PASSWORD = 8;

/**
 * The form schema.
 *
 * Built per render rather than declared once, because two of its rules depend on
 * things only known at runtime: whether this is a create (password required) or
 * an edit (password optional, blank means "leave it"), and whether the typed
 * admission number already exists (if it does the student record is reused, and
 * the class fields are not needed).
 */
function schemaFor(mode: 'create' | 'edit', knownAdmissionNumbers: Set<string>) {
    return z
        .object({
            username: z.string().trim().min(3, 'At least 3 characters').max(64, 'At most 64 characters'),
            email: z.email('Enter a valid e-mail address').max(160, 'At most 160 characters'),
            fullName: z.string().trim().min(1, 'Enter the full name').max(120, 'At most 120 characters'),
            role: z.enum(['ADMIN', 'TEACHER', 'STUDENT']),
            password: z.string(),
            enabled: z.boolean(),
            admissionNo: z.string().trim(),
            className: z.string().trim(),
            section: z.string().trim(),
            academicYear: z.string().trim(),
            staffNo: z.string().trim(),
            department: z.string().trim(),
        })
        .superRefine((values, ctx) => {
            const required = (path: keyof typeof values, message: string) =>
                ctx.addIssue({ code: 'custom', path: [path], message });

            if (mode === 'create' || values.password.length > 0) {
                if (values.password.length < MIN_PASSWORD) {
                    required('password', `At least ${MIN_PASSWORD} characters`);
                }
            }

            if (values.role === 'STUDENT') {
                if (!values.admissionNo) {
                    required('admissionNo', 'Required for a student account');
                } else if (mode === 'create' && !knownAdmissionNumbers.has(values.admissionNo)) {
                    // No record to inherit, so this creates one - and a student
                    // record cannot exist without a class and a year.
                    if (!values.className) {
                        required('className', 'Required to create a new student record');
                    }
                    if (!values.academicYear) {
                        required('academicYear', 'Required to create a new student record');
                    }
                }
            }

            if (values.role === 'TEACHER' && !values.staffNo) {
                required('staffNo', 'Required for a teacher account');
            }
        });
}

type FormValues = z.infer<ReturnType<typeof schemaFor>>;

const EMPTY: FormValues = {
    username: '',
    email: '',
    fullName: '',
    role: 'STUDENT',
    password: '',
    enabled: true,
    admissionNo: '',
    className: '',
    section: '',
    academicYear: '',
    staffNo: '',
    department: '',
};

interface UserFormDialogProps {
    open: boolean;
    /** The account being edited, or null to create a new one. */
    editing: UserView | null;
    roster: RosterStudent[];
    saving: boolean;
    /** The backend's message when a save was refused, e.g. a taken username. */
    error: string | null;
    onSubmit: (payload: CreateUserRequest | UpdateUserRequest) => void;
    onClose: () => void;
}

/**
 * Create and edit in one dialog.
 *
 * Which fields are shown follows from the role, because a user is two records:
 * a student login owns a row in `students` and a teacher one in `teachers`, and
 * those rows have different columns. Showing all of them at once and ignoring
 * the irrelevant ones would invite an admin to fill in a department for a child.
 *
 * On edit the role is fixed and said to be fixed. Moving an account between
 * roles would strand whatever the old role owned - a teacher's classes, a
 * student's marks - so the backend treats it as a delete and a create, and the
 * form should not imply otherwise.
 */
export function UserFormDialog({
    open,
    editing,
    roster,
    saving,
    error,
    onSubmit,
    onClose,
}: UserFormDialogProps) {
    const mode = editing ? 'edit' : 'create';

    const knownAdmissionNumbers = useMemo(
        () => new Set(roster.map((student) => student.admissionNo)),
        [roster],
    );

    const {
        register,
        handleSubmit,
        control,
        reset,
        formState: { errors },
    } = useForm<FormValues>({
        resolver: zodResolver(schemaFor(mode, knownAdmissionNumbers)),
        defaultValues: EMPTY,
    });

    // Reload on open rather than on mount: the dialog instance is reused for
    // every row, so without this the second account edited would show the
    // first one's values.
    useEffect(() => {
        if (!open) return;
        reset(
            editing
                ? {
                      ...EMPTY,
                      username: editing.username,
                      email: editing.email,
                      fullName: editing.fullName,
                      role: editing.role,
                      enabled: editing.enabled,
                      admissionNo: editing.admissionNo ?? '',
                      className: editing.className ?? '',
                      section: editing.section ?? '',
                      academicYear: editing.academicYear ?? '',
                      staffNo: editing.staffNo ?? '',
                      department: editing.department ?? '',
                  }
                : EMPTY,
        );
    }, [open, editing, reset]);

    // `useWatch` rather than the `watch()` returned by useForm: watch() hands
    // back a fresh function on every render, which the React Compiler cannot
    // memoize, so it bails out of optimising this component entirely.
    const role = useWatch({ control, name: 'role' });
    const admissionNo = (useWatch({ control, name: 'admissionNo' }) ?? '').trim();

    const linkedStudent = roster.find((student) => student.admissionNo === admissionNo);

    const submit = (values: FormValues) => {
        if (editing) {
            const payload: UpdateUserRequest = {
                username: values.username,
                email: values.email,
                fullName: values.fullName,
                // Omitted when blank, which is what tells the backend to keep
                // the existing password rather than hash an empty string.
                password: values.password || undefined,
                className: values.className || undefined,
                section: values.section || undefined,
                academicYear: values.academicYear || undefined,
                staffNo: values.staffNo || undefined,
                department: values.department || undefined,
            };
            onSubmit(payload);
            return;
        }

        const payload: CreateUserRequest = {
            username: values.username,
            email: values.email,
            password: values.password,
            fullName: values.fullName,
            role: values.role,
            enabled: values.enabled,
            admissionNo: values.admissionNo || undefined,
            className: values.className || undefined,
            section: values.section || undefined,
            academicYear: values.academicYear || undefined,
            staffNo: values.staffNo || undefined,
            department: values.department || undefined,
        };
        onSubmit(payload);
    };

    return (
        <Modal
            open={open}
            title={editing ? `Edit ${editing.username}` : 'New user'}
            description={
                editing
                    ? 'Changing the password signs this user out everywhere.'
                    : 'The role decides which record is created alongside the login.'
            }
            onClose={onClose}
            className="max-w-xl"
            footer={
                <>
                    <Button variant="outline" onClick={onClose} disabled={saving}>
                        Cancel
                    </Button>
                    <Button
                        type="submit"
                        form="user-form"
                        loading={saving}
                        leftIcon={<UserPlus className="size-4" aria-hidden />}
                    >
                        {editing ? 'Save changes' : 'Create user'}
                    </Button>
                </>
            }
        >
            {error && (
                <div
                    className="mb-4 flex items-start gap-2.5 rounded-lg bg-red-50 p-3 text-sm text-red-700 ring-1 ring-red-200"
                    role="alert"
                >
                    <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden />
                    <span>{error}</span>
                </div>
            )}

            <form id="user-form" onSubmit={handleSubmit(submit)} className="space-y-4" noValidate>
                <Field label="Full name" error={errors.fullName?.message}>
                    <input
                        type="text"
                        autoComplete="off"
                        className={inputClass(Boolean(errors.fullName))}
                        placeholder="Priya Menon"
                        {...register('fullName')}
                    />
                </Field>

                <div className="grid gap-4 sm:grid-cols-2">
                    <Field label="Username" error={errors.username?.message}>
                        <input
                            type="text"
                            autoComplete="off"
                            className={inputClass(Boolean(errors.username))}
                            placeholder="teacher2"
                            {...register('username')}
                        />
                    </Field>

                    <Field label="E-mail" error={errors.email?.message}>
                        <input
                            type="email"
                            autoComplete="off"
                            className={inputClass(Boolean(errors.email))}
                            placeholder="teacher2@school.local"
                            {...register('email')}
                        />
                    </Field>
                </div>

                <Field
                    label="Role"
                    error={errors.role?.message}
                    hint={
                        editing
                            ? 'Fixed after creation — a role change would strand the student or teacher record this account owns.'
                            : undefined
                    }
                >
                    <select
                        className={cn(inputClass(Boolean(errors.role)), 'disabled:bg-slate-50 disabled:text-slate-500')}
                        disabled={Boolean(editing)}
                        {...register('role')}
                    >
                        {ROLES.map((option: Role) => (
                            <option key={option} value={option}>
                                {ROLE_LABELS[option]}
                            </option>
                        ))}
                    </select>
                </Field>

                <Field
                    label={editing ? 'New password' : 'Password'}
                    error={errors.password?.message}
                    hint={editing ? 'Leave blank to keep the current password.' : undefined}
                >
                    <input
                        type="password"
                        autoComplete="new-password"
                        className={inputClass(Boolean(errors.password))}
                        placeholder={editing ? 'Unchanged' : '••••••••'}
                        {...register('password')}
                    />
                </Field>

                {role === 'STUDENT' && (
                    <fieldset className="space-y-4 rounded-lg bg-slate-50 p-4">
                        <legend className="px-1 text-xs font-semibold uppercase tracking-wide text-slate-500">
                            Student record
                        </legend>

                        <Field label="Admission number" error={errors.admissionNo?.message}>
                            <input
                                type="text"
                                autoComplete="off"
                                className={inputClass(Boolean(errors.admissionNo), 'bg-white')}
                                placeholder="1004"
                                disabled={Boolean(editing)}
                                {...register('admissionNo')}
                            />
                        </Field>

                        {/*
                         * The decisive piece of feedback on this form. An
                         * existing number links the login to a child who may
                         * already have a term of marks; an unknown one starts an
                         * empty record. Finding that out after saving is too
                         * late.
                         */}
                        {!editing && admissionNo.length > 0 && (
                            <Hint
                                tone={linkedStudent ? (linkedStudent.hasLogin ? 'warn' : 'link') : 'info'}
                                icon={linkedStudent ? <Link2 className="size-3.5" aria-hidden /> : undefined}
                            >
                                {linkedStudent
                                    ? linkedStudent.hasLogin
                                        ? `${linkedStudent.fullName} already has a login — this will be rejected.`
                                        : `Links to ${linkedStudent.fullName}, class ${linkedStudent.className}${
                                              linkedStudent.section ? `-${linkedStudent.section}` : ''
                                          }, and inherits their existing results.`
                                    : 'No student record with that number — one will be created from the fields below.'}
                            </Hint>
                        )}

                        <div className="grid gap-4 sm:grid-cols-3">
                            <Field label="Class" error={errors.className?.message}>
                                <input
                                    type="text"
                                    className={inputClass(Boolean(errors.className), 'bg-white')}
                                    placeholder="10"
                                    {...register('className')}
                                />
                            </Field>
                            <Field label="Section" error={errors.section?.message}>
                                <input
                                    type="text"
                                    className={inputClass(Boolean(errors.section), 'bg-white')}
                                    placeholder="A"
                                    {...register('section')}
                                />
                            </Field>
                            <Field label="Academic year" error={errors.academicYear?.message}>
                                <input
                                    type="text"
                                    className={inputClass(Boolean(errors.academicYear), 'bg-white')}
                                    placeholder="2025-2026"
                                    {...register('academicYear')}
                                />
                            </Field>
                        </div>
                    </fieldset>
                )}

                {role === 'TEACHER' && (
                    <fieldset className="space-y-4 rounded-lg bg-slate-50 p-4">
                        <legend className="px-1 text-xs font-semibold uppercase tracking-wide text-slate-500">
                            Teacher record
                        </legend>
                        <div className="grid gap-4 sm:grid-cols-2">
                            <Field label="Staff number" error={errors.staffNo?.message}>
                                <input
                                    type="text"
                                    autoComplete="off"
                                    className={inputClass(Boolean(errors.staffNo), 'bg-white')}
                                    placeholder="T-101"
                                    {...register('staffNo')}
                                />
                            </Field>
                            <Field label="Department" error={errors.department?.message}>
                                <input
                                    type="text"
                                    className={inputClass(Boolean(errors.department), 'bg-white')}
                                    placeholder="Mathematics"
                                    {...register('department')}
                                />
                            </Field>
                        </div>
                        {!editing && (
                            <Hint tone="info">
                                Subjects and classes are assigned separately, after the account exists.
                            </Hint>
                        )}
                    </fieldset>
                )}

                {!editing && (
                    <label className="flex items-start gap-2.5 text-sm text-slate-700">
                        <input
                            type="checkbox"
                            className="mt-0.5 size-4 rounded border-slate-300 text-blue-600 focus:ring-blue-600"
                            {...register('enabled')}
                        />
                        <span>
                            Active immediately
                            <span className="block text-xs text-slate-500">
                                Clear this to prepare the account before the person starts.
                            </span>
                        </span>
                    </label>
                )}
            </form>
        </Modal>
    );
}

// -- Local form primitives --------------------------------------------------

function inputClass(invalid: boolean, extra?: string): string {
    return cn(
        'block w-full rounded-lg border-0 px-3 py-2 text-sm text-slate-900',
        'ring-1 ring-inset placeholder:text-slate-400',
        'focus:ring-2 focus:ring-inset focus:ring-blue-600',
        invalid ? 'ring-red-400' : 'ring-slate-300',
        extra,
    );
}

function Field({
    label,
    error,
    hint,
    children,
}: {
    label: string;
    error?: string;
    hint?: string;
    children: ReactNode;
}) {
    return (
        <label className="block">
            <span className="block text-sm font-medium text-slate-700">{label}</span>
            <div className="mt-1.5">{children}</div>
            {error ? (
                <p className="mt-1.5 text-xs text-red-600">{error}</p>
            ) : (
                hint && <p className="mt-1.5 text-xs text-slate-500">{hint}</p>
            )}
        </label>
    );
}

const HINT_TONES = {
    info: 'bg-white text-slate-600 ring-slate-200',
    link: 'bg-blue-50 text-blue-800 ring-blue-200',
    warn: 'bg-amber-50 text-amber-800 ring-amber-200',
};

function Hint({
    tone,
    icon,
    children,
}: {
    tone: keyof typeof HINT_TONES;
    icon?: ReactNode;
    children: ReactNode;
}) {
    return (
        <p
            className={cn(
                'flex items-start gap-2 rounded-lg p-2.5 text-xs leading-relaxed ring-1',
                HINT_TONES[tone],
            )}
        >
            <span className="mt-px shrink-0">{icon ?? <Info className="size-3.5" aria-hidden />}</span>
            <span>{children}</span>
        </p>
    );
}
