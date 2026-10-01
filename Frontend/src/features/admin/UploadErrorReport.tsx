import { Card, CardHeader } from '@/components/ui/Card';
import { DataTable, type Column } from '@/components/ui/DataTable';
import { TriangleAlert } from 'lucide-react';
import { plural } from '@/lib/format';
import type { UploadError } from '@/types/admin';

/**
 * Row-by-row rejection report.
 *
 * The line number and the original text are both shown, because this screen's
 * only job is to let someone open the CSV, find that line, and fix it. A count
 * of failures without the lines would send them hunting through 900 rows.
 */
export function UploadErrorReport({ errors }: { errors: UploadError[] }) {
    if (errors.length === 0) return null;

    const columns: Array<Column<UploadError>> = [
        {
            key: 'line',
            header: 'Line',
            className: 'w-16 font-mono text-xs',
            render: (error) => error.lineNumber || '—',
        },
        {
            key: 'message',
            header: 'Why it was rejected',
            render: (error) => <span className="text-slate-700">{error.message}</span>,
        },
        {
            key: 'raw',
            header: 'Row',
            hideOnMobile: true,
            className: 'max-w-xs',
            render: (error) =>
                error.rawLine ? (
                    <code className="block truncate font-mono text-xs text-slate-400" title={error.rawLine}>
                        {error.rawLine}
                    </code>
                ) : (
                    <span className="text-slate-300">—</span>
                ),
        },
    ];

    return (
        <Card>
            <CardHeader
                title="Validation errors"
                subtitle={`${plural(errors.length, 'row')} rejected — every other row was imported`}
                icon={<TriangleAlert className="size-4" aria-hidden />}
            />
            <div className="mt-4">
                <DataTable
                    columns={columns}
                    rows={errors}
                    rowKey={(error) => `${error.lineNumber}-${error.message}`}
                />
            </div>
        </Card>
    );
}
