package vn.edu.ptit.english.service;

import vn.edu.ptit.english.dto.StatisticsViews.*;
import vn.edu.ptit.english.dto.CatalogViews.CatalogPage;
import vn.edu.ptit.english.security.UserPrincipal;

public interface StatisticsService {
    LearnerOverview learner(String period, UserPrincipal actor);
    CatalogPage<Breakdown> breakdown(String period, String groupBy, int page, int size, UserPrincipal actor);
    CatalogPage<Lesson> lessons(int page, int size, UserPrincipal actor);
    AdminOverview admin(String period, UserPrincipal actor);
    CatalogPage<Question> questions(String period, String difficulty, int page, int size, UserPrincipal actor);
    CatalogPage<TeacherUnit> teacherUnits(int page, int size, UserPrincipal actor);
    TeacherOverview teacher(String period, Long unitId, UserPrincipal actor);
    CatalogPage<TeacherLesson> teacherLessons(String period, Long unitId, int page, int size, UserPrincipal actor);
    CatalogPage<Question> teacherQuestions(String period, Long unitId, String difficulty, int page, int size, UserPrincipal actor);
    CatalogPage<Breakdown> teacherBreakdown(String period, Long unitId, String groupBy, int page, int size, UserPrincipal actor);
}
