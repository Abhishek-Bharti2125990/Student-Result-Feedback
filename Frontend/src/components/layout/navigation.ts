import {
    BookOpen,
    History,
    LayoutDashboard,
    Sparkles,
    Upload,
    UserCog,
    Users,
    type LucideIcon,
} from 'lucide-react';
import type { Role } from '@/types/common';

export interface NavItem {
    label: string;
    to: string;
    icon: LucideIcon;
    description: string;
}

/**
 * The sidebar, per role.
 *
 * Only routes the backend can actually serve appear here. There is no resource
 * management and no standalone student or teacher roster editor, because those
 * endpoints do not exist - a nav item leading to a page that cannot load is
 * worse than a shorter menu. Accounts are managed under Users, which creates the
 * student and teacher records as a side effect of creating their logins.
 */
export const NAV_BY_ROLE: Record<Role, NavItem[]> = {
    STUDENT: [
        {
            label: 'Dashboard',
            to: '/student/dashboard',
            icon: LayoutDashboard,
            description: 'Your result, rank and topic analysis',
        },
        {
            label: 'AI Feedback',
            to: '/student/feedback',
            icon: Sparkles,
            description: 'Strengths, weaknesses and your study plan',
        },
        {
            label: 'Resources',
            to: '/student/resources',
            icon: BookOpen,
            description: 'Books and videos for your weak topics',
        },
    ],
    TEACHER: [
        {
            label: 'Class Dashboard',
            to: '/teacher/dashboard',
            icon: Users,
            description: 'Score categories, weak topics and AI guidance',
        },
    ],
    ADMIN: [
        {
            label: 'Upload Results',
            to: '/admin/upload',
            icon: Upload,
            description: 'Import a results CSV',
        },
        {
            label: 'Upload History',
            to: '/admin/uploads',
            icon: History,
            description: 'Past imports and their error reports',
        },
        {
            label: 'Users',
            to: '/admin/users',
            icon: UserCog,
            description: 'Logins for admins, teachers and students',
        },
    ],
};

/** Where a role lands after signing in. */
export const LANDING_BY_ROLE: Record<Role, string> = {
    STUDENT: '/student/dashboard',
    TEACHER: '/teacher/dashboard',
    ADMIN: '/admin/upload',
};

/** Human labels for breadcrumbs, keyed by path segment. */
export const SEGMENT_LABELS: Record<string, string> = {
    student: 'Student',
    teacher: 'Teacher',
    admin: 'Admin',
    dashboard: 'Dashboard',
    feedback: 'AI Feedback',
    resources: 'Resources',
    upload: 'Upload Results',
    uploads: 'Upload History',
    users: 'Users',
};
