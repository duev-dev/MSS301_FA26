package com.mss301.brand.repository;

import com.mss301.brand.entity.BrandWatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// [TODO-5.1]
public interface BrandWatchRepository extends JpaRepository<BrandWatch, Long> {

    Optional<BrandWatch> findByWatchId(Integer watchId);

    long countByBrandId(Integer brandId);

    List<BrandWatch> findByBrandIdOrderByWatchIdAsc(Integer brandId);
}
