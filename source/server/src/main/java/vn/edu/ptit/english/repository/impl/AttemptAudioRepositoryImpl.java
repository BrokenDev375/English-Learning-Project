package vn.edu.ptit.english.repository.impl;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import vn.edu.ptit.english.entity.AttemptAudioListenEntity;
import vn.edu.ptit.english.repository.AttemptAudioRepository;
@Repository
public class AttemptAudioRepositoryImpl implements AttemptAudioRepository {
    private final EntityManager em;
    public AttemptAudioRepositoryImpl(EntityManager em){this.em=em;}
    public Optional<AttemptAudioListenEntity> find(long attemptId,long materialVersionId){
        return em.createQuery("select a from AttemptAudioListenEntity a where a.attemptId=:attempt and a.materialVersionId=:material",AttemptAudioListenEntity.class)
            .setParameter("attempt",attemptId).setParameter("material",materialVersionId).getResultStream().findFirst();
    }
    public void save(AttemptAudioListenEntity value){if(value.getId()==null)em.persist(value);em.flush();}
}
