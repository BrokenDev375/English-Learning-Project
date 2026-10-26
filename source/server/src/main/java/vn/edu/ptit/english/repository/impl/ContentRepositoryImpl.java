package vn.edu.ptit.english.repository.impl;
import java.util.*;
import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.repository.*;

@Repository
public class ContentRepositoryImpl implements ContentRepository {
    private final QuestionJpaRepository questions;private final QuestionVersionJpaRepository qv;
    private final LessonJpaRepository lessons;private final LessonVersionJpaRepository lv;
    private final MaterialVersionJpaRepository mv;private final MaterialJpaRepository materialRoots;private final TopicJpaRepository topics;
    private final ObjectiveJpaRepository objectives;private final UnitJpaRepository units;private final EntityManager em;
    public ContentRepositoryImpl(QuestionJpaRepository questions,QuestionVersionJpaRepository qv,LessonJpaRepository lessons,LessonVersionJpaRepository lv,MaterialVersionJpaRepository mv,MaterialJpaRepository materialRoots,TopicJpaRepository topics,ObjectiveJpaRepository objectives,UnitJpaRepository units,EntityManager em){this.questions=questions;this.qv=qv;this.lessons=lessons;this.lv=lv;this.mv=mv;this.materialRoots=materialRoots;this.topics=topics;this.objectives=objectives;this.units=units;this.em=em;}
    private <T> Optional<T> lock(Optional<T> value,boolean lock){if(lock)value.ifPresent(v -> em.refresh(v,LockModeType.PESSIMISTIC_WRITE));return value;}
    public Optional<QuestionEntity> question(long id,boolean lock){return lock(questions.findById(id),lock);}
    public Optional<LessonEntity> lesson(long id,boolean lock){return lock(lessons.findById(id),lock);}
    public Optional<QuestionVersionEntity> questionVersion(long id){return qv.findById(id);}
    public Optional<LessonVersionEntity> lessonVersion(long id){return lv.findById(id);}
    public Optional<MaterialVersionEntity> materialVersion(long id){return mv.findById(id);}
    public MaterialEntity lockMaterial(long id){return lock(materialRoots.findById(id),true).orElseThrow(()->new IllegalArgumentException("Missing dependency"));}
    public QuestionEntity save(QuestionEntity v){return questions.saveAndFlush(v);}public QuestionVersionEntity save(QuestionVersionEntity v){return qv.saveAndFlush(v);}
    public LessonEntity save(LessonEntity v){return lessons.saveAndFlush(v);}public LessonVersionEntity save(LessonVersionEntity v){return lv.saveAndFlush(v);}
    public void delete(QuestionVersionEntity v){qv.delete(v);qv.flush();}public void delete(LessonVersionEntity v){lv.delete(v);lv.flush();}
    public void refresh(Object v){em.refresh(v);}
    public List<LessonEntity> lessons(long unit){return lessons.findByUnitIdOrderBySortOrderAscIdAsc(unit);}
    public List<QuestionEntity> questionsUsingMaterial(long materialId){
        return em.createQuery("""
            select distinct q from QuestionEntity q, MaterialVersionEntity m
            where q.published.materialVersionId=m.id and m.materialId=:material and m.kind='AUDIO' and q.retiredAt is null
            order by q.id
            """,QuestionEntity.class).setParameter("material",materialId).getResultList();
    }
    public List<LessonEntity> lessonsUsingMaterial(long materialId,long targetVersionId){
        return em.createQuery("""
            select distinct l from LessonEntity l, LessonVersionEntity v join v.questions s,
                QuestionVersionEntity q, MaterialVersionEntity m
            where l.published.id=v.id and s.questionVersionId=q.id and q.materialVersionId=m.id
                and m.materialId=:material and m.kind='AUDIO' and m.id<>:target and l.retiredAt is null
            order by l.id
            """,LessonEntity.class).setParameter("material",materialId).setParameter("target",targetVersionId).getResultList();
    }
    public boolean questionReferenced(long version){return qv.countReferences(version)>0;}
    public boolean questionInUse(long root){return qv.countPublishedUsage(root)>0;}
    static Predicate like(CriteriaBuilder cb,Expression<String> column,String q){String escaped=q.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_");return cb.like(cb.lower(column),"%"+escaped+"%",'!');}
    private <R> Specification<R> bank(Filter f,long actor,boolean admin,boolean lesson){return (root,query,cb)->{
        var draft=root.join("draft",JoinType.LEFT);var published=root.join("published",JoinType.LEFT);var p=new ArrayList<Predicate>();p.add(cb.or(cb.isNotNull(draft.get("id")),cb.isNotNull(published.get("id"))));
        if(f.scope().equals("OWN"))p.add(cb.equal(root.get("ownerId"),actor));
        if(f.scope().equals("PUBLISHED")){p.add(cb.isNotNull(published.get("id")));p.add(cb.isNull(root.get("retiredAt")));}
        if(f.status()!=null)switch(f.status()){case "DRAFT"->{p.add(cb.isNull(published.get("id")));p.add(cb.isNull(root.get("retiredAt")));}case "PUBLISHED"->{p.add(cb.isNotNull(published.get("id")));p.add(cb.isNull(root.get("retiredAt")));}case "RETIRED"->p.add(cb.isNotNull(root.get("retiredAt")));}
        Predicate privateDraft=cb.and(admin?cb.conjunction():cb.equal(root.get("ownerId"),actor),cb.isNotNull(draft.get("id")));
        if(f.scope().equals("PUBLISHED"))privateDraft=cb.disjunction();
        String title=lesson?"title":"prompt";var selected=cb.<String>selectCase().when(privateDraft,draft.get(title)).otherwise(published.get(title));if(!f.q().isBlank())p.add(like(cb,selected,f.q()));
        if(lesson){if(f.unitId()!=null)p.add(cb.equal(root.get("unitId"),f.unitId()));if(f.topicId()!=null){var sub=query.subquery(Long.class);var unit=sub.from(UnitEntity.class);sub.select(unit.get("id")).where(cb.equal(unit.get("topicId"),f.topicId()));p.add(root.get("unitId").in(sub));}}
        else{Object[][] filters={{"topicId",f.topicId()},{"objectiveId",f.objectiveId()},{"type",f.type()},{"difficulty",f.difficulty()}};for(Object[] field:filters)if(field[1]!=null)p.add(cb.equal(cb.selectCase().when(privateDraft,draft.get((String)field[0])).otherwise(published.get((String)field[0])),field[1]));}
        return cb.and(p.toArray(Predicate[]::new));};}
    public Page<QuestionEntity> questions(Filter f,long actor,boolean admin){return questions.findAll(bank(f,actor,admin,false),PageRequest.of(f.page(),f.size(),Sort.by(Sort.Direction.DESC,"id")));}
    public Page<LessonEntity> lessons(Filter f,long actor,boolean admin){return lessons.findAll(bank(f,actor,admin,true),PageRequest.of(f.page(),f.size(),Sort.by("sortOrder","id")));}
    private <R> Specification<R> visible(String q,Long topic,String kind){return (root,query,cb)->{
        var exists=query.subquery(Long.class);var lesson=exists.from(LessonEntity.class);var unit=exists.from(UnitEntity.class);var p=new ArrayList<Predicate>();
        p.add(cb.equal(lesson.get("unitId"),unit.get("id")));p.add(cb.isNull(lesson.get("retiredAt")));p.add(cb.isNotNull(lesson.get("published")));
        if(kind.equals("topic"))p.add(cb.equal(unit.get("topicId"),root.get("id")));
        else if(kind.equals("unit"))p.add(cb.equal(unit.get("id"),root.get("id")));
        else{var version=exists.from(LessonVersionEntity.class);p.add(cb.equal(lesson.get("published").get("id"),version.get("id")));p.add(cb.isMember(root.<Long>get("id"),version.<Set<Long>>get("objectiveIds")));}
        exists.select(lesson.get("id")).where(p.toArray(Predicate[]::new));var result=new ArrayList<Predicate>();result.add(cb.exists(exists));if(!q.isBlank())result.add(like(cb,root.get(kind.equals("unit")?"title":"name"),q));if(topic!=null)result.add(cb.equal(root.get("topicId"),topic));return cb.and(result.toArray(Predicate[]::new));};}
    public Page<TopicEntity> publicTopics(String q,int page,int size){return topics.findAll(visible(q,null,"topic"),PageRequest.of(page,size,Sort.by("id")));}
    public Page<ObjectiveEntity> publicObjectives(String q,Long topic,int page,int size){return objectives.findAll(visible(q,topic,"objective"),PageRequest.of(page,size,Sort.by("id")));}
    public List<UnitEntity> allPublicUnits(){return units.findAll(visible("",null,"unit"),Sort.by("stage","sortOrder","id"));}
    public Page<UnitEntity> publicUnits(String q,Long topic,int page,int size){return units.findAll(visible(q,topic,"unit"),PageRequest.of(page,size,Sort.by("stage","sortOrder","id")));}
}
