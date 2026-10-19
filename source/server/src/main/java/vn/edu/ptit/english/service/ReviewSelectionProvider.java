package vn.edu.ptit.english.service;
import java.util.List;
/** M04 supplies a provider; all returned versions are revalidated by M03. No demo fallback. */
public interface ReviewSelectionProvider {
    List<Long> select(long userId,String reviewKind,Long topicId,int count);
}
