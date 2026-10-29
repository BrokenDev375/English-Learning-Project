package vn.edu.ptit.english.repository;
import java.util.*;
import org.springframework.data.domain.Page;
import vn.edu.ptit.english.entity.*;
public interface WritingRepository {
    Optional<WritingTaskEntity> task(long unitId);
    List<WritingTaskEntity> tasks(Collection<Long> unitIds);
    WritingTaskEntity save(WritingTaskEntity task);
    WritingVersionEntity save(WritingVersionEntity version);
    Optional<WritingVersionEntity> version(long id);
    Optional<WritingWorkEntity> work(long student,long unit);
    Optional<WritingWorkEntity> work(long id);
    List<WritingWorkEntity> works(long student,Collection<Long> units);
    WritingWorkEntity save(WritingWorkEntity work);
    Optional<WritingSubmissionEntity> submission(long id);
    WritingSubmissionEntity save(WritingSubmissionEntity submission);
    List<WritingSubmissionEntity> history(long workId);
    List<WritingGradeEntity> grades(Collection<Long> submissions);
    WritingGradeEntity save(WritingGradeEntity grade);
    Page<WritingSubmissionEntity> queue(long unit,String state,int page,int size);
}
