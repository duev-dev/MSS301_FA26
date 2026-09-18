package com.fudn.product_service.exception;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(String id) {
        super("Khong tim thay san pham voi id: " + id);
    }
}
