import { useEffect, useMemo } from 'react';
import { Link } from 'react-router-dom';
import {
    Award,
    BarChart3,
    BookOpen,
    Check,
    GaugeCircle,
    Medal,
    Sparkles,
    TrendingDown,
    TrendingUp,
    TriangleAlert,
    Users,
} from 'lucide-react';
import { PageHeader } from '@/components/layout/AppLayout';
import { PerformanceTrendChart } from '@/components/charts/PerformanceTrendChart';
import { StrongVsWeakTopicsChart } from '@/components/charts/StrongVsWeakTopicsChart';
import { SubjectComparisonChart } from '@/components/charts/SubjectComparisonChart';
import { CategoryBadge } from '@/components/domain/CategoryBadge';
import { TopicCard } from '@/components/domain/TopicCard';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { ChartCard } from '@/components/ui/ChartCard';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { ChartSkeleton, StatRowSkeleton } from '@/components/ui/Skeleton';
import { categoryStyle } from '@/lib/category';
import { cn } from '@/lib/cn';
import { marks, percent, rank, signedPercent } from '@/lib/format';
import {
    STRONG_SUBJECT_THRESHOLD,
    WEAK_SUBJECT_THRESHOLD,
    strongSubjects,
    weakSubjects,
} from '@/lib/subjects';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import { fetchMyTrend } from '@/store/slices/analyticsSlice';
import { fetchStudentDashboard } from '@/store/slices/studentSlice';
import type { SubjectPerformance } from '@/types/common';

