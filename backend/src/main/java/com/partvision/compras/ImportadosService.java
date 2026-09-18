package com.partvision.compras;

import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.domain.ProductoEstado;
import com.partvision.catalog.dto.ProductoRequest;
import com.partvision.catalog.dto.ProductoResponse;
import com.partvision.catalog.repository.ProductoRepository;
import com.partvision.catalog.service.ProductoService;
import com.partvision.common.exception.BusinessException;
import com.partvision.common.exception.DuplicateResourceException;
import com.partvision.common.exception.ResourceNotFoundException;
import com.partvision.compras.domain.Compra;
import com.partvision.compras.domain.CompraEstado;
import com.partvision.compras.domain.CompraLinea;
import com.partvision.compras.dto.AltaImportadoRequest;
import com.partvision.compras.dto.ImportadoPendienteResponse;
import com.partvision.compras.dto.ImportadoResueltoResponse;
import com.partvision.compras.dto.VincularImportadoRequest;
import com.partvision.compras.repository.CompraLineaRepository;
import com.partvision.inventory.dto.EntradaRequest;
import com.partvision.inventory.service.StockService;
import com.partvision.location.domain.Ubicacion;
import com.partvision.location.service.UbicacionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Las lineas que llegan sin codigo en la planilla son pedidos puntuales de clientes: entran
 * como IMPORTADOS, sin producto y sin stock. Si el cliente cree que la pieza se va a volver a
 * pedir, desde aca se la da de alta en el catalogo con un SKU propio, o se la asocia a una que
 * ya se dio de alta antes (la misma pieza pedida otra vez vuelve a llegar sin codigo).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportadosService {

    /** Ningun SKU de proveedor empieza asi (verificado sobre el catalogo al crearlo). */
    static final String PREFIJO_SKU = "IMP-";
    private static final Pattern NUMERO = Pattern.compile("\\d{1,9}");

    private final CompraLineaRepository lineaRepo;
    private final ProductoRepository productoRepo;
    private final ProductoService productoService;
    private final StockService stockService;
    private final UbicacionService ubicacionService;

    @Transactional(readOnly = true)
    public Page<ImportadoPendienteResponse> listarPendientes(Pageable pageable) {
        return lineaRepo.findSinProductoPorCodigo(LectorSheet.CODIGO_IMPORTADO, pageable)
                .map(ImportadoPendienteResponse::from);
    }

    /**
     * El siguiente {@code IMP-NNNNN} libre. Numera a partir del mayor que exista, y si aun asi
     * el numero ya esta tomado (otra persona dando de alta al mismo tiempo) pasa al siguiente.
     */
    @Transactional(readOnly = true)
    public String proponerSku() {
        int mayor = productoRepo.findSkusConPrefijo(PREFIJO_SKU).stream()
                .map(sku -> sku.substring(PREFIJO_SKU.length()))
                .filter(numero -> NUMERO.matcher(numero).matches())
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0);
        int siguiente = mayor;
        String sku;
        do {
            siguiente++;
            sku = String.format("%s%05d", PREFIJO_SKU, siguiente);
        } while (productoRepo.existsBySkuIgnoreCase(sku));
        return sku;
    }

    /**
     * Crea el producto y le asocia la linea. El SKU tiene que ser unico en todo el catalogo,
     * no solo dentro de una marca: es un codigo inventado y no puede confundirse con ningun otro.
     */
    @Transactional
    public ImportadoResueltoResponse darDeAlta(Long lineaId, AltaImportadoRequest request) {
        CompraLinea linea = pendiente(lineaId);
        Compra compra = linea.getCompra();
        exigirUbicacionSiYaIngreso(compra, request.ubicacionId());

        String sku = request.sku().trim().toUpperCase(Locale.ROOT);
        if (productoRepo.existsBySkuIgnoreCase(sku)) {
            throw new DuplicateResourceException(
                    "El SKU " + sku + " ya existe en el catalogo. Libre: " + proponerSku());
        }

        ProductoResponse creado = productoService.create(new ProductoRequest(
                sku, null, null, null, request.descripcion().trim(), ProductoEstado.ACTIVO,
                null, null, compra.getProveedor()));
        Producto producto = productoService.getEntity(creado.id());
        log.info("Importado de la factura {} dado de alta como {}", compra.getNumeroFactura(), sku);
        return resolver(linea, producto, request.ubicacionId(), "Dado de alta como " + sku);
    }

    /** La misma pieza pedida otra vez: se asocia al producto que ya se habia dado de alta. */
    @Transactional
    public ImportadoResueltoResponse vincular(Long lineaId, VincularImportadoRequest request) {
        CompraLinea linea = pendiente(lineaId);
        exigirUbicacionSiYaIngreso(linea.getCompra(), request.ubicacionId());
        Producto producto = productoService.getEntity(request.productoId());
        return resolver(linea, producto, request.ubicacionId(), "Asociado a " + producto.getSku());
    }

    private CompraLinea pendiente(Long lineaId) {
        CompraLinea linea = lineaRepo.findById(lineaId)
                .orElseThrow(() -> new ResourceNotFoundException("Linea de compra no encontrada: " + lineaId));
        if (!LectorSheet.CODIGO_IMPORTADO.equals(linea.getCodigo())) {
            throw new BusinessException("La linea no es un importado: tiene el codigo " + linea.getCodigo());
        }
        if (linea.getProducto() != null) {
            throw new BusinessException("La linea ya esta asociada a un producto");
        }
        return linea;
    }

    private static void exigirUbicacionSiYaIngreso(Compra compra, Long ubicacionId) {
        if (compra.getEstado() == CompraEstado.INGRESADA && ubicacionId == null) {
            throw new BusinessException("La compra ya ingreso: elegi la ubicacion para cargar el stock");
        }
    }

    /**
     * Si la compra ya ingreso, esta linea quedo afuera del ingreso porque no tenia producto: su
     * stock se carga ahora. Si todavia no ingreso, entra con el resto de la compra.
     */
    private ImportadoResueltoResponse resolver(CompraLinea linea, Producto producto, Long ubicacionId,
                                               String accion) {
        linea.setProducto(producto);
        Compra compra = linea.getCompra();
        boolean cargarAhora = compra.getEstado() == CompraEstado.INGRESADA;
        String ubicacionCodigo = null;
        if (cargarAhora) {
            Ubicacion ubicacion = ubicacionService.getEntity(ubicacionId);
            stockService.registrarEntrada(new EntradaRequest(
                    producto.getId(), ubicacionId, linea.getCantidad(),
                    "Compra factura #" + compra.getNumeroFactura() + " (importado)"));
            linea.setUbicacionIngreso(ubicacion);
            ubicacionCodigo = ubicacion.getCodigo();
        }
        lineaRepo.save(linea);

        String mensaje = cargarAhora
                ? accion + ": se cargaron " + linea.getCantidad() + " en " + ubicacionCodigo
                : accion + ": el stock entra cuando se ingrese la compra";
        return new ImportadoResueltoResponse(linea.getId(), producto.getId(), producto.getSku(),
                producto.getDescripcion(), cargarAhora, ubicacionCodigo, mensaje);
    }
}
