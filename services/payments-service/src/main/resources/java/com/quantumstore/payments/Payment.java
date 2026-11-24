package com.quantumstore.payments;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "payments")
public class Payment extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "order_id", nullable = false)
    public Long orderId;

    @Column(name = "customer_email", nullable = false, length = 180)
    public String customerEmail;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal amount;

    @Column(nullable = false, length = 3)
    public String currency = "USD";

    @Column(name = "payment_method", nullable = false, length = 32)
    public String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    public PaymentStatus status;

    @Column(name = "transaction_ref", nullable = false, unique = true, length = 64)
    public String transactionRef;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt = OffsetDateTime.now();

    public static Payment findCapturedByOrderId(Long orderId) {
        return find("orderId = ?1 and status = ?2", orderId, PaymentStatus.CAPTURED).firstResult();
    }
}