export function StudentDashboardPage() {
    const dispatch = useAppDispatch();
    const { dashboard, dashboardLoading, dashboardError, noData } = useAppSelector(
        (state) => state.student,
    );
    const { trend, trendLoading } = useAppSelector((state) => state.analytics);

    useEffect(() => {
        void dispatch(fetchStudentDashboard());
        void dispatch(fetchMyTrend());
    }, [dispatch]);

    // Derived on the client from `subjects[]`: the dashboard payload carries
    // per-subject percentages but no subject-level strong/weak split.
    const strong = useMemo(() => strongSubjects(dashboard?.subjects ?? []), [dashboard]);
    const weak = useMemo(() => weakSubjects(dashboard?.subjects ?? []), [dashboard]);

    if (dashboardLoading && !dashboard) {
        return (
            <>
                <PageHeader title="Your results" description="Loading your latest exam…" />
                <StatRowSkeleton />
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
                <PageHeader title="Your results" />
                <Card>
                    <EmptyState
                        title="No results published yet"
                        description="Once your school uploads this term's results, your percentage, rank and topic analysis will appear here."
                        icon={<GaugeCircle className="size-6" aria-hidden />}
                    />
                </Card>
            </>
        );
    }

    if (dashboardError || !dashboard) {
        return (
            <>
                <PageHeader title="Your results" />
                <ErrorState
                    message={dashboardError ?? 'Your dashboard could not be loaded.'}
                    onRetry={() => void dispatch(fetchStudentDashboard())}
                    retrying={dashboardLoading}
                />
            </>
        );
    }

    const style = categoryStyle(dashboard.category);
    const vsClass = dashboard.percentage - dashboard.classAveragePercentage;

    return (
        <>
            <PageHeader
                title={`Hello, ${firstName(dashboard.studentName)}`}
                description={`${dashboard.examName} · Class ${dashboard.className}${
                    dashboard.section ? `-${dashboard.section}` : ''
                }`}
                action={
                    <Link to="/student/feedback">
                        <Button
                            variant="outline"
                            size="sm"
                            leftIcon={<Sparkles className="size-4" aria-hidden />}
                        >
                            AI feedback
                        </Button>
                    </Link>
                }
            />

            {/* -- Profile + headline performance ----------------------------- */}
            <section className="grid gap-4 lg:grid-cols-3">
                <Card className={cn('lg:col-span-1', style.surface)}>
                    <div className="flex items-start gap-3">
                        <span className="grid size-12 shrink-0 place-items-center rounded-xl bg-blue-600 text-base font-semibold text-white">
                            {firstName(dashboard.studentName).charAt(0)}
                        </span>
                        <div className="min-w-0">
                            <p className="truncate text-base font-semibold text-slate-900">
                                {dashboard.studentName}
                            </p>
                            <p className="mt-0.5 text-sm text-slate-600">
                                Class {dashboard.className}
                                {dashboard.section ? ` · Section ${dashboard.section}` : ''}
                            </p>
                            <p className="mt-0.5 text-xs text-slate-500">ID {dashboard.admissionNo}</p>
                        </div>
                    </div>

                    <div className="mt-4 flex flex-wrap items-center gap-2">
                        <CategoryBadge category={dashboard.category} />
                        <Badge tone={dashboard.passed ? 'green' : 'red'}>
                            {dashboard.passed ? 'Pass' : 'Below pass mark'}
                        </Badge>
                    </div>
                    <p className="mt-3 text-xs leading-relaxed text-slate-600">
                        {style.heading} — {style.description.toLowerCase()}.
                    </p>
                </Card>

                <div className="grid gap-4 sm:grid-cols-3 lg:col-span-2">
                    <Card>
                        <p className="text-xs font-medium uppercase tracking-wide text-slate-500">
                            Overall
                        </p>
                        <p className="mt-2 text-3xl font-semibold tabular-nums text-slate-900">
                            {percent(dashboard.percentage)}
                        </p>
                        <p
                            className={cn(
                                'mt-1.5 inline-flex items-center gap-1 text-xs font-medium',
                                vsClass >= 0 ? 'text-emerald-600' : 'text-red-600',
                            )}
                        >
                            {vsClass >= 0 ? (
                                <TrendingUp className="size-3.5" aria-hidden />
                            ) : (
                                <TrendingDown className="size-3.5" aria-hidden />
                            )}
                            {signedPercent(vsClass)} vs class
                        </p>
                    </Card>

                    <Card>
                        <p className="text-xs font-medium uppercase tracking-wide text-slate-500">Grade</p>
                        <p className="mt-2 text-3xl font-semibold text-slate-900">{dashboard.grade}</p>
                        <p className="mt-1.5 inline-flex items-center gap-1 text-xs text-slate-500">
                            <Award className="size-3.5" aria-hidden />
                            {dashboard.examName}
                        </p>
                    </Card>

                    <Card>
                        <p className="text-xs font-medium uppercase tracking-wide text-slate-500">Rank</p>
                        <p className="mt-2 text-3xl font-semibold tabular-nums text-slate-900">
                            {dashboard.rankInClass || '—'}
                        </p>
                        <p className="mt-1.5 inline-flex items-center gap-1 text-xs text-slate-500">
                            <Medal className="size-3.5" aria-hidden />
                            {rank(dashboard.rankInClass, dashboard.classSize)}
                        </p>
                    </Card>

                    <Card className="sm:col-span-3">
                        <CardHeader
                            title="Class average"
                            subtitle={`${percent(dashboard.classAveragePercentage)} across ${dashboard.classSize} students`}
                            icon={<Users className="size-4" aria-hidden />}
                        />
                        <div className="mt-4 space-y-2">
                            <ComparisonBar
                                label="You"
                                value={dashboard.percentage}
                                barClassName="bg-blue-600"
                            />
                            <ComparisonBar
                                label="Class"
                                value={dashboard.classAveragePercentage}
                                barClassName="bg-slate-400"
                            />
                        </div>
                    </Card>
                </div>
            </section>

            {/* -- Strong / weak subjects ------------------------------------- */}
            <section className="mt-6 grid gap-6 lg:grid-cols-2">
                <Card>
                    <CardHeader
                        title="Strong subjects"
                        subtitle={`At or above ${STRONG_SUBJECT_THRESHOLD}%`}
                        icon={<Check className="size-4" aria-hidden />}
                    />
                    {strong.length === 0 ? (
                        <EmptyState
                            title="No subject has cleared 75% yet"
                            description="Your best subject is still the place to build from — see the chart below."
                        />
                    ) : (
                        <ul className="mt-4 space-y-2">
                            {strong.map((subject) => (
                                <SubjectRow key={subject.subjectId} subject={subject} variant="strong" />
                            ))}
                        </ul>
                    )}
                </Card>

                <Card>
                    <CardHeader
                        title="Weak subjects"
                        subtitle={`Below ${WEAK_SUBJECT_THRESHOLD}%`}
                        icon={<TriangleAlert className="size-4" aria-hidden />}
                    />
                    {weak.length === 0 ? (
                        <EmptyState
                            title="No subject is below 50%"
                            description="Nothing is in the danger zone. Work from the topic list instead."
                        />
                    ) : (
                        <ul className="mt-4 space-y-2">
                            {weak.map((subject) => (
                                <SubjectRow key={subject.subjectId} subject={subject} variant="weak" />
                            ))}
                        </ul>
                    )}
                </Card>
            </section>

            {/* -- Charts ------------------------------------------------------ */}
            <section className="mt-6 grid gap-6 lg:grid-cols-2">
                <ChartCard
                    title="Subject performance"
                    subtitle="Your score against the class average"
                    icon={<BarChart3 className="size-4" aria-hidden />}
                    hasData={dashboard.subjects.length > 0}
                    emptyTitle="No subject marks in this exam"
                >
                    <SubjectComparisonChart subjects={dashboard.subjects} />
                </ChartCard>

                {trendLoading && !trend ? (
                    <ChartSkeleton />
                ) : (
                    <ChartCard
                        title="Performance trend"
                        subtitle={trendSubtitle(trend?.overallDirection, trend?.overallChange)}
                        icon={<TrendingUp className="size-4" aria-hidden />}
                        hasData={(trend?.overall.length ?? 0) > 1}
                        emptyTitle="One exam so far"
                        emptyDescription="A trend needs at least two exams to compare."
                    >
                        <PerformanceTrendChart points={trend?.overall ?? []} />
                    </ChartCard>
                )}
            </section>

            {/* -- Topic analysis --------------------------------------------- */}
            <section className="mt-6">
                <ChartCard
                    title="Strong vs weak topics"
                    subtitle="Everything on one scale, with the 50% line marked"
                    icon={<BarChart3 className="size-4" aria-hidden />}
                    height={320}
                    hasData={dashboard.strongTopics.length + dashboard.weakTopics.length > 0}
                    emptyTitle="No topic-level marks recorded"
                >
                    <StrongVsWeakTopicsChart
                        strongTopics={dashboard.strongTopics}
                        weakTopics={dashboard.weakTopics}
                    />
                </ChartCard>
            </section>

            <section className="mt-6 grid gap-6 lg:grid-cols-2">
                <Card>
                    <CardHeader
                        title="Topics you have secure"
                        subtitle={`${dashboard.strongTopics.length} topic(s)`}
                        icon={<Check className="size-4" aria-hidden />}
                    />
                    {dashboard.strongTopics.length === 0 ? (
                        <EmptyState title="No strong topics flagged in this exam" />
                    ) : (
                        <div className="mt-4 grid gap-2.5">
                            {dashboard.strongTopics.map((topic) => (
                                <TopicCard
                                    key={`${topic.subject}-${topic.topic}`}
                                    subject={topic.subject}
                                    chapter={topic.chapter}
                                    topic={topic.topic}
                                    percentage={topic.percentage}
                                    variant="strong"
                                />
                            ))}
                        </div>
                    )}
                </Card>

                <Card>
                    <CardHeader
                        title="Topics to work on"
                        subtitle={`${dashboard.weakTopics.length} topic(s), worst first`}
                        icon={<TriangleAlert className="size-4" aria-hidden />}
                        action={
                            dashboard.weakTopics.length > 0 ? (
                                <Link to="/student/resources">
                                    <Button
                                        variant="outline"
                                        size="sm"
                                        leftIcon={<BookOpen className="size-3.5" aria-hidden />}
                                    >
                                        Resources
                                    </Button>
                                </Link>
                            ) : undefined
                        }
                    />
                    {dashboard.weakTopics.length === 0 ? (
                        <EmptyState title="No weak topics flagged in this exam" />
                    ) : (
                        <div className="mt-4 grid gap-2.5">
                            {dashboard.weakTopics.map((topic) => (
                                <TopicCard
                                    key={`${topic.subject}-${topic.topic}`}
                                    subject={topic.subject}
                                    chapter={topic.chapter}
                                    topic={topic.topic}
                                    percentage={topic.percentage}
                                    variant="weak"
                                />
                            ))}
                        </div>
                    )}
                </Card>
            </section>

            <div className="mt-6">
                <Link to="/student/feedback" className="block">
                    <Card className="transition hover:shadow-md">
                        <div className="flex items-center gap-4">
                            <span className="grid size-11 shrink-0 place-items-center rounded-xl bg-violet-50 text-violet-600">
                                <Sparkles className="size-5" aria-hidden />
                            </span>
                            <div className="min-w-0 flex-1">
                                <p className="text-sm font-semibold text-slate-900">
                                    See your AI feedback and study plan
                                </p>
                                <p className="mt-0.5 text-sm text-slate-500">
                                    Strengths, weaknesses and a day-by-day plan built from these numbers.
                                </p>
                            </div>
                            <Button variant="primary" size="sm">
                                Open
                            </Button>
                        </div>
                    </Card>
                </Link>
            </div>
        </>
    );
}

