package com.quantumstore.payments;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderDto(
        Long id,
        String customerEmail,
        String sku,
        BigDecimal totalAmount,
        String status) {}