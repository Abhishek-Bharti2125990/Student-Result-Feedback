import type { ReactNode } from 'react';
import { AlertTriangle } from 'lucide-react';
import { Button } from './Button';
import { Modal } from './Modal';

interface ConfirmDialogProps {
    open: boolean;
    title: string;
    /** What will happen, in the concrete - names and counts, not "this item". */
    body: ReactNode;
    confirmLabel: string;
    /** Red confirm button and a warning icon, for anything irreversible. */
    destructive?: boolean;
    loading?: boolean;
    onConfirm: () => void;
    onCancel: () => void;
}

/**
 * Confirmation before something that cannot be undone.
 *
 * `body` takes a node rather than a string so a caller can spell out the actual
 * consequence - which student record survives, how many subject assignments go
 * with the account. A generic "Are you sure?" trains people to click through it.
 */
export function ConfirmDialog({
    open,
    title,
    body,
    confirmLabel,
    destructive = false,
    loading = false,
    onConfirm,
    onCancel,
}: ConfirmDialogProps) {
    return (
        <Modal
            open={open}
            title={title}
            onClose={loading ? () => undefined : onCancel}
            className="max-w-md"
            footer={
                <>
                    <Button variant="outline" onClick={onCancel} disabled={loading}>
                        Cancel
                    </Button>
                    <Button
                        variant={destructive ? 'danger' : 'primary'}
                        onClick={onConfirm}
                        loading={loading}
                    >
                        {confirmLabel}
                    </Button>
                </>
            }
        >
            <div className="flex items-start gap-3">
                {destructive && (
                    <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-red-50 text-red-600">
                        <AlertTriangle className="size-4" aria-hidden />
                    </span>
                )}
                <div className="min-w-0 text-sm leading-relaxed text-slate-600">{body}</div>
            </div>
        </Modal>
    );
}
