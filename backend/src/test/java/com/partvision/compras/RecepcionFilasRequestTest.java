package com.partvision.compras;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.partvision.compras.dto.RecepcionFilasRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Las tres formas en que puede llegar el cuerpo segun como se arme la accion HTTP del flujo.
 * El 2026-09-22 el flujo del cliente mandaba el array suelto y rebotaba.
 */
class RecepcionFilasRequestTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String UNA_FILA = """
            {"Factura": "900004113", "F. Factura": "46259", "Codigo": "JTKJTS", "Cantidad": "1",
             "Descripcion": "JUNTA TAPA CILINDRO KIA", "Estatus stock": "EN TRÁNSITO",
             "Proveedor": "Autopartes del Sur"}""";

    private RecepcionFilasRequest leer(String json) throws Exception {
        return MAPPER.readValue(json, RecepcionFilasRequest.class);
    }

    /** La salida completa de "Enumerar las filas de una tabla". */
    @Test
    void objetoConValue() throws Exception {
        RecepcionFilasRequest r = leer("{\"@odata.context\": \"x\", \"value\": [" + UNA_FILA + "]}");

        assertThat(r.filas()).hasSize(1);
        assertThat(r.filas().get(0).factura()).isEqualTo("900004113");
        assertThat(r.filas().get(0).proveedor()).isEqualTo("Autopartes del Sur");
    }

    /** Lo que queda al insertar el contenido dinamico de las filas en el cuerpo. */
    @Test
    void soloElArray() throws Exception {
        RecepcionFilasRequest r = leer("[" + UNA_FILA + "]");

        assertThat(r.filas()).hasSize(1);
        assertThat(r.filas().get(0).codigo()).isEqualTo("JTKJTS");
    }

    @Test
    void objetoConFilas() throws Exception {
        RecepcionFilasRequest r = leer("{\"filas\": [" + UNA_FILA + "]}");

        assertThat(r.filas()).hasSize(1);
    }

    @Test
    void arrayVacio_esValido() throws Exception {
        assertThat(leer("{\"value\": []}").filas()).isEmpty();
        assertThat(leer("[]").filas()).isEmpty();
    }

    /**
     * Como llegan de verdad los nombres de columna: el conector de Excel escapa lo que no puede
     * usar en un identificador. Hasta el 2026-09-24 "F. Factura" llegaba como "F_x002e_ Factura",
     * no coincidia con ningun alias y la fecha de la factura quedaba en null sin que nada fallara.
     */
    @Test
    void nombreDeColumnaEscapado_seEntiende() throws Exception {
        RecepcionFilasRequest r = leer("""
                {"value": [{"@odata.etag": "", "ItemInternalId": "1bca8a68",
                 "Factura": "900004113", "F_x002e_ Factura": "46259", "Codigo": "JTKJTS",
                 "Cantidad": "1", "Descripci_x00f3_n": "JUNTA TAPA CILINDRO KIA",
                 "Estatus stock": "EN TRÁNSITO", "Proveedor": "Autopartes del Sur"}]}""");

        assertThat(r.filas()).hasSize(1);
        assertThat(r.filas().get(0).fechaFactura()).isEqualTo("46259");
        assertThat(r.filas().get(0).descripcion()).isEqualTo("JUNTA TAPA CILINDRO KIA");
    }

    /** Un "_x" que no sea un escape de verdad es parte del nombre y se deja como esta. */
    @Test
    void nombreConGuionBajoX_noSeToca() throws Exception {
        RecepcionFilasRequest r = leer("""
                [{"Factura": "1", "F. Factura": "46259", "Codigo": "A", "Cantidad": "1",
                  "Otra_xyz_ columna": "z"}]""");

        assertThat(r.filas().get(0).fechaFactura()).isEqualTo("46259");
    }

    /**
     * Una fila que no es un objeto se deja pasar tal cual: la valida despues la validacion del
     * controller (@NotNull por elemento), que responde 400 diciendo cual es.
     */
    @Test
    void filaQueNoEsObjeto_noRompeElParseo() throws Exception {
        RecepcionFilasRequest r = leer("[null]");

        assertThat(r.filas()).hasSize(1).containsOnlyNulls();
    }

    @Test
    void objetoSinFilas_noSeLee() {
        assertThatThrownBy(() -> leer("{\"otra\": 1}")).hasMessageContaining("array de filas");
    }

    @Test
    void valueQueNoEsArray_noSeLee() {
        assertThatThrownBy(() -> leer("{\"value\": \"texto\"}")).hasMessageContaining("array de filas");
    }
}
