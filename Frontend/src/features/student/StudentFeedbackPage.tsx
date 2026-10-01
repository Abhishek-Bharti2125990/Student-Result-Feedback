import { useEffect, useMemo } from 'react';
import {
    BookMarked,
    CalendarCheck,
    CheckCircle2,
    Circle,
    Clock,
    Quote,
    RefreshCw,
    Sparkles,
    ThumbsUp,
    TriangleAlert,
} from 'lucide-react';
import { PageHeader } from '@/components/layout/AppLayout';
import { AiProvenanceBadge } from '@/components/domain/AiProvenanceBadge';
import { FeedbackCard } from '@/components/domain/FeedbackCard';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { ProgressBar } from '@/components/ui/ProgressBar';
import { CardSkeleton, Skeleton } from '@/components/ui/Skeleton';
import { cn } from '@/lib/cn';
import { plural } from '@/lib/format';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import { fetchStudentDashboard, fetchStudentFeedback } from '@/store/slices/studentSlice';
import { notify, togglePlanItem } from '@/store/slices/uiSlice';
import type { StudyPlanItem } from '@/types/student';

export function StudentFeedbackPage() {
    const dispatch = useAppDispatch();
    const { dashboard, feedback, feedbackLoading, feedbackError, regenerating } = useAppSelector(
        (state) => state.student,
    );
    const completed = useAppSelector((state) => state.ui.completedPlanItems);

    useEffect(() => {
        void dispatch(fetchStudentFeedback());
        // The header shows which exam the feedback is about, which only the
        // dashboard payload knows.
        if (!dashboard) {
            void dispatch(fetchStudentDashboard());
        }
    }, [dispatch, dashboard]);

    // Memoised because `?? []` would otherwise hand back a fresh array on every
    // render and invalidate the count below with it.
    const plan = useMemo(() => feedback?.feedback.studyPlan ?? [], [feedback]);

    /**
     * Ticks are keyed by exam so a new exam starts with a fresh checklist
     * rather than inheriting last term's progress by index.
     */
    const planKeyPrefix = `${dashboard?.examId ?? 'exam'}`;

    const doneCount = useMemo(
        () => plan.filter((_, index) => completed[`${planKeyPrefix}:${index}`]).length,
        [plan, completed, planKeyPrefix],
    );

    const regenerate = () => {
        void dispatch(fetchStudentFeedback({ refresh: true }))
            .unwrap()
            .then(() =>
                dispatch(
                    notify({
                        kind: 'success',
                        title: 'Feedback regenerated',
                        message: 'Built again from your latest marks.',
                    }),
                ),
            )
            .catch(() => undefined);
    };

    if (feedbackLoading && !feedback) {
        return (
            <>
                <PageHeader title="AI feedback" description="Generating from your results…" />
                <Skeleton className="h-28 w-full rounded-xl" />
                <div className="mt-6 grid gap-6 lg:grid-cols-2">
                    <CardSkeleton lines={5} />
                    <CardSkeleton lines={5} />
                </div>
                <div className="mt-6">
                    <CardSkeleton lines={6} />
                </div>
            </>
        );
    }

    if (feedbackError && !feedback) {
        return (
            <>
                <PageHeader title="AI feedback" />
                <ErrorState
                    title="No feedback available"
                    message={feedbackError}
                    onRetry={() => void dispatch(fetchStudentFeedback())}
                    retrying={feedbackLoading}
                />
            </>
        );
    }

    if (!feedback) {
        return (
            <>
                <PageHeader title="AI feedback" />
                <Card>
                    <EmptyState
                        title="Nothing written yet"
                        description="Feedback is generated once your results are imported."
                        icon={<Sparkles className="size-6" aria-hidden />}
                    />
                </Card>
            </>
        );
    }

    const body = feedback.feedback;

    return (
        <>
            <PageHeader
                title="AI feedback"
                description={
                    dashboard
                        ? `${dashboard.examName} · built from your subject and topic marks`
                        : 'Built from your subject and topic marks'
                }
                action={
                    <Button
                        variant="outline"
                        size="sm"
                        onClick={regenerate}
                        loading={regenerating}
                        leftIcon={<RefreshCw className="size-3.5" aria-hidden />}
                    >
                        {regenerating ? 'Regenerating…' : 'Regenerate'}
                    </Button>
                }
            />

            {/* -- Summary + provenance --------------------------------------- */}
            <Card className="bg-gradient-to-br from-violet-50 to-blue-50 ring-violet-100">
                <div className="flex items-start gap-4">
                    <span className="grid size-11 shrink-0 place-items-center rounded-xl bg-violet-600 text-white">
                        <Quote className="size-5" aria-hidden />
                    </span>
                    <div className="min-w-0 flex-1">
                        <p className="text-base leading-relaxed text-slate-800">{body.summary}</p>
                        <div className="mt-3">
                            <AiProvenanceBadge
                                source={feedback.source}
                                model={feedback.model}
                                generatedAt={feedback.generatedAt}
                                cached={feedback.cached}
                            />
                        </div>
                    </div>
                </div>
            </Card>

            {feedback.source === 'FALLBACK' && (
                <p className="mt-2.5 text-xs leading-relaxed text-slate-500">
                    No Claude API key is configured on the server, so this was written locally from
                    the same analytics. Every number in it is still your own.
                </p>
            )}

            {/* -- Strengths and weaknesses ----------------------------------- */}
            <section className="mt-6 grid gap-6 lg:grid-cols-2">
                <FeedbackCard
                    title="Your strengths"
                    icon={<ThumbsUp className="size-4" aria-hidden />}
                    items={body.strengths}
                    tone="strength"
                    emptyText="No strengths were flagged for this exam."
                />
                <FeedbackCard
                    title="What to improve"
                    icon={<TriangleAlert className="size-4" aria-hidden />}
                    items={body.weaknesses}
                    tone="weakness"
                    emptyText="Nothing fell below the weakness thresholds."
                />
            </section>

            {/* -- Study plan -------------------------------------------------- */}
            <section className="mt-6">
                <Card>
                    <CardHeader
                        title="Your study plan"
                        subtitle={
                            plan.length > 0
                                ? `${doneCount} of ${plural(plan.length, 'day')} done`
                                : undefined
                        }
                        icon={<CalendarCheck className="size-4" aria-hidden />}
                    />

                    {plan.length === 0 ? (
                        <EmptyState
                            title="No plan needed right now"
                            description="Nothing was flagged as weak, so keep your current routine."
                        />
                    ) : (
                        <>
                            <div className="mt-4">
                                <ProgressBar value={plan.length ? doneCount / plan.length : 0} />
                            </div>

                            <ol className="mt-4 space-y-2.5">
                                {plan.map((item, index) => (
                                    <PlanRow
                                        key={`${item.subject}-${item.topic}-${index}`}
                                        item={item}
                                        step={index + 1}
                                        done={Boolean(completed[`${planKeyPrefix}:${index}`])}
                                        onToggle={() =>
                                            dispatch(togglePlanItem(`${planKeyPrefix}:${index}`))
                                        }
                                    />
                                ))}
                            </ol>

                            <p className="mt-3.5 text-xs text-slate-400">
                                Ticks are kept in this browser only — the server has no endpoint for
                                plan progress, so they will not follow you to another device.
                            </p>
                        </>
                    )}
                </Card>
            </section>

            {/* -- Closing note ------------------------------------------------ */}
            {body.motivationalNote && (
                <Card className="mt-6 bg-blue-50/60 ring-blue-100">
                    <div className="flex items-start gap-3">
                        <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-blue-100 text-blue-700">
                            <Sparkles className="size-4" aria-hidden />
                        </span>
                        <p className="text-sm leading-relaxed text-slate-700">{body.motivationalNote}</p>
                    </div>
                </Card>
            )}
        </>
    );
}

