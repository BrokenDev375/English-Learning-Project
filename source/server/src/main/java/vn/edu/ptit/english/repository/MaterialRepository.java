package vn.edu.ptit.english.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import vn.edu.ptit.english.entity.*;

public interface MaterialRepository {
    Optional<MaterialEntity> find(long id,boolean lock);
    Optional<MaterialVersionEntity> version(long id,long materialId);
    MaterialEntity save(MaterialEntity root);
    MaterialVersionEntity save(MaterialVersionEntity version);
    void delete(MaterialVersionEntity version);
    Page<MaterialEntity> search(long actorId,boolean admin,String scope,String q,String status,String kind,int page,int size);
    boolean assetReadable(long assetId,long actorId);
    boolean versionReferenced(long versionId);
    boolean publishedUsage(long materialId);
}