function SubjectRow({
    subject,
    variant,
}: {
    subject: SubjectPerformance;
    variant: 'strong' | 'weak';
}) {
    const isStrong = variant === 'strong';
    return (
        <li
            className={cn(
                'flex items-center gap-3 rounded-lg p-3 ring-1',
                isStrong ? 'bg-emerald-50/50 ring-emerald-100' : 'bg-red-50/50 ring-red-100',
            )}
        >
            <span
                className={cn(
                    'grid size-7 shrink-0 place-items-center rounded-full',
                    isStrong ? 'bg-emerald-100 text-emerald-700' : 'bg-red-100 text-red-700',
                )}
                aria-hidden
            >
                {isStrong ? <Check className="size-4" /> : <TriangleAlert className="size-4" />}
            </span>
            <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-slate-900">{subject.subjectName}</p>
                <p className="text-xs text-slate-500">
                    {marks(subject.marksObtained, subject.maxMarks)} · grade {subject.grade}
                </p>
            </div>
            <span
                className={cn(
                    'shrink-0 text-sm font-semibold tabular-nums',
                    isStrong ? 'text-emerald-700' : 'text-red-700',
                )}
            >
                {percent(subject.percentage)}
            </span>
        </li>
    );
}

function ComparisonBar({
    label,
    value,
    barClassName,
}: {
    label: string;
    value: number;
    barClassName: string;
}) {
    return (
        <div className="flex items-center gap-3">
            <span className="w-12 shrink-0 text-xs font-medium text-slate-500">{label}</span>
            <div className="h-2.5 flex-1 overflow-hidden rounded-full bg-slate-100">
                <div
                    className={cn('h-full rounded-full', barClassName)}
                    style={{ width: `${Math.max(0, Math.min(100, value))}%` }}
                />
            </div>
            <span className="w-14 shrink-0 text-right text-xs font-semibold tabular-nums text-slate-700">
                {percent(value)}
            </span>
        </div>
    );
}

function firstName(fullName: string): string {
    return fullName.split(/\s+/)[0] ?? fullName;
}

function trendSubtitle(direction?: string, change?: number): string {
    if (!direction || direction === 'INSUFFICIENT_DATA') return 'Across every exam you have sat';
    const label = direction.charAt(0) + direction.slice(1).toLowerCase();
    if (change === undefined) return label;
    return `${label} · ${signedPercent(change)} since your first exam`;
}
