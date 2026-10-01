import { AlertTriangle, Minus, Sparkles, TrendingUp } from 'lucide-react';
import { Badge } from '@/components/ui/Badge';
import { categoryStyle } from '@/lib/category';
import type { ScoreCategory } from '@/types/common';

const ICONS: Record<ScoreCategory, typeof AlertTriangle> = {
    CRITICAL: AlertTriangle,
    AVERAGE: Minus,
    GOOD: TrendingUp,
    EXCELLENT: Sparkles,
};

interface CategoryBadgeProps {
    category: ScoreCategory;
    /** Shows the backend's full heading instead of the short label. */
    full?: boolean;
    className?: string;
}

/**
 * The score band, always with the same colour and the same icon.
 *
 * Colour alone would not be enough: roughly one in twelve men cannot reliably
 * separate the red and the green tile, so each band carries a distinct glyph
 * and its name in text too.
 */
export function CategoryBadge({ category, full = false, className }: CategoryBadgeProps) {
    const style = categoryStyle(category);
    const Icon = ICONS[category];

    return (
        <Badge classes={style.badge} className={className} icon={<Icon className="size-3" aria-hidden />}>
            {full ? style.heading : style.label}
        </Badge>
    );
}
