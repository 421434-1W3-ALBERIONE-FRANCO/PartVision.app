package com.partvision.compras;

import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.domain.ProductoEstado;
import com.partvision.catalog.repository.ProductoRepository;
import com.partvision.compras.domain.Compra;
import com.partvision.compras.dto.CompraResponse;
import com.partvision.compras.dto.RecepcionCompraRequest;
import com.partvision.compras.dto.RecepcionLineaRequest;
import com.partvision.compras.repository.CompraRepository;
import com.partvision.inventory.repository.StockRepository;
import com.partvision.inventory.service.StockService;
import com.partvision.location.service.UbicacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Un mismo SKU esta cargado una vez por proveedor (~10.300 repetidos entre EGSA y Autopartes
 * del Sur). La factura dice de que proveedor viene, y eso decide a que producto va el stock.
 * Ante la duda la linea queda sin producto: una persona la resuelve en el panel.
 */
@ExtendWith(MockitoExtension.class)
class CompraServiceProveedorTest {

    @Mock private CompraRepository compraRepo;
    @Mock private ProductoRepository productoRepo;
    @Mock private StockService stockService;
    @Mock private StockRepository stockRepository;
    @Mock private UbicacionService ubicacionService;

    private CompraService service;

    @BeforeEach
    void setUp() {
        service = new CompraService(compraRepo, productoRepo, stockService, stockRepository, ubicacionService);
        when(compraRepo.findByNumeroFactura(any())).thenReturn(Optional.empty());
        when(compraRepo.save(any(Compra.class))).thenAnswer(inv -> {
            Compra c = inv.getArgument(0);
            c.setId(1L);
            c.setCreatedAt(Instant.now());
            return c;
        });
    }

    private static Producto producto(Long id, String sku, String proveedor) {
        Producto p = new Producto();
        p.setId(id);
        p.setSku(sku);
        p.setDescripcion("Producto " + sku);
        p.setEstado(ProductoEstado.ACTIVO);
        p.setProveedor(proveedor);
        return p;
    }

    private CompraResponse recibir(String proveedor, String sku) {
        return service.registrarRecepcion(new RecepcionCompraRequest(
                "A-0001-00000001", "2026-09-17", proveedor, "PENDIENTE",
                List.of(new RecepcionLineaRequest(sku, "desc", 4))));
    }

    /** El caso real: 140000 existe para EGSA (#245649) y para ADS (#279402). */
    @Test
    void skuDeDosProveedores_vaAlDeLaFactura() {
        when(productoRepo.findBySkuIn(any())).thenReturn(List.of(
                producto(279402L, "140000", "Autopartes del Sur"),
                producto(245649L, "140000", "EGSA")));

        CompraResponse resp = recibir("EGSA", "140000");

        assertThat(resp.lineas().get(0).productoId()).isEqualTo(245649L);
    }

    @Test
    void elProveedorSeComparaSinMayusculasNiEspacios() {
        when(productoRepo.findBySkuIn(any())).thenReturn(List.of(
                producto(279402L, "140000", "Autopartes del Sur"),
                producto(245649L, "140000", "EGSA")));

        CompraResponse resp = recibir("  autopartes DEL sur ", "140000");

        assertThat(resp.lineas().get(0).productoId()).isEqualTo(279402L);
    }

    /**
     * Si el proveedor de la factura no coincide con ninguno ("ADS" en vez del nombre del
     * catalogo), no se adivina: antes el stock iba al primero que devolviera la base.
     */
    @Test
    void proveedorQueNoCoincide_dejaLaLineaSinProducto() {
        when(productoRepo.findBySkuIn(any())).thenReturn(List.of(
                producto(279402L, "140000", "Autopartes del Sur"),
                producto(245649L, "140000", "EGSA")));

        CompraResponse resp = recibir("ADS", "140000");

        assertThat(resp.lineas().get(0).productoId()).isNull();
        assertThat(resp.lineasMatcheadas()).isZero();
    }

    @Test
    void facturaSinProveedor_conSkuRepetido_dejaLaLineaSinProducto() {
        when(productoRepo.findBySkuIn(any())).thenReturn(List.of(
                producto(279402L, "140000", "Autopartes del Sur"),
                producto(245649L, "140000", "EGSA")));

        assertThat(recibir(null, "140000").lineas().get(0).productoId()).isNull();
    }

    @Test
    void facturaConProveedorEnBlanco_conSkuRepetido_dejaLaLineaSinProducto() {
        when(productoRepo.findBySkuIn(any())).thenReturn(List.of(
                producto(279402L, "140000", "Autopartes del Sur"),
                producto(245649L, "140000", "EGSA")));

        assertThat(recibir("   ", "140000").lineas().get(0).productoId()).isNull();
    }

    /** Dos productos del mismo proveedor con el mismo SKU: ambiguo aun sabiendo el proveedor. */
    @Test
    void dosDelMismoProveedor_dejaLaLineaSinProducto() {
        when(productoRepo.findBySkuIn(any())).thenReturn(List.of(
                producto(1L, "140000", "EGSA"),
                producto(2L, "140000", "egsa")));

        assertThat(recibir("EGSA", "140000").lineas().get(0).productoId()).isNull();
    }

    @Test
    void candidatosSinProveedorCargado_dejaLaLineaSinProducto() {
        when(productoRepo.findBySkuIn(any())).thenReturn(List.of(
                producto(1L, "140000", null),
                producto(2L, "140000", null)));

        assertThat(recibir("EGSA", "140000").lineas().get(0).productoId()).isNull();
    }

    /**
     * Un unico candidato se acepta aunque sea de otro proveedor: es la unica ficha de esa
     * pieza en el catalogo. Es el mismo criterio que usa la importacion de precios.
     */
    @Test
    void unSoloCandidato_seUsaAunqueSeaDeOtroProveedor() {
        when(productoRepo.findBySkuIn(any())).thenReturn(List.of(producto(279402L, "140000", "Autopartes del Sur")));

        assertThat(recibir("EGSA", "140000").lineas().get(0).productoId()).isEqualTo(279402L);
    }

    /** El SKU se compara sin distinguir mayusculas: sku-x y SKU-X son el mismo codigo. */
    @Test
    void skuEnOtraCaja_encuentraAlProductoDelProveedor() {
        when(productoRepo.findBySkuIn(any())).thenReturn(List.of(
                producto(1L, "SKU-X", "Autopartes del Sur"),
                producto(2L, "sku-x", "EGSA")));

        assertThat(recibir("EGSA", "sku-x").lineas().get(0).productoId()).isEqualTo(2L);
    }

    @Test
    void skuInexistente_quedaSinProducto() {
        when(productoRepo.findBySkuIn(any())).thenReturn(List.of());

        assertThat(recibir("EGSA", "NO-EXISTE").lineas().get(0).productoId()).isNull();
    }
}
