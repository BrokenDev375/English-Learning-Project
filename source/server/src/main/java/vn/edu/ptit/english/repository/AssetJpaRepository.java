package vn.edu.ptit.english.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.ptit.english.entity.AssetEntity;

public interface AssetJpaRepository extends JpaRepository<AssetEntity,Long> {}
