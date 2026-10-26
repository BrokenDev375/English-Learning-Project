package vn.edu.ptit.english.service;
import vn.edu.ptit.english.dto.AttemptViews.LessonProgress;
import vn.edu.ptit.english.dto.CatalogViews.CatalogPage;
import vn.edu.ptit.english.security.UserPrincipal;
public interface LearningProgressService {
    CatalogPage<vn.edu.ptit.english.dto.AttemptViews.RoundProgress> rounds(String q,Long topicId,int page,int size,UserPrincipal actor);
    vn.edu.ptit.english.dto.AttemptViews.RoundProgress round(long unitId,UserPrincipal actor);
    CatalogPage<LessonProgress> list(Long unitId,int page,int size,UserPrincipal actor);
}
