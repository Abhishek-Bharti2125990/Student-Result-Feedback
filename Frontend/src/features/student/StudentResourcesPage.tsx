import { useEffect, useMemo, type ReactNode } from 'react';
import { BookOpen, Library, PlayCircle, TriangleAlert } from 'lucide-react';
import { PageHeader } from '@/components/layout/AppLayout';
import { ResourceCard } from '@/components/domain/ResourceCard';
import { Badge } from '@/components/ui/Badge';
import { Card, CardHeader } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { CardSkeleton } from '@/components/ui/Skeleton';
import { percent, plural } from '@/lib/format';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import { fetchStudentDashboard, fetchStudentResources } from '@/store/slices/studentSlice';
import type { ResourceSuggestion } from '@/types/common';

interface TopicGroup {
    topic: string;
    subject: string;
    books: ResourceSuggestion[];
    videos: ResourceSuggestion[];
    /** The student's own mark on this topic, when the dashboard knows it. */
    percentage?: number;
}

export function StudentResourcesPage() {
    const dispatch = useAppDispatch();
    const { dashboard, resources, resourcesLoading, resourcesError } = useAppSelector(
        (state) => state.student,
    );

    useEffect(() => {
        void dispatch(fetchStudentResources());
        if (!dashboard) {
            void dispatch(fetchStudentDashboard());
        }
    }, [dispatch, dashboard]);

    /**
     * Grouped by topic, in the order the API returned them - worst topic first.
     * Re-sorting alphabetically would throw away the one thing that ordering
     * carries: which topic cost the most marks.
     */
    const groups = useMemo<TopicGroup[]>(() => {
        const percentageByTopic = new Map(
            (dashboard?.weakTopics ?? []).map((topic) => [topic.topic.toLowerCase(), topic.percentage]),
        );

        const byTopic = new Map<string, TopicGroup>();
        for (const resource of resources) {
            const key = `${resource.subject}::${resource.topic}`.toLowerCase();
            let group = byTopic.get(key);
            if (!group) {
                group = {
                    topic: resource.topic,
                    subject: resource.subject,
                    books: [],
                    videos: [],
                    percentage: percentageByTopic.get(resource.topic.toLowerCase()),
                };
                byTopic.set(key, group);
            }
            if (resource.resourceType === 'VIDEO') {
                group.videos.push(resource);
            } else {
                group.books.push(resource);
            }
        }
        return [...byTopic.values()];
    }, [resources, dashboard]);

    if (resourcesLoading && resources.length === 0) {
        return (
            <>
                <PageHeader title="Learning resources" description="Finding material for your weak topics…" />
                <div className="grid gap-6">
                    <CardSkeleton lines={4} />
                    <CardSkeleton lines={4} />
                </div>
            </>
        );
    }

    if (resourcesError && resources.length === 0) {
        return (
            <>
                <PageHeader title="Learning resources" />
                <ErrorState
                    message={resourcesError}
                    onRetry={() => void dispatch(fetchStudentResources())}
                    retrying={resourcesLoading}
                />
            </>
        );
    }

    const totalBooks = groups.reduce((sum, group) => sum + group.books.length, 0);
    const totalVideos = groups.reduce((sum, group) => sum + group.videos.length, 0);

    return (
        <>
            <PageHeader
                title="Learning resources"
                description="Books and videos your school has vetted, for the topics costing you marks"
            />

            {groups.length === 0 ? (
                <Card>
                    <EmptyState
                        title="No resources to show"
                        description="Resources appear here once a topic is flagged as weak. Nothing is flagged right now — which is good news."
                        icon={<Library className="size-6" aria-hidden />}
                    />
                </Card>
            ) : (
                <>
                    <div className="mb-5 flex flex-wrap items-center gap-2">
                        <Badge tone="slate">{plural(groups.length, 'topic')}</Badge>
                        <Badge tone="blue">{plural(totalBooks, 'book')}</Badge>
                        <Badge tone="red">{plural(totalVideos, 'video')}</Badge>
                    </div>

                    <div className="space-y-6">
                        {groups.map((group) => (
                            <Card key={`${group.subject}-${group.topic}`}>
                                <CardHeader
                                    title={group.topic}
                                    subtitle={group.subject}
                                    icon={<TriangleAlert className="size-4" aria-hidden />}
                                    action={
                                        group.percentage !== undefined ? (
                                            <Badge tone="red">You scored {percent(group.percentage)}</Badge>
                                        ) : undefined
                                    }
                                />

                                <div className="mt-4 grid gap-5 md:grid-cols-2">
                                    <ResourceColumn
                                        label="Books"
                                        icon={<BookOpen className="size-3.5" aria-hidden />}
                                        resources={group.books}
                                        emptyText="No book listed for this topic."
                                    />
                                    <ResourceColumn
                                        label="Videos"
                                        icon={<PlayCircle className="size-3.5" aria-hidden />}
                                        resources={group.videos}
                                        emptyText="No video listed for this topic."
                                    />
                                </div>
                            </Card>
                        ))}
                    </div>
                </>
            )}
        </>
    );
}

function ResourceColumn({
    label,
    icon,
    resources,
    emptyText,
}: {
    label: string;
    icon: ReactNode;
    resources: ResourceSuggestion[];
    emptyText: string;
}) {
    return (
        <div>
            <p className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-slate-500">
                {icon}
                {label}
            </p>
            {resources.length === 0 ? (
                <p className="mt-2 text-sm text-slate-400">{emptyText}</p>
            ) : (
                <div className="mt-2.5 space-y-2.5">
                    {resources.map((resource) => (
                        <ResourceCard key={`${resource.title}-${resource.resourceType}`} resource={resource} />
                    ))}
                </div>
            )}
        </div>
    );
}
