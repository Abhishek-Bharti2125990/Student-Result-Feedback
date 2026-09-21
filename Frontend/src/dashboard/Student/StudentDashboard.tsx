import React, { useState } from 'react';
import {
    User,
    GraduationCap,
    Award,
    Download,
    Sparkles,
    Send,
    ChevronDown,
    ChevronUp,
    BarChart3,
    MessageSquare,
    CheckCircle2,
    AlertCircle,
    BookOpen,
    Mail,
    FileCheck,
    TrendingUp
} from 'lucide-react';

interface SubjectMark {
    code: string;
    name: string;
    grade: string;
    marks: number;
    maxMarks: number;
    gradePoint: number;
}

interface SemesterResult {
    semNumber: number;
    sgpa: number;
    credits: number;
    status: string;
    subjects: SubjectMark[];
    aiFeedback: {
        summary: string;
        strengths: string[];
        scopeOfImprovement: string[];
        aiTip: string;
    };
}

export const StudentDashboard: React.FC = () => {
    // Student Profile Data
    const student = {
        name: 'Rahul Sharma',
        rollNo: '21CS042',
        branch: 'Computer Science & Engineering',
        semester: '5th Semester',
        enrollmentId: 'EN202100849',
        cgpa: 8.48,
        email: 'rahul.cs21@univ.edu',
        college: 'Institute of Engineering & Technology',
        batch: '2021 - 2025',
        totalCredits: 86
    };

    // Semesters Data
    const [results] = useState<SemesterResult[]>([
        {
            semNumber: 4,
            sgpa: 8.65,
            credits: 22,
            status: 'First Class with Distinction',
            subjects: [
                { code: 'CS401', name: 'Database Management Systems', grade: 'A+', marks: 89, maxMarks: 100, gradePoint: 10 },
                { code: 'CS402', name: 'Computer Networks', grade: 'B+', marks: 74, maxMarks: 100, gradePoint: 7 },
                { code: 'CS403', name: 'Operating Systems', grade: 'A', marks: 84, maxMarks: 100, gradePoint: 9 },
                { code: 'CS404', name: 'Theory of Computation', grade: 'A', marks: 81, maxMarks: 100, gradePoint: 9 },
            ],
            aiFeedback: {
                summary: 'Exceptional performance in core database design and systems architecture. Slight lag observed in computer networks socket programming.',
                strengths: ['SQL Schema Normalization & Indexing', 'Multi-threaded Concurrency in OS'],
                scopeOfImprovement: ['BGP/OSPF Routing Algorithms', 'Subnet Mask Calculations'],
                aiTip: 'Build a small socket-based multi-client chat app in Java or C++ to strengthen practical networking concepts.'
            }
        },
        {
            semNumber: 3,
            sgpa: 8.20,
            credits: 24,
            status: 'First Class',
            subjects: [
                { code: 'CS301', name: 'Data Structures & Algorithms', grade: 'A+', marks: 91, maxMarks: 100, gradePoint: 10 },
                { code: 'CS302', name: 'Discrete Mathematics', grade: 'B', marks: 68, maxMarks: 100, gradePoint: 6 },
                { code: 'CS303', name: 'Digital Logic Design', grade: 'A', marks: 82, maxMarks: 100, gradePoint: 8 },
                { code: 'CS304', name: 'Object Oriented Programming', grade: 'A+', marks: 88, maxMarks: 100, gradePoint: 10 },
            ],
            aiFeedback: {
                summary: 'Solid mastery over abstract data structures. Discrete Math proof questions lowered overall SGPA.',
                strengths: ['Binary Tree & Graph Traversals', 'Polymorphism & OOP Architecture'],
                scopeOfImprovement: ['Recurrence Relations', 'Combinatorial Proof Techniques'],
                aiTip: 'Practice divide-and-conquer recurrence trees on LeetCode / GeeksForGeeks regularly.'
            }
        },
        {
            semNumber: 2,
            sgpa: 8.50,
            credits: 20,
            status: 'First Class with Distinction',
            subjects: [
                { code: 'ES201', name: 'Basic Electrical Sciences', grade: 'A', marks: 85, maxMarks: 100, gradePoint: 9 },
                { code: 'MA201', name: 'Engineering Mathematics II', grade: 'A+', marks: 90, maxMarks: 100, gradePoint: 10 },
                { code: 'CS201', name: 'C Programming & Logic', grade: 'A+', marks: 94, maxMarks: 100, gradePoint: 10 },
            ],
            aiFeedback: {
                summary: 'Well-rounded engineering foundation. High mathematical aptitude and strong syntax execution.',
                strengths: ['Pointer Arithmetic & Dynamic Allocation', 'Multivariate Differential Calculus'],
                scopeOfImprovement: ['Transient Analysis in AC Circuits'],
                aiTip: 'Transition your strong C syntax base into modern C++ Standard Template Library (STL).'
            }
        },
        {
            semNumber: 1,
            sgpa: 8.57,
            credits: 20,
            status: 'First Class with Distinction',
            subjects: [
                { code: 'MA101', name: 'Engineering Mathematics I', grade: 'A+', marks: 88, maxMarks: 100, gradePoint: 10 },
                { code: 'PH101', name: 'Engineering Physics', grade: 'A', marks: 83, maxMarks: 100, gradePoint: 9 },
                { code: 'ME101', name: 'Engineering Mechanics', grade: 'A', marks: 86, maxMarks: 100, gradePoint: 9 },
            ],
            aiFeedback: {
                summary: 'Consistent and strong academic kickoff across both physical science and analytical modules.',
                strengths: ['Matrix Eigenvalues & Eigenvectors', 'Statics and Force Equilibrium'],
                scopeOfImprovement: ['Quantum Mechanics Wave Equations'],
                aiTip: 'Maintain this level of disciplined study distribution across all core theory subjects.'
            }
        }
    ]);

    const [expandedSem, setExpandedSem] = useState<number | null>(4);
    const [facultyQuery, setFacultyQuery] = useState({
        subject: 'CS401 - Database Management Systems (Dr. Sharma)',
        inquiryType: 'Concept Clarification / Doubt',
        question: '',
    });
    const [querySuccess, setQuerySuccess] = useState(false);

    // Graph Data (Chronological order Sem 1 to 4)
    const chartData = results
        .slice()
        .reverse()
        .map((r) => {
            const avgMarks = Math.round(
                r.subjects.reduce((sum, s) => sum + s.marks, 0) / r.subjects.length
            );
            return {
                semLabel: `Sem 0${r.semNumber}`,
                semNumber: r.semNumber,
                avgMarks: avgMarks,
                sgpa: r.sgpa,
            };
        });

    const toggleSemester = (sem: number) => {
        setExpandedSem(expandedSem === sem ? null : sem);
    };

    const handlePrint = () => {
        window.print();
    };

    const handleFacultySubmit = (e: React.FormEvent) => {
        e.preventDefault();
        if (!facultyQuery.question.trim()) return;
        setQuerySuccess(true);
        setFacultyQuery({ ...facultyQuery, question: '' });
        setTimeout(() => setQuerySuccess(false), 4500);
    };

    return (
        <div className="min-h-screen bg-slate-950 text-slate-200 p-4 sm:p-6 lg:p-10 font-sans antialiased selection:bg-indigo-500 selection:text-white">
            <div className="max-w-6xl mx-auto space-y-6">

                {/* ================= 1. BASIC DETAILS HERO CARD ================= */}
                <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 sm:p-8 shadow-xl shadow-black/20">
                    <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6">

                        {/* Student Info */}
                        <div className="flex items-start sm:items-center gap-5">
                            <div className="w-16 h-16 rounded-2xl bg-indigo-950/60 border border-indigo-500/30 flex items-center justify-center font-bold text-indigo-400 text-xl shadow-inner shrink-0">
                                RS
                            </div>
                            <div className="space-y-1">
                                <div className="flex flex-wrap items-center gap-2.5">
                                    <h1 className="text-xl sm:text-2xl font-bold text-white tracking-tight">
                                        {student.name}
                                    </h1>
                                    <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-950/60 text-emerald-400 border border-emerald-500/30">
                                        Active Student
                                    </span>
                                    <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-indigo-950/60 text-indigo-300 border border-indigo-500/30">
                                        {student.semester}
                                    </span>
                                </div>
                                <p className="text-sm text-slate-400">
                                    {student.branch} • {student.college}
                                </p>

                                <div className="flex flex-wrap items-center gap-y-1.5 gap-x-5 pt-2 text-xs text-slate-400">
                                    <span className="flex items-center gap-1.5">
                                        <GraduationCap size={15} className="text-slate-500" />
                                        Roll: <strong className="font-mono text-slate-200">{student.rollNo}</strong>
                                    </span>
                                    <span className="flex items-center gap-1.5">
                                        <User size={15} className="text-slate-500" />
                                        Enrol: <strong className="font-mono text-slate-200">{student.enrollmentId}</strong>
                                    </span>
                                    <span className="flex items-center gap-1.5">
                                        <Mail size={15} className="text-slate-500" />
                                        <span className="text-slate-300">{student.email}</span>
                                    </span>
                                </div>
                            </div>
                        </div>

                        {/* Quick KPI Stat Boxes */}
                        <div className="flex items-center gap-3 border-t lg:border-t-0 lg:border-l border-slate-800 pt-4 lg:pt-0 lg:pl-8">
                            <div className="bg-slate-800/60 border border-slate-700/60 px-5 py-3.5 rounded-xl text-center min-w-[120px]">
                                <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider block">
                                    Cumulative GPA
                                </span>
                                <div className="flex items-center justify-center gap-1.5 mt-1">
                                    <Award size={18} className="text-indigo-400" />
                                    <span className="text-2xl font-black text-white">{student.cgpa}</span>
                                    <span className="text-xs text-slate-500">/ 10</span>
                                </div>
                            </div>

                            <div className="bg-slate-800/60 border border-slate-700/60 px-5 py-3.5 rounded-xl text-center min-w-[120px]">
                                <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider block">
                                    Earned Credits
                                </span>
                                <div className="flex items-center justify-center gap-1.5 mt-1">
                                    <FileCheck size={18} className="text-emerald-400" />
                                    <span className="text-2xl font-black text-white">{student.totalCredits}</span>
                                </div>
                            </div>
                        </div>

                    </div>
                </div>

                {/* ================= 2. REAL DUAL-AXIS VISUAL GRAPH ================= */}
                <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 sm:p-8 shadow-xl shadow-black/20 space-y-6">
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b border-slate-800">
                        <div>
                            <div className="flex items-center gap-2">
                                <BarChart3 size={20} className="text-indigo-400" />
                                <h2 className="text-lg font-bold text-white">
                                    Performance Progression Chart
                                </h2>
                            </div>
                            <p className="text-xs text-slate-400 mt-0.5">
                                Semester-by-semester aggregated marks percentage (Bars) & SGPA curve (Line)
                            </p>
                        </div>

                        {/* Legend */}
                        <div className="flex items-center gap-5 text-xs font-semibold">
                            <div className="flex items-center gap-2">
                                <span className="w-3.5 h-3.5 rounded-sm bg-indigo-500 inline-block shadow-xs" />
                                <span className="text-slate-300">Average Marks (%)</span>
                            </div>
                            <div className="flex items-center gap-2">
                                <span className="w-3.5 h-1 bg-emerald-400 rounded-full inline-block" />
                                <span className="w-2.5 h-2.5 rounded-full border-2 border-emerald-400 bg-slate-900 inline-block -ml-3" />
                                <span className="text-slate-300">SGPA (out of 10)</span>
                            </div>
                        </div>
                    </div>

                    {/* SVG Canvas Dual Chart */}
                    <div className="w-full bg-slate-950/70 border border-slate-800/80 rounded-xl p-4 sm:p-6">
                        <div className="relative w-full h-64">

                            {/* Horizontal Gridlines & Y-Axis Scale (0 to 100%) */}
                            <div className="absolute inset-0 flex flex-col justify-between pointer-events-none text-[10px] text-slate-500 font-mono">
                                {[100, 75, 50, 25, 0].map((val) => (
                                    <div key={val} className="flex items-center gap-2 w-full">
                                        <span className="w-8 text-right shrink-0">{val}%</span>
                                        <div className="w-full border-b border-dashed border-slate-800" />
                                    </div>
                                ))}
                            </div>

                            {/* Chart Plot Area */}
                            <div className="absolute inset-0 left-10 flex items-end justify-around pb-0 pt-2">
                                {chartData.map((item, index) => {
                                    const barHeightPercent = (item.avgMarks / 100) * 82;
                                    return (
                                        <div key={index} className="flex flex-col items-center h-full justify-end group z-10 w-20">

                                            {/* Floating Tooltip */}
                                            <div className="opacity-0 group-hover:opacity-100 transition-opacity absolute -top-2 bg-slate-800 border border-slate-700 text-slate-100 text-[11px] rounded-lg px-2.5 py-1 pointer-events-none shadow-xl whitespace-nowrap z-30">
                                                {item.semLabel}: <strong className="text-indigo-400">{item.avgMarks}%</strong> | SGPA: <strong className="text-emerald-400">{item.sgpa}</strong>
                                            </div>

                                            {/* Marks Label above Bar */}
                                            <span className="text-[11px] font-bold text-indigo-400 mb-1 font-mono">
                                                {item.avgMarks}%
                                            </span>

                                            {/* Bar Component */}
                                            <div
                                                className="w-12 sm:w-14 bg-gradient-to-t from-indigo-600 to-indigo-400 rounded-t-lg shadow-sm hover:brightness-125 transition-all cursor-pointer"
                                                style={{ height: `${barHeightPercent}%` }}
                                            />

                                            {/* X-Axis Label */}
                                            <div className="text-center mt-2 border-t border-slate-800 w-full pt-1.5">
                                                <span className="text-xs font-semibold text-slate-300 block">{item.semLabel}</span>
                                                <span className="text-[10px] text-emerald-400 font-bold block">{item.sgpa} SGPA</span>
                                            </div>
                                        </div>
                                    );
                                })}

                                {/* SVG Overlay for SGPA Line Chart */}
                                <svg className="absolute inset-0 w-full h-[78%] pointer-events-none overflow-visible z-20">
                                    {(() => {
                                        const getY = (sgpa: number) => {
                                            const min = 6.0;
                                            const max = 10.0;
                                            return 200 - ((sgpa - min) / (max - min)) * 170;
                                        };

                                        const points = [
                                            { x: '12.5%', y: getY(chartData[0].sgpa), val: chartData[0].sgpa },
                                            { x: '37.5%', y: getY(chartData[1].sgpa), val: chartData[1].sgpa },
                                            { x: '62.5%', y: getY(chartData[2].sgpa), val: chartData[2].sgpa },
                                            { x: '87.5%', y: getY(chartData[3].sgpa), val: chartData[3].sgpa },
                                        ];

                                        return (
                                            <>
                                                {/* Individual Line Segments */}
                                                <line x1="12.5%" y1={points[0].y} x2="37.5%" y2={points[1].y} stroke="#34D399" strokeWidth="3" strokeLinecap="round" />
                                                <line x1="37.5%" y1={points[1].y} x2="62.5%" y2={points[2].y} stroke="#34D399" strokeWidth="3" strokeLinecap="round" />
                                                <line x1="62.5%" y1={points[2].y} x2="87.5%" y2={points[3].y} stroke="#34D399" strokeWidth="3" strokeLinecap="round" />

                                                {/* Line Points / Dots */}
                                                {points.map((p, i) => (
                                                    <g key={i}>
                                                        <circle cx={p.x} cy={p.y} r="6" fill="#34D399" stroke="#0F172A" strokeWidth="2.5" />
                                                    </g>
                                                ))}
                                            </>
                                        );
                                    })()}
                                </svg>
                            </div>

                        </div>
                    </div>
                </div>

                {/* ================= 3 & 4. RESULTS + AI FEEDBACK + PDF ================= */}
                <div className="space-y-4">
                    <div>
                        <h2 className="text-lg font-bold text-white flex items-center gap-2">
                            <BookOpen size={20} className="text-indigo-400" />
                            <span>Academic Transcripts & AI Feedback</span>
                        </h2>
                        <p className="text-xs text-slate-400 mt-0.5">
                            Click any semester below to view course marks, subject grades, and AI diagnostic reports
                        </p>
                    </div>

                    <div className="space-y-3">
                        {results.map((result) => {
                            const isOpen = expandedSem === result.semNumber;
                            return (
                                <div
                                    key={result.semNumber}
                                    className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl shadow-black/20 transition-all"
                                >
                                    {/* Semester Bar Header */}
                                    <div
                                        onClick={() => toggleSemester(result.semNumber)}
                                        className="p-5 flex items-center justify-between cursor-pointer hover:bg-slate-800/50 transition select-none"
                                    >
                                        <div className="flex items-center gap-4">
                                            <div className="w-11 h-11 rounded-xl bg-indigo-950/60 border border-indigo-500/30 flex items-center justify-center font-bold text-indigo-400 text-sm">
                                                S{result.semNumber}
                                            </div>
                                            <div>
                                                <div className="flex items-center gap-2.5">
                                                    <h3 className="font-bold text-white text-base">
                                                        Semester 0{result.semNumber} Examination
                                                    </h3>
                                                    <span className="text-[11px] font-semibold px-2 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700">
                                                        {result.credits} Credits
                                                    </span>
                                                </div>
                                                <p className="text-xs text-emerald-400 font-medium mt-0.5">
                                                    {result.status}
                                                </p>
                                            </div>
                                        </div>

                                        <div className="flex items-center gap-5">
                                            <div className="text-right">
                                                <span className="text-[10px] uppercase font-bold text-slate-500 block tracking-wider">SGPA</span>
                                                <span className="text-lg font-black text-white">{result.sgpa}</span>
                                            </div>
                                            <div className="p-1 rounded-lg bg-slate-800 text-slate-400">
                                                {isOpen ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
                                            </div>
                                        </div>
                                    </div>

                                    {/* Expanded Body: Course Marksheet, PDF Button, AI Feedback */}
                                    {isOpen && (
                                        <div className="p-5 sm:p-7 border-t border-slate-800 bg-slate-900/60 space-y-6">

                                            {/* Sub-header with Print PDF */}
                                            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                                                <div>
                                                    <h4 className="text-xs font-bold uppercase tracking-wider text-slate-300">Course Marksheet</h4>
                                                    <p className="text-xs text-slate-400">Grading assessed as per university standard 10-point scale</p>
                                                </div>
                                                <button
                                                    type="button"
                                                    onClick={handlePrint}
                                                    className="inline-flex items-center gap-2 px-4 py-2 bg-slate-800 hover:bg-slate-700/80 text-slate-200 border border-slate-700 rounded-xl text-xs font-semibold shadow-xs transition"
                                                >
                                                    <Download size={14} className="text-indigo-400" />
                                                    <span>Download Marksheet (PDF)</span>
                                                </button>
                                            </div>

                                            {/* Course Mark Table */}
                                            <div className="bg-slate-950 border border-slate-800 rounded-xl overflow-x-auto shadow-inner">
                                                <table className="w-full text-left text-xs sm:text-sm">
                                                    <thead className="bg-slate-900/80 border-b border-slate-800 text-slate-400 font-semibold">
                                                        <tr>
                                                            <th className="py-3 px-4">Subject Code</th>
                                                            <th className="py-3 px-4">Subject Name</th>
                                                            <th className="py-3 px-4 text-center">Marks Obtained</th>
                                                            <th className="py-3 px-4 text-center">Grade Point</th>
                                                            <th className="py-3 px-4 text-right">Grade Awarded</th>
                                                        </tr>
                                                    </thead>
                                                    <tbody className="divide-y divide-slate-800/80 text-slate-300">
                                                        {result.subjects.map((sub) => (
                                                            <tr key={sub.code} className="hover:bg-slate-900/40 transition">
                                                                <td className="py-3.5 px-4 font-mono font-bold text-indigo-400 text-xs">{sub.code}</td>
                                                                <td className="py-3.5 px-4 font-semibold text-slate-200">{sub.name}</td>
                                                                <td className="py-3.5 px-4 text-center font-mono text-slate-300">
                                                                    {sub.marks} <span className="text-slate-500 text-xs">/ {sub.maxMarks}</span>
                                                                </td>
                                                                <td className="py-3.5 px-4 text-center font-semibold text-slate-200">{sub.gradePoint}.0</td>
                                                                <td className="py-3.5 px-4 text-right">
                                                                    <span className="inline-block px-2.5 py-1 rounded text-xs font-bold bg-slate-800 text-indigo-300 border border-slate-700">
                                                                        {sub.grade}
                                                                    </span>
                                                                </td>
                                                            </tr>
                                                        ))}
                                                    </tbody>
                                                </table>
                                            </div>

                                            {/* AI Evaluation Insight Card */}
                                            <div className="bg-slate-950/70 border border-indigo-900/40 rounded-2xl p-5 sm:p-6 space-y-4 shadow-sm">
                                                <div className="flex items-center justify-between pb-3 border-b border-slate-800">
                                                    <div className="flex items-center gap-2 text-indigo-400 font-bold text-sm">
                                                        <Sparkles size={17} />
                                                        <span>AI Performance Feedback & Diagnostic Report</span>
                                                    </div>
                                                    <span className="text-[11px] font-semibold text-indigo-300 bg-indigo-950/60 border border-indigo-500/30 px-2.5 py-0.5 rounded-full">
                                                        Sem 0{result.semNumber} Analysis
                                                    </span>
                                                </div>

                                                <p className="text-xs sm:text-sm text-slate-300 leading-relaxed">
                                                    {result.aiFeedback.summary}
                                                </p>

                                                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-1">
                                                    <div className="bg-emerald-950/30 border border-emerald-500/30 p-4 rounded-xl space-y-2">
                                                        <span className="text-xs font-bold text-emerald-400 flex items-center gap-1.5">
                                                            <CheckCircle2 size={15} /> Key Academic Strengths
                                                        </span>
                                                        <ul className="text-xs text-slate-300 space-y-1 pl-4 list-disc marker:text-emerald-400">
                                                            {result.aiFeedback.strengths.map((str, idx) => (
                                                                <li key={idx}>{str}</li>
                                                            ))}
                                                        </ul>
                                                    </div>

                                                    <div className="bg-amber-950/30 border border-amber-500/30 p-4 rounded-xl space-y-2">
                                                        <span className="text-xs font-bold text-amber-400 flex items-center gap-1.5">
                                                            <AlertCircle size={15} /> Scope for Improvement
                                                        </span>
                                                        <ul className="text-xs text-slate-300 space-y-1 pl-4 list-disc marker:text-amber-400">
                                                            {result.aiFeedback.scopeOfImprovement.map((imp, idx) => (
                                                                <li key={idx}>{imp}</li>
                                                            ))}
                                                        </ul>
                                                    </div>
                                                </div>

                                                <div className="bg-indigo-950/30 border border-indigo-500/30 rounded-xl p-3.5 text-xs text-slate-300 flex items-start gap-2.5">
                                                    <TrendingUp size={16} className="text-indigo-400 shrink-0 mt-0.5" />
                                                    <div>
                                                        <span className="font-bold text-white">AI Actionable Recommendation: </span>
                                                        {result.aiFeedback.aiTip}
                                                    </div>
                                                </div>
                                            </div>

                                        </div>
                                    )}
                                </div>
                            );
                        })}
                    </div>
                </div>

                {/* ================= 5. ASK FACULTY FIELD ================= */}
                <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 sm:p-8 shadow-xl shadow-black/20 space-y-4">
                    <div className="flex items-center gap-2.5 pb-3 border-b border-slate-800">
                        <MessageSquare size={20} className="text-indigo-400" />
                        <div>
                            <h2 className="text-base font-bold text-white">Ask Faculty / Academic Helpdesk</h2>
                            <p className="text-xs text-slate-400">
                                Submit specific doubts, project questions, or grade clarification requests directly to professors
                            </p>
                        </div>
                    </div>

                    {querySuccess && (
                        <div className="p-4 rounded-xl bg-emerald-950/50 border border-emerald-500/40 text-emerald-300 text-xs flex items-center gap-2">
                            <CheckCircle2 size={16} className="shrink-0 text-emerald-400" />
                            <span>Your question has been dispatched to the faculty member. You will receive an email update once answered.</span>
                        </div>
                    )}

                    <form onSubmit={handleFacultySubmit} className="space-y-4">
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                            <div>
                                <label className="block text-xs font-semibold text-slate-300 mb-1.5">
                                    Select Course & Faculty
                                </label>
                                <select
                                    value={facultyQuery.subject}
                                    onChange={(e) => setFacultyQuery({ ...facultyQuery, subject: e.target.value })}
                                    className="w-full bg-slate-950 border border-slate-700 rounded-xl px-3.5 py-2.5 text-xs sm:text-sm text-slate-200 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500"
                                >
                                    <option className="bg-slate-900">CS401 - Database Management Systems (Dr. Sharma)</option>
                                    <option className="bg-slate-900">CS402 - Computer Networks (Prof. Anjali Roy)</option>
                                    <option className="bg-slate-900">CS403 - Operating Systems (Dr. Vikram Singh)</option>
                                    <option className="bg-slate-900">CS404 - Theory of Computation (Prof. Rajesh Kumar)</option>
                                </select>
                            </div>

                            <div>
                                <label className="block text-xs font-semibold text-slate-300 mb-1.5">
                                    Inquiry Nature
                                </label>
                                <select
                                    value={facultyQuery.inquiryType}
                                    onChange={(e) => setFacultyQuery({ ...facultyQuery, inquiryType: e.target.value })}
                                    className="w-full bg-slate-950 border border-slate-700 rounded-xl px-3.5 py-2.5 text-xs sm:text-sm text-slate-200 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500"
                                >
                                    <option className="bg-slate-900">Concept Clarification / Doubt</option>
                                    <option className="bg-slate-900">Exam Mark Re-evaluation Query</option>
                                    <option className="bg-slate-900">Lab Assignment Guidance</option>
                                    <option className="bg-slate-900">General Academic Mentorship</option>
                                </select>
                            </div>
                        </div>

                        <div>
                            <label className="block text-xs font-semibold text-slate-300 mb-1.5">
                                Describe your Doubt / Concern
                            </label>
                            <textarea
                                rows={3}
                                required
                                value={facultyQuery.question}
                                onChange={(e) => setFacultyQuery({ ...facultyQuery, question: e.target.value })}
                                placeholder="Write your question clearly with topic and lecture references..."
                                className="w-full bg-slate-950 border border-slate-700 rounded-xl p-3.5 text-xs sm:text-sm text-slate-200 placeholder-slate-500 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 resize-none"
                            />
                        </div>

                        <div className="flex justify-end">
                            <button
                                type="submit"
                                className="inline-flex items-center gap-2 bg-indigo-600 hover:bg-indigo-500 active:bg-indigo-700 text-white text-xs sm:text-sm font-semibold px-5 py-2.5 rounded-xl shadow-lg shadow-indigo-600/20 transition"
                            >
                                <Send size={15} /> Submit Question
                            </button>
                        </div>
                    </form>
                </div>

            </div>
        </div>
    );
};

export default StudentDashboard;