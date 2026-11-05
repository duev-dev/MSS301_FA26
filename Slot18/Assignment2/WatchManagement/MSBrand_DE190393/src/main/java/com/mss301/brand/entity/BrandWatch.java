package com.mss301.brand.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Dữ liệu mà MSBrand sở hữu về quan hệ Brand - Watch.
 * Watches (DBWatch) và BrandWatches (DBBrand) nằm ở 2 database khác nhau,
 * nên KHÔNG thể dùng 1 transaction ACID -> phải dùng Saga để giữ chúng nhất quán.
 */
@Entity
@Table(name = "BrandWatches",
       uniqueConstraints = @UniqueConstraint(name = "UQ_BrandWatches_WatchID", columnNames = "WatchID"))
// [TODO-5.1: bảng BrandWatches (dữ liệu MSBrand sở hữu)]
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrandWatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @Column(name = "WatchID", nullable = false)
    private Integer watchId;          // 1 watch chỉ thuộc 1 brand -> unique

    @Column(name = "BrandID", nullable = false)
    private Integer brandId;

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;
}
