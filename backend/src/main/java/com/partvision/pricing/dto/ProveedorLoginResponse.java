package com.partvision.pricing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProveedorLoginResponse(Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String token) {}
}
