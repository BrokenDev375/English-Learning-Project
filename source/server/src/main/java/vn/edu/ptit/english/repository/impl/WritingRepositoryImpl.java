package vn.edu.ptit.english.repository.impl;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.repository.*;
@Repository
public class WritingRepositoryImpl implements WritingRepository {
    private final WritingTaskJpaRepository tasks;private final WritingVersionJpaRepository versions;private final WritingWorkJpaRepository works;private final WritingSubmissionJpaRepository submissions;private final WritingGradeJpaRepository grades;
    public WritingRepositoryImpl(WritingTaskJpaRepository tasks,WritingVersionJpaRepository versions,WritingWorkJpaRepository works,WritingSubmissionJpaRepository submissions,WritingGradeJpaRepository grades){this.tasks=tasks;this.versions=versions;this.works=works;this.submissions=submissions;this.grades=grades;}
    public Optional<WritingTaskEntity> task(long id){return tasks.findById(id);}
    public List<WritingTaskEntity> tasks(Collection<Long> ids){return ids.isEmpty()?List.of():tasks.findAllById(ids);}
    public WritingTaskEntity save(WritingTaskEntity t){return tasks.saveAndFlush(t);}
    public WritingVersionEntity save(WritingVersionEntity v){return versions.saveAndFlush(v);}
    public Optional<WritingVersionEntity> version(long id){return versions.findById(id);}
    public Optional<WritingWorkEntity> work(long student,long unit){return works.findByStudentIdAndUnitId(student,unit);}
    public Optional<WritingWorkEntity> work(long id){return works.findById(id);}
    public List<WritingWorkEntity> works(long student,Collection<Long> ids){return ids.isEmpty()?List.of():works.findByStudentIdAndUnitIdIn(student,ids);}
    public WritingWorkEntity save(WritingWorkEntity w){return works.saveAndFlush(w);}
    public Optional<WritingSubmissionEntity> submission(long id){return submissions.findById(id);}
    public WritingSubmissionEntity save(WritingSubmissionEntity s){return submissions.saveAndFlush(s);}
    public List<WritingSubmissionEntity> history(long id){return submissions.findByWorkIdOrderByIdDesc(id);}
    public List<WritingGradeEntity> grades(Collection<Long> ids){return ids.isEmpty()?List.of():grades.findBySubmissionIdIn(ids);}
    public WritingGradeEntity save(WritingGradeEntity g){return grades.saveAndFlush(g);}
    public Page<WritingSubmissionEntity> queue(long unit,String state,int page,int size){return submissions.queue(unit,state,PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"id")));}
}
