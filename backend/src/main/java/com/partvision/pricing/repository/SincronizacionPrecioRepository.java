package com.partvision.pricing.repository;

import com.partvision.pricing.domain.ResultadoSincronizacion;
import com.partvision.pricing.domain.SincronizacionPrecio;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SincronizacionPrecioRepository extends JpaRepository<SincronizacionPrecio, Long> {

    List<SincronizacionPrecio> findByProveedorOrderByIniciadaEnDesc(String proveedor, Pageable pageable);

    Optional<SincronizacionPrecio> findFirstByProveedorAndResultadoInOrderByIniciadaEnDesc(
            String proveedor, Collection<ResultadoSincronizacion> resultados);

    /** Las corridas que quedaron a medias porque el servidor se reinicio en el medio. */
    @Modifying
    @Query("update SincronizacionPrecio s set s.resultado = :error, s.terminadaEn = :ahora, s.mensaje = :mensaje "
            + "where s.resultado = :enCurso")
    int cerrarInterrumpidas(@Param("enCurso") ResultadoSincronizacion enCurso,
                            @Param("error") ResultadoSincronizacion error,
                            @Param("ahora") LocalDateTime ahora,
                            @Param("mensaje") String mensaje);
}
