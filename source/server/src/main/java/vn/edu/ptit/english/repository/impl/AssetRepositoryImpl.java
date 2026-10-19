package vn.edu.ptit.english.repository.impl;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.english.entity.AssetEntity;
import vn.edu.ptit.english.repository.*;

@Repository
public class AssetRepositoryImpl implements AssetRepository {
    private final AssetJpaRepository assets;
    public AssetRepositoryImpl(AssetJpaRepository assets){this.assets=assets;}
    public Optional<AssetEntity> find(long id){return assets.findById(id);}
    public AssetEntity save(AssetEntity asset){return assets.saveAndFlush(asset);}
}
