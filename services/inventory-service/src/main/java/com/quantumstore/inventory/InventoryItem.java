package com.quantumstore.inventory;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "inventory_items")
public class InventoryItem extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "product_id", nullable = false, unique = true)
    public Long productId;

    @Column(nullable = false, unique = true, length = 64)
    public String sku;

    @Column(name = "available_quantity", nullable = false)
    public int availableQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    public int reservedQuantity;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt = OffsetDateTime.now();

    public static InventoryItem findBySku(String sku) {
        return find("sku", sku).firstResult();
    }
}