interface PlanRowProps {
    item: StudyPlanItem;
    step: number;
    done: boolean;
    onToggle: () => void;
}

function PlanRow({ item, step, done, onToggle }: PlanRowProps) {
    return (
        <li>
            <button
                type="button"
                onClick={onToggle}
                aria-pressed={done}
                className={cn(
                    'flex w-full items-start gap-3 rounded-lg p-3.5 text-left ring-1 transition',
                    done
                        ? 'bg-emerald-50/60 ring-emerald-100'
                        : 'bg-white ring-slate-200 hover:bg-slate-50',
                )}
            >
                <span className="mt-0.5 shrink-0" aria-hidden>
                    {done ? (
                        <CheckCircle2 className="size-5 text-emerald-600" />
                    ) : (
                        <Circle className="size-5 text-slate-300" />
                    )}
                </span>

                <span className="min-w-0 flex-1">
                    <span className="flex flex-wrap items-center gap-2">
                        <Badge tone={done ? 'green' : 'blue'}>Day {step}</Badge>
                        <Badge tone="slate">{item.timeframe}</Badge>
                        {item.dailyMinutes > 0 && (
                            <span className="inline-flex items-center gap-1 text-xs text-slate-500">
                                <Clock className="size-3" aria-hidden />
                                {item.dailyMinutes} min/day
                            </span>
                        )}
                    </span>

                    <span
                        className={cn(
                            'mt-2 block text-sm font-semibold',
                            done ? 'text-slate-500 line-through' : 'text-slate-900',
                        )}
                    >
                        {item.subject}
                        {item.topic && item.topic !== 'General' ? ` · ${item.topic}` : ''}
                    </span>

                    <span
                        className={cn(
                            'mt-1 block text-sm leading-relaxed',
                            done ? 'text-slate-400' : 'text-slate-600',
                        )}
                    >
                        {item.action}
                    </span>

                    {item.resource && (
                        <span className="mt-2 inline-flex items-center gap-1.5 rounded-md bg-slate-100 px-2 py-1 text-xs text-slate-600">
                            <BookMarked className="size-3" aria-hidden />
                            {item.resource}
                        </span>
                    )}
                </span>
            </button>
        </li>
    );
}
