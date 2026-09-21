import React, { useState } from 'react';
import {
    User,
    BookOpen,
    Building,
    Mail,
    IdCard,
    Plus,
    Search,
    Edit3,
    Trash2,
    MessageSquare,
    Send,
    CheckCircle2,
    Clock,
    X,
    Award,
    Filter,
    Users
} from 'lucide-react';

// Interfaces
interface StudentResultRecord {
    id: string;
    studentName: string;
    rollNo: string;
    semester: number;
    subjectCode: string;
    subjectName: string;
    marks: number;
    maxMarks: number;
    grade: string;
}

interface StudentQuery {
    id: string;
    studentName: string;
    rollNo: string;
    subject: string;
    inquiryType: string;
    question: string;
    createdAt: string;
    status: 'PENDING' | 'RESOLVED';
    response?: string;
}

export const TeacherDashboard: React.FC = () => {
    // 1. Teacher Basic Details
    const teacherProfile = {
        name: 'Dr. Anjali Roy',
        designation: 'Associate Professor & Course Coordinator',
        department: 'Department of Computer Science & Engineering',
        employeeId: 'FAC-CSE-408',
        email: 'anjali.roy@univ.edu',
        cabin: 'Room 304, Academic Block B',
        assignedCourses: ['CS401 - Database Management Systems', 'CS402 - Computer Networks']
    };

    // 2. Student Results State (Full CRUD Ready)
    const [resultsList, setResultsList] = useState<StudentResultRecord[]>([
        {
            id: 'res-1',
            studentName: 'Rahul Sharma',
            rollNo: '21CS042',
            semester: 4,
            subjectCode: 'CS401',
            subjectName: 'Database Management Systems',
            marks: 89,
            maxMarks: 100,
            grade: 'A+'
        },
        {
            id: 'res-2',
            studentName: 'Sneha Patel',
            rollNo: '21CS018',
            semester: 4,
            subjectCode: 'CS401',
            subjectName: 'Database Management Systems',
            marks: 78,
            maxMarks: 100,
            grade: 'A'
        },
        {
            id: 'res-3',
            studentName: 'Amit Verma',
            rollNo: '21CS005',
            semester: 4,
            subjectCode: 'CS402',
            subjectName: 'Computer Networks',
            marks: 64,
            maxMarks: 100,
            grade: 'B'
        },
        {
            id: 'res-4',
            studentName: 'Priya Nair',
            rollNo: '21CS031',
            semester: 4,
            subjectCode: 'CS402',
            subjectName: 'Computer Networks',
            marks: 92,
            maxMarks: 100,
            grade: 'A+'
        }
    ]);

    // 3. Student Queries State
    const [queries, setQueries] = useState<StudentQuery[]>([
        {
            id: 'q-1',
            studentName: 'Rahul Sharma',
            rollNo: '21CS042',
            subject: 'CS401 - Database Management Systems',
            inquiryType: 'Concept Clarification / Doubt',
            question: 'Ma’am, could you explain the difference between B-Tree and B+ Tree indexing when querying range scans?',
            createdAt: 'Today, 11:30 AM',
            status: 'PENDING'
        },
        {
            id: 'q-2',
            studentName: 'Amit Verma',
            rollNo: '21CS005',
            subject: 'CS402 - Computer Networks',
            inquiryType: 'Exam Mark Re-evaluation Query',
            question: 'I had written the 3-way handshake sequence with TCP flag diagram in Question 3(b). Please review my marks once.',
            createdAt: 'Yesterday, 04:15 PM',
            status: 'RESOLVED',
            response: 'Rechecked your answer sheet. Added 2 grace marks for the correct diagram. Updated in records.'
        }
    ]);

    // Filter & Search
    const [searchTerm, setSearchTerm] = useState('');
    const [subjectFilter, setSubjectFilter] = useState('ALL');

    // CRUD Modal State
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [modalMode, setModalMode] = useState<'CREATE' | 'EDIT'>('CREATE');
    const [currentEditId, setCurrentEditId] = useState<string | null>(null);

    const initialFormState = {
        studentName: '',
        rollNo: '',
        semester: 4,
        subjectCode: 'CS401',
        subjectName: 'Database Management Systems',
        marks: 75,
        maxMarks: 100,
        grade: 'A'
    };
    const [formData, setFormData] = useState(initialFormState);

    // Active query being replied to
    const [replyText, setReplyText] = useState<{ [key: string]: string }>({});

    // CRUD Actions
    const handleOpenCreateModal = () => {
        setModalMode('CREATE');
        setCurrentEditId(null);
        setFormData(initialFormState);
        setIsModalOpen(true);
    };

    const handleOpenEditModal = (record: StudentResultRecord) => {
        setModalMode('EDIT');
        setCurrentEditId(record.id);
        setFormData({
            studentName: record.studentName,
            rollNo: record.rollNo,
            semester: record.semester,
            subjectCode: record.subjectCode,
            subjectName: record.subjectName,
            marks: record.marks,
            maxMarks: record.maxMarks,
            grade: record.grade
        });
        setIsModalOpen(true);
    };

    const handleDeleteRecord = (id: string) => {
        if (window.confirm('Are you sure you want to permanently delete this student record?')) {
            setResultsList((prev) => prev.filter((item) => item.id !== id));
        }
    };

    // Auto calculate Grade on marks change
    const calculateGrade = (marks: number) => {
        if (marks >= 85) return 'A+';
        if (marks >= 75) return 'A';
        if (marks >= 65) return 'B+';
        if (marks >= 55) return 'B';
        if (marks >= 45) return 'C';
        return 'F';
    };

    const handleFormSubmit = (e: React.FormEvent) => {
        e.preventDefault();

        const computedGrade = calculateGrade(Number(formData.marks));

        if (modalMode === 'CREATE') {
            const newRecord: StudentResultRecord = {
                id: `res-${Date.now()}`,
                studentName: formData.studentName,
                rollNo: formData.rollNo,
                semester: Number(formData.semester),
                subjectCode: formData.subjectCode,
                subjectName: formData.subjectCode === 'CS401' ? 'Database Management Systems' : 'Computer Networks',
                marks: Number(formData.marks),
                maxMarks: Number(formData.maxMarks),
                grade: computedGrade
            };
            setResultsList((prev) => [newRecord, ...prev]);
        } else if (modalMode === 'EDIT' && currentEditId) {
            setResultsList((prev) =>
                prev.map((item) =>
                    item.id === currentEditId
                        ? {
                            ...item,
                            studentName: formData.studentName,
                            rollNo: formData.rollNo,
                            semester: Number(formData.semester),
                            subjectCode: formData.subjectCode,
                            subjectName: formData.subjectCode === 'CS401' ? 'Database Management Systems' : 'Computer Networks',
                            marks: Number(formData.marks),
                            maxMarks: Number(formData.maxMarks),
                            grade: computedGrade
                        }
                        : item
                )
            );
        }

        setIsModalOpen(false);
    };

    // Submit reply to a student query
    const handleSendReply = (queryId: string) => {
        const message = replyText[queryId];
        if (!message || !message.trim()) return;

        setQueries((prev) =>
            prev.map((q) =>
                q.id === queryId
                    ? { ...q, status: 'RESOLVED', response: message }
                    : q
            )
        );

        // Clear input
        setReplyText((prev) => ({ ...prev, [queryId]: '' }));
    };

    // Filtered student records
    const filteredRecords = resultsList.filter((r) => {
        const matchesSearch =
            r.studentName.toLowerCase().includes(searchTerm.toLowerCase()) ||
            r.rollNo.toLowerCase().includes(searchTerm.toLowerCase());
        const matchesSubject = subjectFilter === 'ALL' || r.subjectCode === subjectFilter;
        return matchesSearch && matchesSubject;
    });

    return (
        <div className="min-h-screen bg-[#F8FAFC] text-slate-800 p-4 sm:p-6 lg:p-10 font-sans antialiased">
            <div className="max-w-6xl mx-auto space-y-6">

                {/* ================= 1. TEACHER BASIC DETAILS ================= */}
                <div className="bg-white border border-slate-200 rounded-2xl p-6 sm:p-8 shadow-sm">
                    <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6">

                        {/* Profile Info */}
                        <div className="flex items-start sm:items-center gap-5">
                            <div className="w-16 h-16 rounded-2xl bg-indigo-50 border border-indigo-100 flex items-center justify-center font-bold text-indigo-600 text-xl shadow-inner shrink-0">
                                AR
                            </div>
                            <div className="space-y-1">
                                <div className="flex flex-wrap items-center gap-2.5">
                                    <h1 className="text-xl sm:text-2xl font-bold text-slate-900 tracking-tight">
                                        {teacherProfile.name}
                                    </h1>
                                    <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-indigo-50 text-indigo-700 border border-indigo-200">
                                        Faculty Member
                                    </span>
                                </div>
                                <p className="text-sm text-slate-600 font-medium">
                                    {teacherProfile.designation} • {teacherProfile.department}
                                </p>

                                <div className="flex flex-wrap items-center gap-y-1.5 gap-x-5 pt-2 text-xs text-slate-500">
                                    <span className="flex items-center gap-1.5">
                                        <IdCard size={15} className="text-slate-400" />
                                        Emp ID: <strong className="font-mono text-slate-700">{teacherProfile.employeeId}</strong>
                                    </span>
                                    <span className="flex items-center gap-1.5">
                                        <Building size={15} className="text-slate-400" />
                                        <span className="text-slate-700">{teacherProfile.cabin}</span>
                                    </span>
                                    <span className="flex items-center gap-1.5">
                                        <Mail size={15} className="text-slate-400" />
                                        <span className="text-slate-600">{teacherProfile.email}</span>
                                    </span>
                                </div>
                            </div>
                        </div>

                        {/* Quick Stats */}
                        <div className="flex items-center gap-3 border-t lg:border-t-0 lg:border-l border-slate-100 pt-4 lg:pt-0 lg:pl-8">
                            <div className="bg-slate-50 border border-slate-200/80 px-5 py-3.5 rounded-xl text-center min-w-[120px]">
                                <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider block">
                                    Managed Records
                                </span>
                                <div className="flex items-center justify-center gap-1.5 mt-1">
                                    <Award size={18} className="text-indigo-600" />
                                    <span className="text-2xl font-black text-slate-900">{resultsList.length}</span>
                                </div>
                            </div>

                            <div className="bg-slate-50 border border-slate-200/80 px-5 py-3.5 rounded-xl text-center min-w-[120px]">
                                <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider block">
                                    Pending Queries
                                </span>
                                <div className="flex items-center justify-center gap-1.5 mt-1">
                                    <Clock size={18} className="text-amber-500" />
                                    <span className="text-2xl font-black text-slate-900">
                                        {queries.filter(q => q.status === 'PENDING').length}
                                    </span>
                                </div>
                            </div>
                        </div>

                    </div>
                </div>

                {/* ================= 2. STUDENT RESULT CRUD SECTION ================= */}
                <div className="bg-white border border-slate-200 rounded-2xl p-6 sm:p-8 shadow-sm space-y-5">
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-100">
                        <div>
                            <div className="flex items-center gap-2">
                                <Users size={20} className="text-indigo-600" />
                                <h2 className="text-lg font-bold text-slate-900">
                                    Student Results Management
                                </h2>
                            </div>
                            <p className="text-xs text-slate-500 mt-0.5">
                                Add new examination marks, update grading entries, or remove student scores
                            </p>
                        </div>

                        {/* CREATE Action Button */}
                        <button
                            onClick={handleOpenCreateModal}
                            className="inline-flex items-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-semibold shadow-xs transition"
                        >
                            <Plus size={16} />
                            <span>Add Student Result</span>
                        </button>
                    </div>

                    {/* Filters & Search Toolbar */}
                    <div className="flex flex-col sm:flex-row items-center justify-between gap-3">
                        {/* Search Input */}
                        <div className="relative w-full sm:w-72">
                            <Search size={15} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
                            <input
                                type="text"
                                placeholder="Search by student name or roll no..."
                                value={searchTerm}
                                onChange={(e) => setSearchTerm(e.target.value)}
                                className="w-full bg-slate-50 border border-slate-200 rounded-xl pl-10 pr-4 py-2 text-xs sm:text-sm text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-600 focus:bg-white transition"
                            />
                        </div>

                        {/* Subject Selector Filter */}
                        <div className="flex items-center gap-2 w-full sm:w-auto">
                            <Filter size={15} className="text-slate-400 shrink-0" />
                            <select
                                value={subjectFilter}
                                onChange={(e) => setSubjectFilter(e.target.value)}
                                className="bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-xs sm:text-sm text-slate-700 focus:outline-none focus:border-indigo-600 focus:bg-white"
                            >
                                <option value="ALL">All Subjects</option>
                                <option value="CS401">CS401 - DBMS</option>
                                <option value="CS402">CS402 - Computer Networks</option>
                            </select>
                        </div>
                    </div>

                    {/* Results Table (READ, UPDATE, DELETE) */}
                    <div className="border border-slate-200 rounded-xl overflow-x-auto shadow-xs">
                        <table className="w-full text-left text-xs sm:text-sm">
                            <thead className="bg-slate-50 border-b border-slate-200 text-slate-600 font-semibold">
                                <tr>
                                    <th className="py-3 px-4">Roll No</th>
                                    <th className="py-3 px-4">Student Name</th>
                                    <th className="py-3 px-4">Course Code & Name</th>
                                    <th className="py-3 px-4 text-center">Semester</th>
                                    <th className="py-3 px-4 text-center">Marks</th>
                                    <th className="py-3 px-4 text-center">Grade</th>
                                    <th className="py-3 px-4 text-right">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-100 text-slate-700">
                                {filteredRecords.length > 0 ? (
                                    filteredRecords.map((item) => (
                                        <tr key={item.id} className="hover:bg-slate-50/70 transition">
                                            <td className="py-3.5 px-4 font-mono font-bold text-indigo-600 text-xs">
                                                {item.rollNo}
                                            </td>
                                            <td className="py-3.5 px-4 font-semibold text-slate-900">
                                                {item.studentName}
                                            </td>
                                            <td className="py-3.5 px-4">
                                                <span className="font-mono text-xs font-semibold text-slate-700">{item.subjectCode}</span>
                                                <span className="text-slate-500 text-xs block">{item.subjectName}</span>
                                            </td>
                                            <td className="py-3.5 px-4 text-center font-mono text-slate-600">
                                                Sem 0{item.semester}
                                            </td>
                                            <td className="py-3.5 px-4 text-center font-mono text-slate-800">
                                                <strong>{item.marks}</strong> <span className="text-slate-400 text-xs">/ {item.maxMarks}</span>
                                            </td>
                                            <td className="py-3.5 px-4 text-center">
                                                <span className="inline-block px-2 py-0.5 rounded text-xs font-bold bg-slate-100 text-slate-800 border border-slate-200">
                                                    {item.grade}
                                                </span>
                                            </td>
                                            <td className="py-3.5 px-4 text-right space-x-1">
                                                {/* UPDATE button */}
                                                <button
                                                    onClick={() => handleOpenEditModal(item)}
                                                    className="p-1.5 text-slate-500 hover:text-indigo-600 hover:bg-indigo-50 rounded-lg transition"
                                                    title="Edit Marks"
                                                >
                                                    <Edit3 size={15} />
                                                </button>
                                                {/* DELETE button */}
                                                <button
                                                    onClick={() => handleDeleteRecord(item.id)}
                                                    className="p-1.5 text-slate-500 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition"
                                                    title="Delete Record"
                                                >
                                                    <Trash2 size={15} />
                                                </button>
                                            </td>
                                        </tr>
                                    ))
                                ) : (
                                    <tr>
                                        <td colSpan={7} className="py-8 text-center text-xs text-slate-400">
                                            No matching student result records found.
                                        </td>
                                    </tr>
                                )}
                            </tbody>
                        </table>
                    </div>
                </div>

                {/* ================= 3. STUDENT QUERIES & RESPONSE SECTION ================= */}
                <div className="bg-white border border-slate-200 rounded-2xl p-6 sm:p-8 shadow-sm space-y-5">
                    <div className="pb-4 border-b border-slate-100">
                        <div className="flex items-center gap-2">
                            <MessageSquare size={20} className="text-indigo-600" />
                            <h2 className="text-lg font-bold text-slate-900">
                                Student Academic Inquiries
                            </h2>
                        </div>
                        <p className="text-xs text-slate-500 mt-0.5">
                            Respond directly to doubts, clarification requests, and mark re-evaluations submitted by students
                        </p>
                    </div>

                    <div className="space-y-4">
                        {queries.map((q) => (
                            <div
                                key={q.id}
                                className="border border-slate-200 rounded-xl p-5 space-y-3.5 bg-slate-50/50 hover:bg-slate-50 transition"
                            >
                                {/* Query Header */}
                                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                                    <div className="flex items-center gap-2.5">
                                        <span className="font-bold text-sm text-slate-900">{q.studentName}</span>
                                        <span className="font-mono text-xs text-slate-500 font-semibold bg-white border border-slate-200 px-2 py-0.5 rounded">
                                            {q.rollNo}
                                        </span>
                                        <span className="text-xs text-slate-400">• {q.subject}</span>
                                    </div>

                                    <div className="flex items-center gap-3">
                                        <span className="text-[11px] text-slate-400">{q.createdAt}</span>
                                        <span
                                            className={`px-2.5 py-0.5 rounded-full text-xs font-semibold flex items-center gap-1 ${q.status === 'RESOLVED'
                                                    ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                                                    : 'bg-amber-50 text-amber-700 border border-amber-200'
                                                }`}
                                        >
                                            {q.status === 'RESOLVED' ? (
                                                <>
                                                    <CheckCircle2 size={12} /> Resolved
                                                </>
                                            ) : (
                                                <>
                                                    <Clock size={12} /> Pending Reply
                                                </>
                                            )}
                                        </span>
                                    </div>
                                </div>

                                {/* Inquiry Type Tag & Body */}
                                <div className="space-y-1">
                                    <span className="text-[11px] font-semibold uppercase tracking-wider text-indigo-600">
                                        {q.inquiryType}
                                    </span>
                                    <p className="text-xs sm:text-sm text-slate-700 leading-relaxed bg-white border border-slate-200/80 p-3.5 rounded-lg">
                                        "{q.question}"
                                    </p>
                                </div>

                                {/* Teacher Response Area */}
                                {q.status === 'RESOLVED' && q.response ? (
                                    <div className="bg-emerald-50/50 border border-emerald-200/80 p-3.5 rounded-lg text-xs sm:text-sm space-y-1">
                                        <div className="flex items-center gap-1.5 font-bold text-emerald-800 text-xs">
                                            <CheckCircle2 size={14} /> Faculty Feedback Provided:
                                        </div>
                                        <p className="text-slate-700 pl-5">{q.response}</p>
                                    </div>
                                ) : (
                                    <div className="pt-2 space-y-2">
                                        <label className="block text-xs font-semibold text-slate-700">
                                            Your Response to {q.studentName}:
                                        </label>
                                        <div className="flex gap-2">
                                            <input
                                                type="text"
                                                placeholder="Type response or explanation here..."
                                                value={replyText[q.id] || ''}
                                                onChange={(e) =>
                                                    setReplyText({ ...replyText, [q.id]: e.target.value })
                                                }
                                                className="flex-1 bg-white border border-slate-300 rounded-xl px-3.5 py-2 text-xs sm:text-sm text-slate-800 focus:outline-none focus:border-indigo-600"
                                            />
                                            <button
                                                onClick={() => handleSendReply(q.id)}
                                                className="inline-flex items-center gap-1.5 bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold px-4 py-2 rounded-xl transition shadow-xs shrink-0"
                                            >
                                                <Send size={13} /> Send Response
                                            </button>
                                        </div>
                                    </div>
                                )}

                            </div>
                        ))}
                    </div>
                </div>

            </div>

            {/* ================= MODAL: CREATE / EDIT RESULT ENTRY ================= */}
            {isModalOpen && (
                <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4">
                    <div className="bg-white border border-slate-200 rounded-2xl w-full max-w-lg p-6 space-y-5 shadow-xl">

                        {/* Modal Header */}
                        <div className="flex items-center justify-between pb-3 border-b border-slate-100">
                            <h3 className="text-base font-bold text-slate-900">
                                {modalMode === 'CREATE' ? 'Add Student Examination Result' : 'Edit Examination Marks'}
                            </h3>
                            <button
                                onClick={() => setIsModalOpen(false)}
                                className="p-1 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition"
                            >
                                <X size={18} />
                            </button>
                        </div>

                        {/* Modal Form */}
                        <form onSubmit={handleFormSubmit} className="space-y-4 text-xs sm:text-sm">
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
                                <div>
                                    <label className="block text-xs font-semibold text-slate-700 mb-1">
                                        Student Full Name
                                    </label>
                                    <input
                                        type="text"
                                        required
                                        placeholder="e.g. Rahul Sharma"
                                        value={formData.studentName}
                                        onChange={(e) => setFormData({ ...formData, studentName: e.target.value })}
                                        className="w-full bg-slate-50 border border-slate-300 rounded-xl px-3.5 py-2 text-slate-800 focus:bg-white focus:outline-none focus:border-indigo-600"
                                    />
                                </div>

                                <div>
                                    <label className="block text-xs font-semibold text-slate-700 mb-1">
                                        Roll Number
                                    </label>
                                    <input
                                        type="text"
                                        required
                                        placeholder="e.g. 21CS042"
                                        value={formData.rollNo}
                                        onChange={(e) => setFormData({ ...formData, rollNo: e.target.value })}
                                        className="w-full bg-slate-50 border border-slate-300 rounded-xl px-3.5 py-2 text-slate-800 font-mono focus:bg-white focus:outline-none focus:border-indigo-600"
                                    />
                                </div>
                            </div>

                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
                                <div>
                                    <label className="block text-xs font-semibold text-slate-700 mb-1">
                                        Subject / Course
                                    </label>
                                    <select
                                        value={formData.subjectCode}
                                        onChange={(e) => setFormData({ ...formData, subjectCode: e.target.value })}
                                        className="w-full bg-slate-50 border border-slate-300 rounded-xl px-3 py-2 text-slate-800 focus:bg-white focus:outline-none focus:border-indigo-600"
                                    >
                                        <option value="CS401">CS401 - Database Management Systems</option>
                                        <option value="CS402">CS402 - Computer Networks</option>
                                    </select>
                                </div>

                                <div>
                                    <label className="block text-xs font-semibold text-slate-700 mb-1">
                                        Semester
                                    </label>
                                    <select
                                        value={formData.semester}
                                        onChange={(e) => setFormData({ ...formData, semester: Number(e.target.value) })}
                                        className="w-full bg-slate-50 border border-slate-300 rounded-xl px-3 py-2 text-slate-800 focus:bg-white focus:outline-none focus:border-indigo-600"
                                    >
                                        {[1, 2, 3, 4, 5, 6, 7, 8].map((s) => (
                                            <option key={s} value={s}>Semester 0{s}</option>
                                        ))}
                                    </select>
                                </div>
                            </div>

                            <div className="grid grid-cols-2 gap-3.5">
                                <div>
                                    <label className="block text-xs font-semibold text-slate-700 mb-1">
                                        Marks Scored
                                    </label>
                                    <input
                                        type="number"
                                        required
                                        min={0}
                                        max={100}
                                        value={formData.marks}
                                        onChange={(e) => setFormData({ ...formData, marks: Number(e.target.value) })}
                                        className="w-full bg-slate-50 border border-slate-300 rounded-xl px-3.5 py-2 text-slate-800 font-mono focus:bg-white focus:outline-none focus:border-indigo-600"
                                    />
                                </div>

                                <div>
                                    <label className="block text-xs font-semibold text-slate-700 mb-1">
                                        Max Marks
                                    </label>
                                    <input
                                        type="number"
                                        disabled
                                        value={formData.maxMarks}
                                        className="w-full bg-slate-100 border border-slate-200 rounded-xl px-3.5 py-2 text-slate-500 font-mono"
                                    />
                                </div>
                            </div>

                            <div className="pt-2 text-xs text-slate-500 bg-slate-50 p-2.5 rounded-lg border border-slate-200">
                                Grade auto-calculated: <strong className="text-indigo-600">{calculateGrade(Number(formData.marks))}</strong>
                            </div>

                            {/* Actions */}
                            <div className="flex items-center justify-end gap-3 pt-3 border-t border-slate-100">
                                <button
                                    type="button"
                                    onClick={() => setIsModalOpen(false)}
                                    className="px-4 py-2 text-slate-600 hover:text-slate-800 text-xs font-semibold"
                                >
                                    Cancel
                                </button>
                                <button
                                    type="submit"
                                    className="px-5 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-semibold shadow-xs transition"
                                >
                                    {modalMode === 'CREATE' ? 'Add Result' : 'Save Changes'}
                                </button>
                            </div>
                        </form>

                    </div>
                </div>
            )}

        </div>
    );
};

export default TeacherDashboard;