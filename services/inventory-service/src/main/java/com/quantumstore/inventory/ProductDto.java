package com.quantumstore.inventory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductDto(Long id, String sku, String name, boolean active) {}