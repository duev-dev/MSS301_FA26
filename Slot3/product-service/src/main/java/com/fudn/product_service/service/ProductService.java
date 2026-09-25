package com.fudn.product_service.service;

import com.fudn.product_service.dto.ProductRequest;
import com.fudn.product_service.dto.ProductResponse;
import com.fudn.product_service.exception.ProductNotFoundException;
import com.fudn.product_service.model.Product;
import com.fudn.product_service.repository.IProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {
    private final IProductRepository productRepository;
    public ProductResponse createProduct(ProductRequest productRequest) {
        // Ánh xạ từ ProductRequest sang Product entity, sau đó lưu vào DB.
        Product product = Product.builder()
                .id(productRequest.getId())
                .name(productRequest.getName())
                .description(productRequest.getDescription())
                .price(productRequest.getPrice())
                .build();
        productRepository.save(product);
        log.info("Product {} saved..", product.getId());
        // Ánh xạ từ Product entity sang ProductResponse để trả về cho client.
        return new ProductResponse(product.getId(), product.getName(),
                product.getDescription(), product.getPrice());
    }

    public List<ProductResponse> getAllProducts() {
        // Lấy tất cả sản phẩm từ DB, ánh xạ sang ProductResponse.
        List<Product> products = productRepository.findAll();
        return products.stream()
                .map(product -> new ProductResponse(product.getId(),
                        product.getName(), product.getDescription(),
                        product.getPrice())).toList();
    }


    public ProductResponse updateProduct(String id, ProductRequest productRequest) {
        Product product = productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        product.setName(productRequest.getName());
        product.setDescription(productRequest.getDescription());
        product.setPrice(productRequest.getPrice());
        productRepository.save(product);
        return new ProductResponse(product.getId(), product.getName(), product.getDescription(), product.getPrice());
    }

    public void deleteProduct(String id) {
        if (!productRepository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        productRepository.deleteById(id);
    }
}
