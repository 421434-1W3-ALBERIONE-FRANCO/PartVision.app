package com.partvision.compras;

import com.partvision.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Los valores de la planilla "Ingreso stock" tal como pueden llegar desde Power Automate.
 * Los numeros de serie de Excel estan calculados aparte (dias desde el 30/12/1899).
 */
class LectorSheetTest {

    private static final LocalDate DIA_FACTURA = LocalDate.of(2026, 8, 24);

    // --- fecha ---

    @Test
    void fecha_comoSeVeEnLaPlanilla() {
        assertThat(LectorSheet.fecha("24/08/2026")).isEqualTo(DIA_FACTURA);
    }

    @Test
    void fecha_sinCerosALaIzquierda() {
        assertThat(LectorSheet.fecha("4/8/2026")).isEqualTo(LocalDate.of(2026, 8, 4));
    }

    @Test
    void fecha_iso() {
        assertThat(LectorSheet.fecha("2026-08-24")).isEqualTo(DIA_FACTURA);
    }

    /** Lo que devuelve Power Automate si la accion lee con formato ISO 8601. */
    @Test
    void fecha_isoConHora() {
        assertThat(LectorSheet.fecha("2026-08-24T00:00:00.000Z")).isEqualTo(DIA_FACTURA);
    }

    /** Lo que devuelve Power Automate por defecto: el numero de serie de Excel. */
    @Test
    void fecha_numeroDeSerieDeExcel() {
        assertThat(LectorSheet.fecha("46258")).isEqualTo(DIA_FACTURA);
        assertThat(LectorSheet.fecha("46259.0")).isEqualTo(LocalDate.of(2026, 8, 25));
    }

    @Test
    void fecha_serieEnLosBordesDelRango() {
        assertThat(LectorSheet.fecha("32874")).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(LectorSheet.fecha("73050")).isEqualTo(LocalDate.of(2099, 12, 31));
    }

    /** Un numero cualquiera no es una fecha: 123 seria 1900, 20260824 un disparate. */
    @Test
    void fecha_numeroFueraDeRango_esError() {
        assertThatThrownBy(() -> LectorSheet.fecha("123")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> LectorSheet.fecha("20260824")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> LectorSheet.fecha("32873")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> LectorSheet.fecha("73051")).isInstanceOf(BusinessException.class);
    }

    @Test
    void fecha_diaQueNoExiste_esError() {
        assertThatThrownBy(() -> LectorSheet.fecha("31/02/2026"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Formato de fecha inválido");
    }

    @Test
    void fecha_isoInvalida_esError() {
        assertThatThrownBy(() -> LectorSheet.fecha("2026-13-45")).isInstanceOf(BusinessException.class);
    }

    @Test
    void fecha_texto_esError() {
        assertThatThrownBy(() -> LectorSheet.fecha("ayer")).isInstanceOf(BusinessException.class);
    }

    @Test
    void fecha_faltante_esError() {
        assertThatThrownBy(() -> LectorSheet.fecha(null)).hasMessageContaining("Falta la fecha");
        assertThatThrownBy(() -> LectorSheet.fecha("  ")).hasMessageContaining("Falta la fecha");
    }

    // --- cantidad ---

    @Test
    void cantidad_entera() {
        assertThat(LectorSheet.cantidad("2")).isEqualTo(2);
        assertThat(LectorSheet.cantidad(" 50 ")).isEqualTo(50);
    }

    @Test
    void cantidad_serializadaConDecimalesCero() {
        assertThat(LectorSheet.cantidad("2.0")).isEqualTo(2);
        assertThat(LectorSheet.cantidad("2.00")).isEqualTo(2);
    }

    /** Filas sin cantidad utilizable: anotaciones como "CONTROLO:" o datos rotos. */
    @Test
    void cantidad_invalida_esNull() {
        assertThat(LectorSheet.cantidad(null)).isNull();
        assertThat(LectorSheet.cantidad("")).isNull();
        assertThat(LectorSheet.cantidad("0")).isNull();
        assertThat(LectorSheet.cantidad("-1")).isNull();
        assertThat(LectorSheet.cantidad("1.5")).isNull();
        assertThat(LectorSheet.cantidad("dos")).isNull();
        assertThat(LectorSheet.cantidad("99999999999")).isNull();
    }

    // --- estado ---

    @Test
    void dicenIngresada_reconoceLasVariantes() {
        assertThat(LectorSheet.dicenIngresada("INGRESADA")).isTrue();
        assertThat(LectorSheet.dicenIngresada(" ingresada ")).isTrue();
        assertThat(LectorSheet.dicenIngresada("Ingresado")).isTrue();
    }

    @Test
    void dicenIngresada_cualquierOtroValorEsTransito() {
        assertThat(LectorSheet.dicenIngresada("EN TRÁNSITO")).isFalse();
        assertThat(LectorSheet.dicenIngresada("")).isFalse();
        assertThat(LectorSheet.dicenIngresada(null)).isFalse();
    }

    // --- texto ---

    @Test
    void codigo_vacio_esImportado() {
        assertThat(LectorSheet.codigo(null)).isEqualTo("IMPORTADOS");
        assertThat(LectorSheet.codigo("   ")).isEqualTo("IMPORTADOS");
        assertThat(LectorSheet.codigo(" 272005 ")).isEqualTo("272005");
    }

    /**
     * La planilla no deja la celda vacia: escribe la palabra. En el primer envio real fueron
     * 76 de 228 lineas, que quedaban con un codigo inexistente y fuera del boton Importados.
     */
    @Test
    void codigo_conLaPalabraImportado_tambienEsImportado() {
        assertThat(LectorSheet.codigo("Importado")).isEqualTo("IMPORTADOS");
        assertThat(LectorSheet.codigo("IMPORTADO")).isEqualTo("IMPORTADOS");
        assertThat(LectorSheet.codigo(" importados ")).isEqualTo("IMPORTADOS");
    }

    /** Un codigo real que empiece con esa palabra no se toca: solo cuenta la palabra sola. */
    @Test
    void codigo_queEmpiezaConImportado_seRespeta() {
        assertThat(LectorSheet.codigo("IMPORTADO-4512")).isEqualTo("IMPORTADO-4512");
        assertThat(LectorSheet.codigo("IMPORTADO ESPECIAL")).isEqualTo("IMPORTADO ESPECIAL");
    }

    @Test
    void limpio_recortaYDescartaVacios() {
        assertThat(LectorSheet.limpio(null)).isNull();
        assertThat(LectorSheet.limpio("  ")).isNull();
        assertThat(LectorSheet.limpio(" CONTROLO: ")).isEqualTo("CONTROLO:");
    }

    @Test
    void normalizar_ignoraTildesEspaciosYMayusculas() {
        assertThat(LectorSheet.normalizar("  Autopartes   del Súr ")).isEqualTo("AUTOPARTES DEL SUR");
    }
}
