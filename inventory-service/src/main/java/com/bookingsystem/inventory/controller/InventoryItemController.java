package com.bookingsystem.inventory.controller;

import com.bookingsystem.inventory.domain.InventoryItem;
import com.bookingsystem.inventory.dto.CreateInventoryItemRequest;
import com.bookingsystem.inventory.dto.InventoryItemResponse;
import com.bookingsystem.inventory.exception.InventoryItemNotFoundException;
import com.bookingsystem.inventory.repository.InventoryItemRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory-items")
public class InventoryItemController {

    private final InventoryItemRepository inventoryItemRepository;

    public InventoryItemController(InventoryItemRepository inventoryItemRepository) {
        this.inventoryItemRepository = inventoryItemRepository;
    }

    @PostMapping
    public ResponseEntity<InventoryItemResponse> create(@Valid @RequestBody CreateInventoryItemRequest request) {
        InventoryItem saved = inventoryItemRepository.save(
                new InventoryItem(request.name(), request.totalQuantity()));
        return ResponseEntity.status(HttpStatus.CREATED).body(InventoryItemResponse.from(saved));
    }

    @GetMapping("/{id}")
    public InventoryItemResponse get(@PathVariable Long id) {
        return inventoryItemRepository.findById(id)
                .map(InventoryItemResponse::from)
                .orElseThrow(() -> new InventoryItemNotFoundException(id));
    }
}
