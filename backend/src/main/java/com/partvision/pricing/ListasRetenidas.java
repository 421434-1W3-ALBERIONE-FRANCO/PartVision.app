package com.partvision.pricing;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** El archivo de la ultima lista que quedo retenida, por proveedor (ver V33). */
@Component
class ListasRetenidas {

    private final JdbcTemplate jdbc;

    ListasRetenidas(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void guardar(String proveedor, byte[] archivo, String nombre) {
        jdbc.update("""
                insert into listas_retenidas (proveedor, archivo, nombre, recibida_en)
                values (?, ?, ?, now())
                on conflict (proveedor) do update
                   set archivo = excluded.archivo, nombre = excluded.nombre, recibida_en = excluded.recibida_en
                """, proveedor, archivo, nombre);
    }

    Optional<byte[]> leer(String proveedor) {
        List<byte[]> r = jdbc.query("select archivo from listas_retenidas where proveedor = ?",
                (rs, i) -> rs.getBytes(1), proveedor);
        return r.stream().findFirst();
    }

    boolean hay(String proveedor) {
        Integer n = jdbc.queryForObject("select count(*) from listas_retenidas where proveedor = ?", Integer.class, proveedor);
        return n != null && n > 0;
    }

    void borrar(String proveedor) {
        jdbc.update("delete from listas_retenidas where proveedor = ?", proveedor);
    }
}
