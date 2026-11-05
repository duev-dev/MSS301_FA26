package com.mss301.brand.repository;

import com.mss301.brand.entity.WatchBrand;
import org.springframework.data.jpa.repository.JpaRepository;

// [TODO-5.1]
public interface BrandRepository extends JpaRepository<WatchBrand, Integer> {
}
