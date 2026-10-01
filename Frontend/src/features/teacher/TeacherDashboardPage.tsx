import { useEffect, useMemo } from 'react';
import {
    BarChart3,
    ClipboardList,
    Lightbulb,
    PieChart as PieIcon,
    RefreshCw,
    Repeat,
    Sparkles,
    TrendingDown,
    TrendingUp,
    TriangleAlert,
    Users,
} from 'lucide-react';
import { PageHeader } from '@/components/layout/AppLayout';
import { CategoryDistributionChart } from '@/components/charts/CategoryDistributionChart';
import { ClassSubjectChart } from '@/components/charts/ClassSubjectChart';
import { WeakTopicChart } from '@/components/charts/WeakTopicChart';
import { AiProvenanceBadge } from '@/components/domain/AiProvenanceBadge';
import { CategoryBadge } from '@/components/domain/CategoryBadge';
import { FeedbackCard } from '@/components/domain/FeedbackCard';
import { StudentCategoryCardView } from '@/components/domain/StudentCategoryCardView';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { ChartCard } from '@/components/ui/ChartCard';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { StatCard } from '@/components/ui/StatCard';
import { ChartSkeleton, StatRowSkeleton } from '@/components/ui/Skeleton';
import { CATEGORY_ORDER, categoryStyle } from '@/lib/category';
import { cn } from '@/lib/cn';
import { percent, plural } from '@/lib/format';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import { fetchClassReport } from '@/store/slices/analyticsSlice';
import { fetchTeacherDashboard, selectCategory } from '@/store/slices/teacherSlice';

