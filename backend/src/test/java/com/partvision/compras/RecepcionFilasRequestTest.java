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

    @Test
    void objetoSinFilas_noSeLee() {
        assertThatThrownBy(() -> leer("{\"otra\": 1}")).hasMessageContaining("array de filas");
    }

    @Test
    void valueQueNoEsArray_noSeLee() {
        assertThatThrownBy(() -> leer("{\"value\": \"texto\"}")).hasMessageContaining("array de filas");
    }
}
