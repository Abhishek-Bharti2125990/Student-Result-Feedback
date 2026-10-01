import { BookMarked, ExternalLink, PlayCircle } from 'lucide-react';
import { Badge } from '@/components/ui/Badge';
import { cn } from '@/lib/cn';
import type { ResourceSuggestion } from '@/types/common';

/**
 * One vetted book or video.
 *
 * The link is rendered **only** when the backend supplied a URL. A printed book
 * has no link, and an anchor to `#` or to a blank `href` would look like a
 * working button that does nothing - worse than no button at all.
 */
export function ResourceCard({ resource }: { resource: ResourceSuggestion }) {
    const isVideo = resource.resourceType === 'VIDEO';
    const hasLink = typeof resource.url === 'string' && resource.url.trim().length > 0;

    return (
        <div className="flex items-start gap-3 rounded-lg bg-white p-3.5 ring-1 ring-slate-200/80">
            <span
                className={cn(
                    'mt-0.5 grid size-9 shrink-0 place-items-center rounded-lg',
                    isVideo ? 'bg-rose-50 text-rose-600' : 'bg-blue-50 text-blue-600',
                )}
                aria-hidden
            >
                {isVideo ? <PlayCircle className="size-5" /> : <BookMarked className="size-5" />}
            </span>

            <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                    <Badge tone={isVideo ? 'red' : 'blue'}>{resource.resourceType}</Badge>
                    <span className="text-xs text-slate-500">{resource.subject}</span>
                </div>
                <p className="mt-1.5 text-sm font-medium text-slate-900">{resource.title}</p>

                {hasLink ? (
                    <a
                        href={resource.url}
                        target="_blank"
                        // noreferrer alongside noopener: the target must not be
                        // handed this app's URL, and must not get window.opener.
                        rel="noopener noreferrer"
                        className="mt-2 inline-flex items-center gap-1.5 text-xs font-semibold text-blue-600 hover:text-blue-700 hover:underline"
                    >
                        {isVideo ? 'Watch' : 'Open'}
                        <ExternalLink className="size-3" aria-hidden />
                    </a>
                ) : (
                    <p className="mt-2 text-xs text-slate-400">Printed resource — ask your teacher</p>
                )}
            </div>
        </div>
    );
}
