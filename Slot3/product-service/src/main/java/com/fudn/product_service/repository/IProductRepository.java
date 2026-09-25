package com.fudn.product_service.repository;

import com.fudn.product_service.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IProductRepository extends JpaRepository<Product, String> {
}
