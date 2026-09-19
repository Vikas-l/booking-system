package com.bookingsystem.inventory.repository;

import com.bookingsystem.inventory.domain.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {
}
