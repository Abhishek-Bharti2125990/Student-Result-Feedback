import { useEffect, useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
    AlertCircle,
    Eye,
    EyeOff,
    GraduationCap,
    LogIn,
    ShieldCheck,
    Sparkles,
    Upload,
    Users,
} from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { LANDING_BY_ROLE } from '@/components/layout/navigation';
import { cn } from '@/lib/cn';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import { clearAuthError, loginThunk } from '@/store/slices/authSlice';
import type { Role } from '@/types/common';

const schema = z.object({
    username: z.string().trim().min(1, 'Enter your username or e-mail'),
    password: z.string().min(1, 'Enter your password'),
});

type FormValues = z.infer<typeof schema>;

interface RoleCard {
    role: Role;
    title: string;
    blurb: string;
    icon: typeof Users;
    accent: string;
}

/**
 * The three cards are a signpost, not a switch.
 *
 * Every role signs in through the same form, and the backend's token decides
 * where the user lands. Letting the client pick a role would be theatre: the
 * API would reject the mismatch anyway, and the login screen would have told
 * the user something untrue about what it controls.
 */
const ROLE_CARDS: RoleCard[] = [
    {
        role: 'STUDENT',
        title: 'Student',
        blurb: 'Your result, weak topics, AI study plan and resources',
        icon: GraduationCap,
        accent: 'from-blue-500 to-blue-600',
    },
    {
        role: 'TEACHER',
        title: 'Teacher',
        blurb: 'Class score bands, topics to re-teach and AI guidance',
        icon: Users,
        accent: 'from-violet-500 to-violet-600',
    },
    {
        role: 'ADMIN',
        title: 'Admin',
        blurb: 'Upload result CSVs and monitor every import',
        icon: Upload,
        accent: 'from-amber-500 to-amber-600',
    },
];

