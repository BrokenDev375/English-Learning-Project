package vn.edu.ptit.english.repository;

import java.util.Optional;
import vn.edu.ptit.english.entity.AssetEntity;

public interface AssetRepository {
    Optional<AssetEntity> find(long id);
    AssetEntity save(AssetEntity asset);
}
