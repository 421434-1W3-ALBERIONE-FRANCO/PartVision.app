package com.partvision.pricing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProveedorCatalogoResponse(Meta meta, Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(int rows, int totalRows, int page, int totalPages, int pageSize) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(List<ProveedorProducto> productos) {}
}
