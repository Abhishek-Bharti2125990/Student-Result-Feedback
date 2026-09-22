import React, { useState } from 'react';
import { Mail, Lock, Eye, EyeOff, ArrowRight, AlertCircle } from 'lucide-react';
import type { AuthUser } from '../types';

interface SignInProps {
    onLoginSuccess: (user: AuthUser) => void;
    onSwitchToSignUp: () => void;
}

export const SignIn: React.FC<SignInProps> = ({ onLoginSuccess, onSwitchToSignUp }) => {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [showPassword, setShowPassword] = useState(false);
    const [loading, setLoading] = useState(false);
    const [errorMessage, setErrorMessage] = useState('');

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setErrorMessage('');
        setLoading(true);

        await new Promise((resolve) => setTimeout(resolve, 500));

        const storedUsersRaw = localStorage.getItem('registered_users');
        const registeredUsers: any[] = storedUsersRaw ? JSON.parse(storedUsersRaw) : [];

        const matchedUser = registeredUsers.find(
            (u) => u.email.toLowerCase() === email.toLowerCase() && u.password === password
        );

        if (!matchedUser) {
            setErrorMessage('Invalid credentials. Please verify email and password.');
            setLoading(false);
            return;
        }

        const { password: _, ...safeUser } = matchedUser;
        onLoginSuccess(safeUser as AuthUser);
        setLoading(false);
    };

    return (
        <div className="space-y-4">
            {errorMessage && (
                <div className="p-2.5 rounded-md bg-rose-500/10 border border-rose-500/25 text-rose-400 text-xs flex items-center gap-2">
                    <AlertCircle size={15} className="shrink-0" />
                    <span>{errorMessage}</span>
                </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-3.5">
                <div>
                    <label className="block text-xs font-medium text-slate-300 mb-1">
                        Email Address
                    </label>
                    <div className="relative">
                        <Mail size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none" />
                        <input
                            type="email"
                            required
                            placeholder="name@university.edu"
                            value={email}
                            onChange={(e) => {
                                setEmail(e.target.value);
                                setErrorMessage('');
                            }}
                            className="w-full bg-slate-950/80 border border-slate-700/80 rounded-md pl-9 pr-3 py-2 text-xs sm:text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 transition"
                        />
                    </div>
                </div>

                <div>
                    <label className="block text-xs font-medium text-slate-300 mb-1">
                        Password
                    </label>
                    <div className="relative">
                        <Lock size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none" />
                        <input
                            type={showPassword ? 'text' : 'password'}
                            required
                            placeholder="••••••••"
                            value={password}
                            onChange={(e) => {
                                setPassword(e.target.value);
                                setErrorMessage('');
                            }}
                            className="w-full bg-slate-950/80 border border-slate-700/80 rounded-md pl-9 pr-9 py-2 text-xs sm:text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 transition"
                        />
                        <button
                            type="button"
                            onClick={() => setShowPassword(!showPassword)}
                            className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300 cursor-pointer p-1 transition"
                        >
                            {showPassword ? <EyeOff size={15} /> : <Eye size={15} />}
                        </button>
                    </div>
                </div>

                <button
                    type="submit"
                    disabled={loading}
                    className="w-full mt-1 flex items-center justify-center gap-2 bg-indigo-600 hover:bg-indigo-500 text-white font-medium py-2 rounded-md text-xs sm:text-sm shadow-sm cursor-pointer transition disabled:opacity-50 disabled:cursor-not-allowed"
                >
                    {loading ? (
                        <span className="inline-block w-4 h-4 border-2 border-white/20 border-t-white rounded-full animate-spin" />
                    ) : (
                        <>
                            <span>Sign In</span>
                            <ArrowRight size={14} />
                        </>
                    )}
                </button>
            </form>

            <div className="text-center text-xs text-slate-400 pt-1">
                Don't have an account?{' '}
                <button
                    type="button"
                    onClick={onSwitchToSignUp}
                    className="text-indigo-400 font-semibold hover:underline cursor-pointer"
                >
                    Sign up
                </button>
            </div>
        </div>
    );
};

export default SignIn;