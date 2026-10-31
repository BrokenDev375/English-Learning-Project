package vn.edu.ptit.english.repository;
import java.util.Optional;
import vn.edu.ptit.english.entity.AttemptAudioListenEntity;
public interface AttemptAudioRepository {
    Optional<AttemptAudioListenEntity> find(long attemptId,long materialVersionId);
    void save(AttemptAudioListenEntity value);
}
