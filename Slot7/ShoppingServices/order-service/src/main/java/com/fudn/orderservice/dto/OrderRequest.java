package com.fudn.orderservice.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OrderRequest(Long id,
                           @NotNull(message = "skuCode must not be null")
                           String skuCode,
                           BigDecimal price,
                           Integer quantity) {

}
