package com.mss301.inventory.controller;

import com.mss301.inventory.entity.*;
import com.mss301.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final ProductRepository productRepository;
    private final ReservationRepository reservationRepository;

    @GetMapping("/products")
    public List<Product> products() {
        return productRepository.findAll();
    }

    @GetMapping("/reservations")
    public List<Reservation> reservations() {
        return reservationRepository.findAll();
    }
}
