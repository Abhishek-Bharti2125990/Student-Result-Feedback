import { useEffect, useRef, type ReactNode } from 'react';
import { X } from 'lucide-react';
import { cn } from '@/lib/cn';

interface ModalProps {
    open: boolean;
    title: string;
    description?: string;
    /** Buttons, right-aligned in a bar at the foot of the panel. */
    footer?: ReactNode;
    onClose: () => void;
    children: ReactNode;
    className?: string;
}

/**
 * A centred dialog.
 *
 * Deliberately not the native `<dialog>` element: it is the one part of the
 * design system that has to sit above the sidebar and the toast stack, and
 * `<dialog>`'s top layer ignores z-index, so a toast fired from inside the
 * dialog would render behind it.
 *
 * Three things make it usable by keyboard, none of which come for free:
 * Escape closes, focus moves into the panel on open and returns to whatever
 * opened it on close, and Tab is trapped inside while it is open. Without the
 * last one, tabbing walks into the page behind and the user is editing a form
 * they cannot see.
 */
export function Modal({
    open,
    title,
    description,
    footer,
    onClose,
    children,
    className,
}: ModalProps) {
    const panelRef = useRef<HTMLDivElement>(null);
    const restoreFocusTo = useRef<HTMLElement | null>(null);

    useEffect(() => {
        if (!open) return;

        restoreFocusTo.current = document.activeElement as HTMLElement | null;

        // The first field, not the close button: this dialog is a form, and
        // landing on "cancel" means the first keystroke goes nowhere.
        const focusable = () =>
            Array.from(
                panelRef.current?.querySelectorAll<HTMLElement>(
                    'input:not([disabled]), select:not([disabled]), textarea:not([disabled]), button:not([disabled]), [href], [tabindex]:not([tabindex="-1"])',
                ) ?? [],
            );
        focusable()[0]?.focus();

        const onKeyDown = (event: KeyboardEvent) => {
            if (event.key === 'Escape') {
                event.preventDefault();
                onClose();
                return;
            }
            if (event.key !== 'Tab') return;

            const items = focusable();
            if (items.length === 0) return;

            const first = items[0];
            const last = items[items.length - 1];
            const active = document.activeElement;

            if (event.shiftKey && (active === first || !panelRef.current?.contains(active))) {
                event.preventDefault();
                last.focus();
            } else if (!event.shiftKey && active === last) {
                event.preventDefault();
                first.focus();
            }
        };

        document.addEventListener('keydown', onKeyDown);

        // Stop the page behind from scrolling under the dialog on a phone.
        const previousOverflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';

        return () => {
            document.removeEventListener('keydown', onKeyDown);
            document.body.style.overflow = previousOverflow;
            restoreFocusTo.current?.focus();
        };
    }, [open, onClose]);

    if (!open) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto p-4 sm:items-center sm:p-6">
            <div
                className="fixed inset-0 bg-slate-900/50 backdrop-blur-sm"
                onClick={onClose}
                aria-hidden
            />

            <div
                ref={panelRef}
                role="dialog"
                aria-modal="true"
                aria-labelledby="modal-title"
                aria-describedby={description ? 'modal-description' : undefined}
                className={cn(
                    'relative z-10 my-auto w-full max-w-lg rounded-xl bg-white shadow-xl ring-1 ring-slate-200',
                    className,
                )}
            >
                <div className="flex items-start justify-between gap-4 border-b border-slate-100 px-5 py-4">
                    <div className="min-w-0">
                        <h2 id="modal-title" className="text-base font-semibold text-slate-900">
                            {title}
                        </h2>
                        {description && (
                            <p id="modal-description" className="mt-1 text-sm text-slate-500">
                                {description}
                            </p>
                        )}
                    </div>
                    <button
                        type="button"
                        onClick={onClose}
                        className="-m-1 grid size-8 shrink-0 place-items-center rounded-lg text-slate-400 transition hover:bg-slate-100 hover:text-slate-600"
                        aria-label="Close"
                    >
                        <X className="size-4" aria-hidden />
                    </button>
                </div>

                <div className="px-5 py-4">{children}</div>

                {footer && (
                    <div className="flex flex-wrap justify-end gap-2 border-t border-slate-100 px-5 py-4">
                        {footer}
                    </div>
                )}
            </div>
        </div>
    );
}
