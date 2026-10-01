import { useEffect } from 'react';
import { CheckCircle2, Info, X, XCircle } from 'lucide-react';
import { cn } from '@/lib/cn';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import { dismissNotification, type NotificationKind } from '@/store/slices/uiSlice';

const TONES: Record<NotificationKind, { ring: string; icon: typeof Info; iconClass: string }> = {
    success: { ring: 'ring-emerald-200', icon: CheckCircle2, iconClass: 'text-emerald-600' },
    error: { ring: 'ring-red-200', icon: XCircle, iconClass: 'text-red-600' },
    info: { ring: 'ring-blue-200', icon: Info, iconClass: 'text-blue-600' },
};

/** How long a toast stays. Errors linger - they usually need reading twice. */
const DISMISS_AFTER: Record<NotificationKind, number> = {
    success: 4000,
    info: 5000,
    error: 8000,
};

/**
 * Toast stack, top-right.
 *
 * `aria-live="polite"` rather than `assertive`: these confirm things the user
 * just did, and interrupting a screen reader mid-sentence to say "Upload
 * accepted" is more disruptive than useful.
 */
export function NotificationArea() {
    const notifications = useAppSelector((state) => state.ui.notifications);

    return (
        <div
            className="pointer-events-none fixed right-4 top-4 z-50 flex w-full max-w-sm flex-col gap-2"
            aria-live="polite"
            aria-atomic="false"
        >
            {notifications.map((notification) => (
                <Toast key={notification.id} {...notification} />
            ))}
        </div>
    );
}

interface ToastProps {
    id: string;
    kind: NotificationKind;
    title: string;
    message?: string;
}

function Toast({ id, kind, title, message }: ToastProps) {
    const dispatch = useAppDispatch();
    const tone = TONES[kind];
    const Icon = tone.icon;

    useEffect(() => {
        const timer = window.setTimeout(() => {
            dispatch(dismissNotification(id));
        }, DISMISS_AFTER[kind]);
        return () => window.clearTimeout(timer);
    }, [dispatch, id, kind]);

    return (
        <div
            className={cn(
                'pointer-events-auto flex items-start gap-3 rounded-xl bg-white p-3.5 shadow-lg ring-1',
                tone.ring,
            )}
        >
            <Icon className={cn('mt-0.5 size-5 shrink-0', tone.iconClass)} aria-hidden />
            <div className="min-w-0 flex-1">
                <p className="text-sm font-semibold text-slate-900">{title}</p>
                {message && <p className="mt-0.5 text-sm text-slate-600">{message}</p>}
            </div>
            <button
                type="button"
                onClick={() => dispatch(dismissNotification(id))}
                className="rounded-md p-1 text-slate-400 transition hover:bg-slate-100 hover:text-slate-600"
                aria-label="Dismiss notification"
            >
                <X className="size-4" aria-hidden />
            </button>
        </div>
    );
}