export function TeacherDashboardPage() {
    const dispatch = useAppDispatch();
    const { dashboard, loading, error, noData, selectedCategory, filters } = useAppSelector(
        (state) => state.teacher,
    );
    const { classReport } = useAppSelector((state) => state.analytics);

    useEffect(() => {
        void dispatch(fetchTeacherDashboard(filters));
    }, [dispatch, filters]);

    // The dashboard payload has no per-subject class figures, so the subject
    // chart needs a second call - which can only be made once the dashboard has
    // told us which class and exam are being shown.
    useEffect(() => {
        if (dashboard) {
            void dispatch(
                fetchClassReport({ className: dashboard.className, examId: dashboard.examId }),
            );
        }
    }, [dispatch, dashboard]);

    const bucket = useMemo(
        () => dashboard?.buckets.find((candidate) => candidate.category === selectedCategory),
        [dashboard, selectedCategory],
    );

    if (loading && !dashboard) {
        return (
            <>
                <PageHeader title="Class dashboard" description="Loading the latest exam…" />
                <StatRowSkeleton />
                <div className="mt-6">
                    <StatRowSkeleton />
                </div>
                <div className="mt-6 grid gap-6 lg:grid-cols-2">
                    <ChartSkeleton />
                    <ChartSkeleton />
                </div>
            </>
        );
    }

    if (noData) {
        return (
            <>
                <PageHeader title="Class dashboard" />
                <Card>
                    <EmptyState
                        title="No results imported yet"
                        description="Once an admin uploads a results CSV, your class appears here split into score categories."
                        icon={<Users className="size-6" aria-hidden />}
                    />
                </Card>
            </>
        );
    }

    if (error || !dashboard) {
        return (
            <>
                <PageHeader title="Class dashboard" />
                <ErrorState
                    message={error ?? 'The class dashboard could not be loaded.'}
                    onRetry={() => void dispatch(fetchTeacherDashboard(filters))}
                    retrying={loading}
                />
            </>
        );
    }

    const guidance = dashboard.aiGuidance?.feedback;

    return (
        <>
            <PageHeader
                title={`Class ${dashboard.className}`}
                description={`${dashboard.examName} · ${plural(dashboard.totalStudents, 'student')}`}
                action={
                    <Button
                        variant="outline"
                        size="sm"
                        onClick={() => void dispatch(fetchTeacherDashboard(filters))}
                        loading={loading}
                        leftIcon={<RefreshCw className="size-3.5" aria-hidden />}
                    >
                        Refresh
                    </Button>
                }
            />

            {/* -- Class summary KPIs ----------------------------------------- */}
            <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                <StatCard
                    label="Total students"
                    value={dashboard.totalStudents}
                    hint={`Class ${dashboard.className}`}
                    icon={<Users className="size-5" aria-hidden />}
                />
                <StatCard
                    label="Class average"
                    value={percent(dashboard.classAveragePercentage)}
                    hint={`${dashboard.examName}`}
                    icon={<BarChart3 className="size-5" aria-hidden />}
                />
                <StatCard
                    label="Highest"
                    value={percent(dashboard.highestPercentage)}
                    hint="Top of the class"
                    icon={<TrendingUp className="size-5" aria-hidden />}
                    iconClassName="bg-emerald-50 text-emerald-600"
                />
                <StatCard
                    label="Lowest"
                    value={percent(dashboard.lowestPercentage)}
                    hint="Needs attention first"
                    icon={<TrendingDown className="size-5" aria-hidden />}
                    iconClassName="bg-red-50 text-red-600"
                />
            </section>

            {/* -- Category overview: also the filter ------------------------- */}
            <section className="mt-6">
                <h2 className="mb-3 text-sm font-semibold text-slate-900">Score categories</h2>
                <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                    {CATEGORY_ORDER.map((category) => {
                        const count =
                            dashboard.categoryCounts.find((candidate) => candidate.category === category)
                                ?.students ?? 0;
                        const style = categoryStyle(category);

                        return (
                            <StatCard
                                key={category}
                                label={style.label}
                                value={count}
                                hint={style.heading}
                                accentClassName={style.accent}
                                selected={selectedCategory === category}
                                onClick={() => dispatch(selectCategory(category))}
                                icon={<Users className="size-5" aria-hidden />}
                                iconClassName={style.solid}
                            />
                        );
                    })}
                </div>
                <p className="mt-2.5 text-xs text-slate-500">
                    Select a category to see those students below.
                </p>
            </section>

            {/* -- Students in the selected category -------------------------- */}
            <section className="mt-6">
                <Card>
                    <CardHeader
                        title={categoryStyle(selectedCategory).heading}
                        subtitle={
                            bucket
                                ? `${plural(bucket.studentCount, 'student')} · worst score first`
                                : undefined
                        }
                        icon={<ClipboardList className="size-4" aria-hidden />}
                        action={<CategoryBadge category={selectedCategory} />}
                    />

                    {!bucket || bucket.students.length === 0 ? (
                        <EmptyState
                            title={`No students in ${categoryStyle(selectedCategory).label.toLowerCase()}`}
                            description={
                                selectedCategory === 'CRITICAL'
                                    ? 'Nobody is below 50% in this exam.'
                                    : 'Try another category above.'
                            }
                        />
                    ) : (
                        <div className="mt-4 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                            {bucket.students.map((student) => (
                                <StudentCategoryCardView
                                    key={student.studentId}
                                    student={student}
                                    classSize={dashboard.totalStudents}
                                />
                            ))}
                        </div>
                    )}
                </Card>
            </section>

            {/* -- Analytics -> AI recommendation, side by side --------------- */}
            <section className="mt-6">
                <div className="mb-3 flex flex-wrap items-center gap-2">
                    <h2 className="text-sm font-semibold text-slate-900">Topics to re-teach</h2>
                    <span className="text-xs text-slate-400">
                        analytics on the left, what to do about it on the right
                    </span>
                </div>

                {/* `items-start` so the chart card keeps its own height. Stretched
                    to match the taller guidance column it would show 300px of
                    empty white under a 320px plot. */}
                <div className="grid items-start gap-6 lg:grid-cols-5">
                    <ChartCard
                        title="Most failed topics"
                        subtitle="How many students are weak on each"
                        icon={<Repeat className="size-4" aria-hidden />}
                        height={320}
                        className="lg:col-span-3"
                        hasData={dashboard.weakestTopics.length > 0}
                        emptyTitle="No class-wide weak topic"
                        emptyDescription="No topic was failed by enough students to be a teaching issue."
                    >
                        <WeakTopicChart topics={dashboard.weakestTopics} />
                    </ChartCard>

                    <Card className="lg:col-span-2">
                        <CardHeader
                            title="AI guidance"
                            subtitle="Generated from the analytics beside it"
                            icon={<Sparkles className="size-4" aria-hidden />}
                        />

                        {!dashboard.aiGuidance || !guidance ? (
                            <EmptyState
                                title="No guidance written yet"
                                description="The import job writes this once results are processed."
                                icon={<Sparkles className="size-5" aria-hidden />}
                            />
                        ) : (
                            <div className="mt-4 space-y-4">
                                <AiProvenanceBadge
                                    source={dashboard.aiGuidance.source}
                                    model={dashboard.aiGuidance.model}
                                    generatedAt={dashboard.aiGuidance.generatedAt}
                                    cached={dashboard.aiGuidance.cached}
                                />

                                <p className="rounded-lg bg-slate-50 p-3.5 text-sm leading-relaxed text-slate-700">
                                    {guidance.classSummary}
                                </p>

                                <FeedbackCard
                                    title="Interventions"
                                    icon={<Lightbulb className="size-4" aria-hidden />}
                                    items={guidance.interventionSuggestions}
                                    tone="neutral"
                                />

                                <FeedbackCard
                                    title="Remedial actions"
                                    icon={<ClipboardList className="size-4" aria-hidden />}
                                    items={guidance.remedialRecommendations}
                                    tone="weakness"
                                />

                                {guidance.topicsToReteach.length > 0 && (
                                    <div>
                                        <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                                            Re-teach
                                        </p>
                                        <div className="mt-2 flex flex-wrap gap-1.5">
                                            {guidance.topicsToReteach.map((topic) => (
                                                <Badge key={topic} tone="amber">
                                                    {topic}
                                                </Badge>
                                            ))}
                                        </div>
                                    </div>
                                )}
                            </div>
                        )}
                    </Card>
                </div>
            </section>

            {/* -- Priority students, from the AI ----------------------------- */}
            {guidance && guidance.weakStudents.length > 0 && (
                <section className="mt-6">
                    <Card>
                        <CardHeader
                            title="Students flagged by the AI"
                            subtitle="Most urgent first, with one action each"
                            icon={<TriangleAlert className="size-4" aria-hidden />}
                        />
                        <ul className="mt-4 space-y-2.5">
                            {guidance.weakStudents.map((note, index) => (
                                <li
                                    key={`${note.studentName}-${index}`}
                                    className="rounded-lg bg-slate-50 p-3.5 ring-1 ring-slate-100"
                                >
                                    <div className="flex flex-wrap items-center gap-2">
                                        <span className="text-sm font-semibold text-slate-900">
                                            {note.studentName}
                                        </span>
                                        <Badge tone={priorityTone(note.priority)}>{note.priority}</Badge>
                                    </div>
                                    <p className="mt-1.5 text-sm text-slate-600">{note.concern}</p>
                                    <p className="mt-1.5 flex items-start gap-1.5 text-sm text-slate-700">
                                        <Lightbulb
                                            className="mt-0.5 size-3.5 shrink-0 text-amber-500"
                                            aria-hidden
                                        />
                                        {note.suggestedAction}
                                    </p>
                                </li>
                            ))}
                        </ul>
                    </Card>
                </section>
            )}

            {/* -- Visual analytics ------------------------------------------- */}
            <section className="mt-6 grid gap-6 lg:grid-cols-2">
                <ChartCard
                    title="Score category distribution"
                    subtitle="How the class splits across the four bands"
                    icon={<PieIcon className="size-4" aria-hidden />}
                    hasData={dashboard.categoryCounts.some((count) => count.students > 0)}
                    emptyTitle="No categorised students"
                >
                    <CategoryDistributionChart counts={dashboard.categoryCounts} />
                </ChartCard>

                <ChartCard
                    title="Class subject performance"
                    subtitle="Average per subject, weakest first"
                    icon={<BarChart3 className="size-4" aria-hidden />}
                    hasData={(classReport?.subjectStats.length ?? 0) > 0}
                    emptyTitle="Subject figures unavailable"
                    emptyDescription="The class report for this exam could not be loaded."
                >
                    <ClassSubjectChart stats={classReport?.subjectStats ?? []} />
                </ChartCard>
            </section>

            {/* -- Weak topic table ------------------------------------------- */}
            {dashboard.weakestTopics.length > 0 && (
                <section className="mt-6">
                    <Card>
                        <CardHeader
                            title="Weak topic detail"
                            subtitle="Chapter and class average for each flagged topic"
                            icon={<Repeat className="size-4" aria-hidden />}
                        />
                        <ul className="mt-4 divide-y divide-slate-100">
                            {dashboard.weakestTopics.map((topic) => (
                                <li
                                    key={`${topic.subjectCode}-${topic.topicName}`}
                                    className="flex flex-wrap items-center gap-3 py-3 first:pt-0 last:pb-0"
                                >
                                    <div className="min-w-0 flex-1">
                                        <p className="text-sm font-medium text-slate-900">
                                            {topic.topicName}
                                        </p>
                                        <p className="mt-0.5 text-xs text-slate-500">
                                            {topic.subjectName}
                                            {topic.chapterName ? ` · ${topic.chapterName}` : ''}
                                        </p>
                                    </div>
                                    <Badge tone="red">
                                        {plural(topic.weakStudents, 'student')} weak
                                    </Badge>
                                    <span
                                        className={cn(
                                            'w-16 text-right text-sm font-semibold tabular-nums',
                                            topic.classAveragePercentage < 50
                                                ? 'text-red-600'
                                                : 'text-slate-700',
                                        )}
                                    >
                                        {percent(topic.classAveragePercentage)}
                                    </span>
                                </li>
                            ))}
                        </ul>
                    </Card>
                </section>
            )}
        </>
    );
}

function priorityTone(priority: string): 'red' | 'amber' | 'slate' {
    switch (priority.toUpperCase()) {
        case 'HIGH':
            return 'red';
        case 'MEDIUM':
            return 'amber';
        default:
            return 'slate';
    }
}
