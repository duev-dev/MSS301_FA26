package com.mss301.brand.entity;

import jakarta.persistence.*;
import lombok.*;

// [TODO-5.1: bảng WatchBrands]
@Entity
@Table(name = "WatchBrands")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WatchBrand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "BrandID")
    private Integer brandId;

    @Column(name = "BrandName", length = 200)
    private String brandName;

    @Column(name = "CountryOfOrigin", length = 50)
    private String countryOfOrigin;
}
