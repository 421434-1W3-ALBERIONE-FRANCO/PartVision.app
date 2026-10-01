package com.partvision.pricing.dto;

import java.util.List;

/** @param ids los precios elegidos; siempre explicitos, nunca "todos" por omision */
public record RevisionPreciosRequest(List<Long> ids) {}
