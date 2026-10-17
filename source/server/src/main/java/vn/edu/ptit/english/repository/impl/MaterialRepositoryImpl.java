package vn.edu.ptit.english.repository.impl;

import java.util.*;
import jakarta.persistence.criteria.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.repository.*;

@Repository
public class MaterialRepositoryImpl implements MaterialRepository {
    private final MaterialJpaRepository roots;
    private final MaterialVersionJpaRepository versions;
    public MaterialRepositoryImpl(MaterialJpaRepository roots,MaterialVersionJpaRepository versions){this.roots=roots;this.versions=versions;}
    public Optional<MaterialEntity> find(long id,boolean lock){return lock?roots.lockById(id):roots.findById(id);}
    public Optional<MaterialVersionEntity> version(long id,long materialId){return versions.findByIdAndMaterialId(id,materialId);}
    public MaterialEntity save(MaterialEntity root){return roots.saveAndFlush(root);}
    public MaterialVersionEntity save(MaterialVersionEntity version){return versions.saveAndFlush(version);}
    public void delete(MaterialVersionEntity version){versions.delete(version);versions.flush();}
    public boolean assetReadable(long assetId,long actorId){return versions.countReadableAsset(assetId,actorId)>0;}
    public boolean versionReferenced(long versionId){return versions.countReferences(versionId)>0;}
    public boolean publishedUsage(long root){return versions.countPublishedQuestionUsage(root)>0||versions.countPublishedLessonUsage(root)>0;}
    public Page<MaterialEntity> search(long actorId,boolean admin,String scope,String q,String status,String kind,int page,int size){
        Specification<MaterialEntity> filter=(root,query,cb) -> {
            var draft=root.join("draft",JoinType.LEFT);var published=root.join("published",JoinType.LEFT);
            var predicates=new ArrayList<Predicate>();
            predicates.add(cb.or(cb.isNotNull(draft.get("id")),cb.isNotNull(published.get("id"))));
            if(scope.equals("OWN"))predicates.add(cb.equal(root.get("ownerId"),actorId));
            if(scope.equals("PUBLISHED")){predicates.add(cb.isNotNull(published.get("id")));predicates.add(cb.isNull(root.get("retiredAt")));}
            if(status!=null)switch(status){
                case "DRAFT" -> {predicates.add(cb.isNull(root.get("retiredAt")));predicates.add(cb.isNull(published.get("id")));}
                case "PUBLISHED" -> {predicates.add(cb.isNull(root.get("retiredAt")));predicates.add(cb.isNotNull(published.get("id")));}
                case "RETIRED" -> predicates.add(cb.isNotNull(root.get("retiredAt")));
                default -> throw new IllegalArgumentException("Invalid status");
            }
            Expression<String> title=published.get("title"),selectedKind=published.get("kind");
            if(!scope.equals("PUBLISHED")){
                Predicate privateView=admin?cb.conjunction():cb.equal(root.get("ownerId"),actorId);
                title=cb.<String>selectCase().when(cb.and(privateView,cb.isNotNull(draft.get("id"))),draft.get("title")).otherwise(published.get("title"));
                selectedKind=cb.<String>selectCase().when(cb.and(privateView,cb.isNotNull(draft.get("id"))),draft.get("kind")).otherwise(published.get("kind"));
            }
            if(!q.isBlank()){String escaped=q.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_");predicates.add(cb.like(cb.lower(title),"%"+escaped+"%",'!'));}
            if(kind!=null)predicates.add(cb.equal(selectedKind,kind));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return roots.findAll(filter,PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"id")));
    }
}
