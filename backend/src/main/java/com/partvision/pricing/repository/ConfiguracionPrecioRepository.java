package com.partvision.pricing.repository;

import com.partvision.pricing.domain.ConfiguracionPrecio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConfiguracionPrecioRepository extends JpaRepository<ConfiguracionPrecio, Long> {
    Optional<ConfiguracionPrecio> findByProveedorIgnoreCase(String proveedor);
}
