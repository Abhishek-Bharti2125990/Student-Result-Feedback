import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';
import { EmptyState } from './EmptyState';

export interface Column<T> {
    /** Stable key, also used as the React key for cells in this column. */
    key: string;
    header: ReactNode;
    render: (row: T) => ReactNode;
    /** Extra classes for both the header cell and every body cell. */
    className?: string;
    /** Hidden below `sm`, for columns that are context rather than content. */
    hideOnMobile?: boolean;
}

interface DataTableProps<T> {
    columns: Array<Column<T>>;
    rows: T[];
    rowKey: (row: T) => string | number;
    onRowClick?: (row: T) => void;
    emptyTitle?: string;
    emptyDescription?: string;
    className?: string;
}

/**
 * A plain, responsive table.
 *
 * Horizontally scrollable rather than collapsing to cards: the one table in
 * this app is the upload history, where the columns are short and keeping them
 * aligned is more useful than stacking them on a phone.
 */
export function DataTable<T>({
    columns,
    rows,
    rowKey,
    onRowClick,
    emptyTitle = 'Nothing here yet',
    emptyDescription,
    className,
}: DataTableProps<T>) {
    if (rows.length === 0) {
        return <EmptyState title={emptyTitle} description={emptyDescription} />;
    }

    return (
        <div className={cn('-mx-5 overflow-x-auto', className)}>
            <table className="w-full min-w-[40rem] border-collapse text-sm">
                <thead>
                    <tr className="border-b border-slate-200 text-left">
                        {columns.map((column) => (
                            <th
                                key={column.key}
                                scope="col"
                                className={cn(
                                    'px-5 py-3 text-xs font-semibold uppercase tracking-wide text-slate-500',
                                    column.hideOnMobile && 'hidden sm:table-cell',
                                    column.className,
                                )}
                            >
                                {column.header}
                            </th>
                        ))}
                    </tr>
                </thead>
                <tbody>
                    {rows.map((row) => (
                        <tr
                            key={rowKey(row)}
                            onClick={onRowClick ? () => onRowClick(row) : undefined}
                            className={cn(
                                'border-b border-slate-100 last:border-0',
                                onRowClick && 'cursor-pointer transition hover:bg-slate-50',
                            )}
                        >
                            {columns.map((column) => (
                                <td
                                    key={column.key}
                                    className={cn(
                                        'px-5 py-3.5 text-slate-700 align-middle',
                                        column.hideOnMobile && 'hidden sm:table-cell',
                                        column.className,
                                    )}
                                >
                                    {column.render(row)}
                                </td>
                            ))}
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
}
