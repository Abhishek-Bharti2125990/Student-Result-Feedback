import { Lightbulb, TriangleAlert, Trophy } from 'lucide-react';
import { Badge } from '@/components/ui/Badge';
import { CategoryBadge } from '@/components/domain/CategoryBadge';
import { categoryStyle } from '@/lib/category';
import { cn } from '@/lib/cn';
import { percent, rank } from '@/lib/format';
import type { StudentCategoryCard } from '@/types/teacher';

interface Props {
    student: StudentCategoryCard;
    classSize: number;
}

/**
 * One student, as the teacher dashboard shows them.
 *
 * Everything needed for the next decision is on the card: the score, the topics
 * they are losing marks on, and one concrete action. There is deliberately no
 * link to a fuller profile - the backend has no per-student teacher view, and a
 * link that opened a thinner page than this one would be a step backwards.
 */
export function StudentCategoryCardView({ student, classSize }: Props) {
    const style = categoryStyle(student.category);

    return (
        <article className="rounded-xl bg-white p-4 ring-1 ring-slate-200/80 shadow-sm transition hover:shadow-md">
            <header className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                    <h3 className="truncate text-sm font-semibold text-slate-900">{student.studentName}</h3>
                    <p className="mt-0.5 text-xs text-slate-500">
                        {student.admissionNo && <span>ID {student.admissionNo} · </span>}
                        Class {student.className}
                        {student.section ? `-${student.section}` : ''} · Rank{' '}
                        {rank(student.rankInClass, classSize)}
                    </p>
                </div>
                <div className="shrink-0 text-right">
                    <p className={cn('text-xl font-semibold tabular-nums', scoreColour(student.category))}>
                        {percent(student.percentage)}
                    </p>
                    <p className="text-xs text-slate-500">Grade {student.grade}</p>
                </div>
            </header>

            <div className="mt-3">
                <CategoryBadge category={student.category} />
            </div>

            {student.weakTopics.length > 0 && (
                <section className="mt-3.5">
                    <p className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-slate-500">
                        <TriangleAlert className="size-3 text-red-500" aria-hidden />
                        Weak topics
                    </p>
                    <div className="mt-1.5 flex flex-wrap gap-1.5">
                        {student.weakTopics.map((topic) => (
                            <Badge key={topic} tone="red">
                                {topic}
                            </Badge>
                        ))}
                    </div>
                </section>
            )}

            {student.weakTopics.length === 0 && student.strongTopics.length > 0 && (
                <section className="mt-3.5">
                    <p className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-slate-500">
                        <Trophy className="size-3 text-emerald-600" aria-hidden />
                        Strong topics
                    </p>
                    <div className="mt-1.5 flex flex-wrap gap-1.5">
                        {student.strongTopics.map((topic) => (
                            <Badge key={topic} tone="green">
                                {topic}
                            </Badge>
                        ))}
                    </div>
                </section>
            )}

            <section className={cn('mt-3.5 rounded-lg p-3 ring-1 ring-slate-100', style.surface)}>
                <p className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-slate-500">
                    <Lightbulb className="size-3 text-amber-500" aria-hidden />
                    Suggested action
                </p>
                <p className="mt-1 text-sm leading-relaxed text-slate-700">{student.suggestedAction}</p>
            </section>
        </article>
    );
}

function scoreColour(category: StudentCategoryCard['category']): string {
    switch (category) {
        case 'CRITICAL':
            return 'text-red-600';
        case 'AVERAGE':
            return 'text-amber-600';
        case 'GOOD':
            return 'text-blue-600';
        case 'EXCELLENT':
            return 'text-emerald-600';
    }
}
