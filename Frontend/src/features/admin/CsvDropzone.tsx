import { useCallback, useRef, useState } from 'react';
import { FileSpreadsheet, UploadCloud, X } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { cn } from '@/lib/cn';
import { fileSize } from '@/lib/format';

/** Rejected before any request is made, so the server is never asked twice. */
export interface FileRejection {
    reason: string;
}

interface CsvDropzoneProps {
    file: File | null;
    onFileSelected: (file: File) => void;
    onClear: () => void;
    onRejected: (rejection: FileRejection) => void;
    disabled?: boolean;
}

/** Matches `spring.servlet.multipart.max-file-size` on the backend. */
const MAX_BYTES = 25 * 1024 * 1024;

/**
 * Drag-and-drop CSV picker.
 *
 * The two checks here - extension and size - are the ones the server would
 * reject anyway, and catching them locally turns a round trip and a 4xx into an
 * instant message. Everything else about the file, including its header row, is
 * validated server-side, because that is where the authority is.
 */
export function CsvDropzone({
    file,
    onFileSelected,
    onClear,
    onRejected,
    disabled = false,
}: CsvDropzoneProps) {
    const [dragging, setDragging] = useState(false);
    const inputRef = useRef<HTMLInputElement>(null);

    const accept = useCallback(
        (candidate: File | undefined) => {
            if (!candidate) return;

            if (!candidate.name.toLowerCase().endsWith('.csv')) {
                onRejected({ reason: `Only .csv files are accepted — got "${candidate.name}".` });
                return;
            }
            if (candidate.size === 0) {
                onRejected({ reason: 'That file is empty.' });
                return;
            }
            if (candidate.size > MAX_BYTES) {
                onRejected({
                    reason: `That file is ${fileSize(candidate.size)}; the limit is ${fileSize(MAX_BYTES)}.`,
                });
                return;
            }
            onFileSelected(candidate);
        },
        [onFileSelected, onRejected],
    );

    if (file) {
        return (
            <div className="flex items-center gap-3 rounded-xl bg-blue-50/60 p-4 ring-1 ring-blue-100">
                <span className="grid size-11 shrink-0 place-items-center rounded-lg bg-blue-600 text-white">
                    <FileSpreadsheet className="size-5" aria-hidden />
                </span>
                <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-semibold text-slate-900">{file.name}</p>
                    <p className="mt-0.5 text-xs text-slate-500">{fileSize(file.size)}</p>
                </div>
                <Button
                    variant="ghost"
                    size="sm"
                    onClick={onClear}
                    disabled={disabled}
                    leftIcon={<X className="size-4" aria-hidden />}
                >
                    Remove
                </Button>
            </div>
        );
    }

    return (
        <div
            onDragOver={(event) => {
                event.preventDefault();
                if (!disabled) setDragging(true);
            }}
            onDragLeave={() => setDragging(false)}
            onDrop={(event) => {
                event.preventDefault();
                setDragging(false);
                if (disabled) return;
                accept(event.dataTransfer.files[0]);
            }}
            className={cn(
                'rounded-xl border-2 border-dashed p-8 text-center transition',
                dragging ? 'border-blue-500 bg-blue-50/60' : 'border-slate-300 bg-slate-50/60',
                disabled && 'opacity-60',
            )}
        >
            <span className="mx-auto grid size-12 place-items-center rounded-full bg-white text-blue-600 ring-1 ring-slate-200">
                <UploadCloud className="size-6" aria-hidden />
            </span>

            <p className="mt-4 text-sm font-semibold text-slate-900">
                Drag a results CSV here
            </p>
            <p className="mt-1 text-sm text-slate-500">
                or choose one from your computer — up to {fileSize(MAX_BYTES)}
            </p>

            <Button
                variant="outline"
                size="sm"
                className="mt-4"
                disabled={disabled}
                onClick={() => inputRef.current?.click()}
            >
                Select file
            </Button>

            {/* Visually hidden rather than display:none, so it stays focusable
                for keyboard and assistive-technology users. */}
            <input
                ref={inputRef}
                type="file"
                accept=".csv,text/csv"
                className="sr-only"
                disabled={disabled}
                onChange={(event) => {
                    accept(event.target.files?.[0]);
                    // Reset so choosing the same file twice still fires change.
                    event.target.value = '';
                }}
            />
        </div>
    );
}
