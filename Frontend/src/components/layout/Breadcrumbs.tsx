import { useLocation } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import { SEGMENT_LABELS } from './navigation';

/**
 * Trail derived from the URL.
 *
 * Only the last segment is a link target worth following, so the earlier ones
 * are plain text: `/student` is not a page in this app, and a crumb that 404s
 * is worse than one that is not clickable.
 */
export function Breadcrumbs() {
    const { pathname } = useLocation();
    const segments = pathname.split('/').filter(Boolean);

    if (segments.length === 0) return null;

    return (
        <nav aria-label="Breadcrumb" className="min-w-0">
            <ol className="flex items-center gap-1.5 text-sm">
                {segments.map((segment, index) => {
                    const isLast = index === segments.length - 1;
                    const label = SEGMENT_LABELS[segment] ?? prettify(segment);

                    return (
                        <li key={`${segment}-${index}`} className="flex min-w-0 items-center gap-1.5">
                            {index > 0 && (
                                <ChevronRight className="size-3.5 shrink-0 text-slate-300" aria-hidden />
                            )}
                            {isLast ? (
                                <span className="truncate font-medium text-slate-900" aria-current="page">
                                    {label}
                                </span>
                            ) : (
                                <span className="truncate text-slate-500">{label}</span>
                            )}
                        </li>
                    );
                })}
            </ol>
        </nav>
    );
}

/** `uploads` -> `Uploads`; a numeric id becomes `#12`. */
function prettify(segment: string): string {
    if (/^\d+$/.test(segment)) return `#${segment}`;
    return segment.charAt(0).toUpperCase() + segment.slice(1).replace(/-/g, ' ');
}