export function LoginPage() {
    const dispatch = useAppDispatch();
    const location = useLocation();
    const { isAuthenticated, user, loading, error, sessionExpired } = useAppSelector(
        (state) => state.auth,
    );

    const [highlighted, setHighlighted] = useState<Role>('STUDENT');
    const [showPassword, setShowPassword] = useState(false);

    const {
        register,
        handleSubmit,
        formState: { errors },
    } = useForm<FormValues>({
        resolver: zodResolver(schema),
        defaultValues: { username: '', password: '' },
    });

    // A stale "Invalid credentials" sitting above a fresh form is confusing.
    useEffect(() => {
        dispatch(clearAuthError());
    }, [dispatch]);

    if (isAuthenticated && user) {
        const from = (location.state as { from?: string } | null)?.from;
        return <Navigate to={from ?? LANDING_BY_ROLE[user.role]} replace />;
    }

    const onSubmit = (values: FormValues) => {
        void dispatch(loginThunk(values));
    };

    return (
        <div className="min-h-screen bg-slate-50 lg:grid lg:grid-cols-2">
            {/* Left: what the platform is. Hidden on mobile, where the form wins. */}
            <section className="relative hidden overflow-hidden bg-slate-900 p-12 lg:flex lg:flex-col lg:justify-between">
                <div
                    className="pointer-events-none absolute -right-24 -top-24 size-96 rounded-full bg-blue-600/20 blur-3xl"
                    aria-hidden
                />
                <div
                    className="pointer-events-none absolute -bottom-32 -left-20 size-96 rounded-full bg-violet-600/20 blur-3xl"
                    aria-hidden
                />

                <div className="relative">
                    <div className="flex items-center gap-2.5">
                        <span className="grid size-10 place-items-center rounded-xl bg-blue-600 text-white">
                            <GraduationCap className="size-6" aria-hidden />
                        </span>
                        <span className="text-lg font-semibold text-white">Result Intelligence</span>
                    </div>

                    <h1 className="mt-14 max-w-md text-4xl font-semibold leading-tight tracking-tight text-white">
                        From a results CSV to advice a student can act on.
                    </h1>
                    <p className="mt-4 max-w-md text-base leading-relaxed text-slate-300">
                        Marks are imported, ranked and broken down to the topic. Claude turns the
                        analytics into a study plan for students and an intervention list for teachers.
                    </p>

                    <ul className="mt-10 space-y-3.5">
                        {[
                            { icon: Sparkles, text: 'AI feedback grounded in the real numbers' },
                            { icon: Users, text: 'Class split into four score bands' },
                            { icon: ShieldCheck, text: 'Topic-level strengths and weaknesses' },
                        ].map((feature) => (
                            <li key={feature.text} className="flex items-center gap-3 text-sm text-slate-300">
                                <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-white/10 text-blue-300">
                                    <feature.icon className="size-4" aria-hidden />
                                </span>
                                {feature.text}
                            </li>
                        ))}
                    </ul>
                </div>

                <p className="relative text-xs text-slate-500">
                    Spring Boot 3 · MySQL · Spring Batch · Claude
                </p>
            </section>

            {/* Right: the form. */}
            <section className="flex items-center justify-center px-4 py-10 sm:px-8">
                <div className="w-full max-w-md">
                    <div className="lg:hidden">
                        <div className="flex items-center gap-2.5">
                            <span className="grid size-10 place-items-center rounded-xl bg-blue-600 text-white">
                                <GraduationCap className="size-6" aria-hidden />
                            </span>
                            <span className="text-lg font-semibold text-slate-900">Result Intelligence</span>
                        </div>
                    </div>

                    <h2 className="mt-8 text-2xl font-semibold tracking-tight text-slate-900 lg:mt-0">
                        Sign in
                    </h2>
                    <p className="mt-1.5 text-sm text-slate-500">
                        One form for every role — your account decides what you see.
                    </p>

                    <div className="mt-6 grid gap-2.5 sm:grid-cols-3">
                        {ROLE_CARDS.map((card) => (
                            <button
                                key={card.role}
                                type="button"
                                onClick={() => setHighlighted(card.role)}
                                aria-pressed={highlighted === card.role}
                                className={cn(
                                    'group rounded-xl bg-white p-3 text-left ring-1 transition',
                                    highlighted === card.role
                                        ? 'ring-2 ring-blue-500 shadow-sm'
                                        : 'ring-slate-200 hover:ring-slate-300',
                                )}
                            >
                                <span
                                    className={cn(
                                        'grid size-8 place-items-center rounded-lg bg-gradient-to-br text-white',
                                        card.accent,
                                    )}
                                >
                                    <card.icon className="size-4" aria-hidden />
                                </span>
                                <span className="mt-2 block text-sm font-semibold text-slate-900">
                                    {card.title}
                                </span>
                            </button>
                        ))}
                    </div>

                    <p className="mt-2.5 text-xs leading-relaxed text-slate-500">
                        {ROLE_CARDS.find((card) => card.role === highlighted)?.blurb}
                    </p>

                    {sessionExpired && (
                        <div className="mt-5 flex items-start gap-2.5 rounded-lg bg-amber-50 p-3 text-sm text-amber-800 ring-1 ring-amber-200">
                            <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden />
                            <span>Your session expired. Please sign in again.</span>
                        </div>
                    )}

                    {error && (
                        <div
                            className="mt-5 flex items-start gap-2.5 rounded-lg bg-red-50 p-3 text-sm text-red-700 ring-1 ring-red-200"
                            role="alert"
                        >
                            <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden />
                            <span>{error}</span>
                        </div>
                    )}

                    <form onSubmit={handleSubmit(onSubmit)} className="mt-5 space-y-4" noValidate>
                        <div>
                            <label htmlFor="username" className="block text-sm font-medium text-slate-700">
                                Username or e-mail
                            </label>
                            <input
                                id="username"
                                type="text"
                                autoComplete="username"
                                autoFocus
                                {...register('username')}
                                aria-invalid={Boolean(errors.username)}
                                className={cn(
                                    'mt-1.5 block w-full rounded-lg border-0 px-3 py-2.5 text-sm text-slate-900',
                                    'ring-1 ring-inset placeholder:text-slate-400',
                                    'focus:ring-2 focus:ring-inset focus:ring-blue-600',
                                    errors.username ? 'ring-red-400' : 'ring-slate-300',
                                )}
                                placeholder="student1"
                            />
                            {errors.username && (
                                <p className="mt-1.5 text-xs text-red-600">{errors.username.message}</p>
                            )}
                        </div>

                        <div>
                            <label htmlFor="password" className="block text-sm font-medium text-slate-700">
                                Password
                            </label>
                            <div className="relative mt-1.5">
                                <input
                                    id="password"
                                    type={showPassword ? 'text' : 'password'}
                                    autoComplete="current-password"
                                    {...register('password')}
                                    aria-invalid={Boolean(errors.password)}
                                    className={cn(
                                        'block w-full rounded-lg border-0 px-3 py-2.5 pr-10 text-sm text-slate-900',
                                        'ring-1 ring-inset placeholder:text-slate-400',
                                        'focus:ring-2 focus:ring-inset focus:ring-blue-600',
                                        errors.password ? 'ring-red-400' : 'ring-slate-300',
                                    )}
                                    placeholder="••••••••"
                                />
                                <button
                                    type="button"
                                    onClick={() => setShowPassword((shown) => !shown)}
                                    className="absolute inset-y-0 right-0 grid w-10 place-items-center text-slate-400 hover:text-slate-600"
                                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                                >
                                    {showPassword ? (
                                        <EyeOff className="size-4" aria-hidden />
                                    ) : (
                                        <Eye className="size-4" aria-hidden />
                                    )}
                                </button>
                            </div>
                            {errors.password && (
                                <p className="mt-1.5 text-xs text-red-600">{errors.password.message}</p>
                            )}
                        </div>

                        <Button
                            type="submit"
                            size="lg"
                            fullWidth
                            loading={loading}
                            leftIcon={<LogIn className="size-4" aria-hidden />}
                        >
                            {loading ? 'Signing in…' : 'Sign in'}
                        </Button>
                    </form>

                    <div className="mt-6 rounded-lg bg-slate-100 p-3.5">
                        <p className="text-xs font-semibold text-slate-700">Demo logins</p>
                        <p className="mt-1 text-xs leading-relaxed text-slate-500">
                            <code className="font-mono">admin</code>,{' '}
                            <code className="font-mono">teacher1</code> or{' '}
                            <code className="font-mono">student1</code> — password{' '}
                            <code className="font-mono">Passw0rd!</code>
                        </p>
                    </div>
                </div>
            </section>
        </div>
    );
}
