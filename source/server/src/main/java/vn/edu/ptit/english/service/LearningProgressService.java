package vn.edu.ptit.english.service;
import vn.edu.ptit.english.dto.AttemptViews.LessonProgress;
import vn.edu.ptit.english.dto.CatalogViews.CatalogPage;
import vn.edu.ptit.english.security.UserPrincipal;
public interface LearningProgressService {
    CatalogPage<LessonProgress> list(Long unitId,int page,int size,UserPrincipal actor);
}
