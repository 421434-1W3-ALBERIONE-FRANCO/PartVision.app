package com.partvision.pricing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProveedorProducto(
        @JsonProperty("Codigo") String codigo,
        @JsonProperty("Titulo") String titulo,
        @JsonProperty("Marca") String marca,
        @JsonProperty("PrecioListaSinIVA") BigDecimal precioLista,
        @JsonProperty("PrecioCostoMostradorSinIVA") BigDecimal precioCostoSinIva,
        @JsonProperty("PrecioCostoMostradorConIVA") BigDecimal precioCostoConIva,
        @JsonProperty("TasaIVA") BigDecimal tasaIva
) {}
