import React, { useState } from 'react';
import { GraduationCap, BookOpen } from 'lucide-react';
import type { Role, AuthUser } from './types';
import { SignIn } from './signin/SignIn';
import { SignUp } from './signup/SignUp';

export type { Role, AuthUser };

interface AuthPageProps {
    onLoginSuccess: (user: AuthUser) => void;
}

export const AuthPage: React.FC<AuthPageProps> = ({ onLoginSuccess }) => {
    const [mode, setMode] = useState<'LOGIN' | 'SIGNUP'>('LOGIN');
    const [role, setRole] = useState<Role>('STUDENT');

    return (
        <div className="min-h-screen bg-slate-950 flex items-center justify-center p-4 sm:p-6 antialiased">
            {/* Crisp, Structured Card */}
            <div className="w-full max-w-sm bg-slate-900 border border-slate-800 rounded-lg p-6 shadow-xl space-y-5">

                {/* Header Title */}
                <div className="text-center space-y-1">
                    <div className="inline-flex p-2 bg-indigo-500/10 border border-indigo-500/20 rounded-md text-indigo-400 mb-1">
                        {role === 'STUDENT' ? <GraduationCap size={22} /> : <BookOpen size={22} />}
                    </div>
                    <h2 className="text-xl font-bold text-slate-100 tracking-tight">
                        {mode === 'LOGIN' ? 'Portal Authentication' : 'Create Portal Account'}
                    </h2>
                    <p className="text-xs text-slate-400">
                        {mode === 'LOGIN'
                            ? 'Enter verified university credentials'
                            : 'Register to access academic dashboards'}
                    </p>
                </div>

                {/* Tab Toggle */}
                <div className="flex bg-slate-950 p-1 rounded-md border border-slate-800/80">
                    <button
                        type="button"
                        onClick={() => setMode('LOGIN')}
                        className={`flex-1 py-1.5 text-xs font-semibold rounded-sm transition cursor-pointer ${mode === 'LOGIN'
                                ? 'bg-indigo-600 text-white shadow-xs'
                                : 'text-slate-400 hover:text-slate-200'
                            }`}
                    >
                        Sign In
                    </button>
                    <button
                        type="button"
                        onClick={() => setMode('SIGNUP')}
                        className={`flex-1 py-1.5 text-xs font-semibold rounded-sm transition cursor-pointer ${mode === 'SIGNUP'
                                ? 'bg-indigo-600 text-white shadow-xs'
                                : 'text-slate-400 hover:text-slate-200'
                            }`}
                    >
                        Sign Up
                    </button>
                </div>

                {/* Separate Sub-Components */}
                {mode === 'LOGIN' ? (
                    <SignIn
                        onLoginSuccess={onLoginSuccess}
                        onSwitchToSignUp={() => setMode('SIGNUP')}
                    />
                ) : (
                    <SignUp
                        onLoginSuccess={onLoginSuccess}
                        onSwitchToSignIn={() => setMode('LOGIN')}
                        currentRole={role}
                        onRoleChange={setRole}
                    />
                )}

            </div>
        </div>
    );
};

export default AuthPage;