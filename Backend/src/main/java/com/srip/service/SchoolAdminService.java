package com.srip.service;

import com.srip.domain.Student;
import com.srip.domain.Teacher;
import com.srip.domain.TeacherSubject;
import com.srip.dto.admin.AdminDtos.ExamView;
import com.srip.dto.admin.AdminDtos.LinkCreated;
import com.srip.dto.admin.AdminDtos.StudentView;
import com.srip.dto.admin.AdminDtos.SubjectView;
import com.srip.dto.admin.AdminDtos.TeacherSubjectRequest;
import com.srip.dto.admin.AdminDtos.TopicView;
import com.srip.exception.ApiExceptions;
import com.srip.repository.ExamRepository;
import com.srip.repository.StudentRepository;
import com.srip.repository.SubjectRepository;
import com.srip.repository.TeacherRepository;
import com.srip.repository.TeacherSubjectRepository;
import com.srip.repository.TopicRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Reference-data reads and the link operation only an admin performs. */
@Service
public class SchoolAdminService {

    private final StudentRepository students;
    private final ExamRepository exams;
    private final SubjectRepository subjects;
    private final TopicRepository topics;
    private final TeacherRepository teachers;
    private final TeacherSubjectRepository teacherSubjects;

    public SchoolAdminService(StudentRepository students,
                              ExamRepository exams,
                              SubjectRepository subjects,
                              TopicRepository topics,
                              TeacherRepository teachers,
                              TeacherSubjectRepository teacherSubjects) {
        this.students = students;
        this.exams = exams;
        this.subjects = subjects;
        this.topics = topics;
        this.teachers = teachers;
        this.teacherSubjects = teacherSubjects;
    }

    @Transactional(readOnly = true)
    public List<StudentView> allStudents() {
        return students.findAll().stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<StudentView> studentsInClass(String className) {
        return students.findByClassName(className).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<ExamView> allExams() {
        return exams.findAllByOrderByExamDateAsc().stream()
                .map(exam -> new ExamView(exam.getId(), exam.getCode(), exam.getName(), exam.getTerm(),
                        exam.getExamDate(), exam.getClassName(), exam.getAcademicYear()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SubjectView> allSubjects() {
        return subjects.findAll().stream()
                .map(subject -> new SubjectView(subject.getId(), subject.getCode(),
                        subject.getName(), subject.getClassName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TopicView> topicsOfSubject(String subjectCode) {
        var subject = subjects.findByCode(subjectCode)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Unknown subject code: " + subjectCode));
        return topics.findBySubjectId(subject.getId()).stream()
                .map(topic -> new TopicView(topic.getId(), topic.getChapterName(),
                        topic.getName(), subjectCode))
                .toList();
    }

    @Transactional
    public LinkCreated assignTeacherSubject(TeacherSubjectRequest request) {
        Teacher teacher = teachers.findByStaffNo(request.staffNo())
                .orElseThrow(() -> new ApiExceptions.NotFoundException(
                        "No teacher with staff number " + request.staffNo()));
        var subject = subjects.findByCode(request.subjectCode())
                .orElseThrow(() -> new ApiExceptions.NotFoundException(
                        "Unknown subject code: " + request.subjectCode()));

        boolean alreadyAssigned = teacherSubjects.findByTeacherId(teacher.getId()).stream()
                .anyMatch(assignment -> assignment.getSubject().getId().equals(subject.getId())
                        && assignment.getClassName().equals(request.className()));
        if (alreadyAssigned) {
            throw new ApiExceptions.ConflictException("That teacher already teaches this subject to this class");
        }

        TeacherSubject assignment = teacherSubjects.save(
                new TeacherSubject(teacher, subject, request.className()));
        return new LinkCreated(assignment.getId(), "%s assigned to %s for class %s"
                .formatted(teacher.getFullName(), subject.getName(), request.className()));
    }

    private StudentView toView(Student student) {
        return new StudentView(
                student.getId(),
                student.getAdmissionNo(),
                student.getFullName(),
                student.getClassName(),
                student.getSection(),
                student.getAcademicYear(),
                student.getUser() != null);
    }
}
