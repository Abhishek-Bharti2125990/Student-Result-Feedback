import { BookOpen, Check, TriangleAlert } from 'lucide-react';
import { Badge } from '@/components/ui/Badge';
import { cn } from '@/lib/cn';
import { percent } from '@/lib/format';
import { PRIORITY_STYLES, topicPriority } from '@/lib/subjects';

interface TopicCardProps {
    subject?: string;
    chapter?: string;
    topic: string;
    percentage: number;
    variant: 'strong' | 'weak';
}

/**
 * One topic, with the chapter it sits in.
 *
 * The chapter is what makes the card actionable: "revise Quadratic Equations"
 * is advice, "revise the Algebra chapter on Quadratic Equations" is an
 * instruction a student can follow into a textbook.
 *
 * Priority is shown for weak topics only. Ranking strengths by urgency would be
 * meaningless, and a "LOW priority" tag beside a strength reads as a criticism.
 */
export function TopicCard({ subject, chapter, topic, percentage, variant }: TopicCardProps) {
    const isStrong = variant === 'strong';
    const priority = topicPriority(percentage);

    return (
        <div
            className={cn(
                'flex items-start gap-3 rounded-lg p-3.5 ring-1',
                isStrong ? 'bg-emerald-50/50 ring-emerald-100' : 'bg-red-50/50 ring-red-100',
            )}
        >
            <span
                className={cn(
                    'mt-0.5 grid size-7 shrink-0 place-items-center rounded-full',
                    isStrong ? 'bg-emerald-100 text-emerald-700' : 'bg-red-100 text-red-700',
                )}
                aria-hidden
            >
                {isStrong ? <Check className="size-4" /> : <TriangleAlert className="size-4" />}
            </span>

            <div className="min-w-0 flex-1">
                <div className="flex items-start justify-between gap-2">
                    <p className="text-sm font-semibold text-slate-900">{topic}</p>
                    <span
                        className={cn(
                            'shrink-0 text-sm font-semibold tabular-nums',
                            isStrong ? 'text-emerald-700' : 'text-red-700',
                        )}
                    >
                        {percent(percentage)}
                    </span>
                </div>

                {(subject || chapter) && (
                    <p className="mt-1 flex items-center gap-1.5 text-xs text-slate-500">
                        <BookOpen className="size-3 shrink-0" aria-hidden />
                        <span className="truncate">
                            {subject}
                            {subject && chapter && ' · '}
                            {chapter}
                        </span>
                    </p>
                )}

                {!isStrong && (
                    <Badge classes={PRIORITY_STYLES[priority]} className="mt-2">
                        {priority} priority
                    </Badge>
                )}
            </div>
        </div>
    );
}
