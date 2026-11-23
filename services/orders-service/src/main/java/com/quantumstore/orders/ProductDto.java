package com.quantumstore.orders;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductDto(Long id, String sku, String name, BigDecimal price, boolean active) {}