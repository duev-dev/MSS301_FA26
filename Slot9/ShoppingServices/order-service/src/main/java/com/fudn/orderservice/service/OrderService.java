package com.fudn.orderservice.service;

import com.fudn.orderservice.client.InventoryClient;
import com.fudn.orderservice.dto.OrderRequest;
import com.fudn.orderservice.model.Order;
import com.fudn.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;   // TODO 3.4: inject FeignClient

    public void placeOrder(OrderRequest orderRequest) {
        // 1. Goi dong bo sang Inventory Service
        boolean inStock = inventoryClient.isInStock(
                orderRequest.skuCode(), orderRequest.quantity());

        // 2. Con hang -> luu don; het hang -> nem exception
        if (inStock) {
            Order order = mapToOrder(orderRequest);
            orderRepository.save(order);
        } else {
            throw new RuntimeException(
                    "Product with SkuCode " + orderRequest.skuCode() + " is not in stock");
        }
    }

    private static Order mapToOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());
        order.setPrice(orderRequest.price());
        order.setQuantity(orderRequest.quantity());
        order.setSkuCode(orderRequest.skuCode());
        return order;
    }
}
