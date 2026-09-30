package com.partvision.compras;

import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.domain.ProductoEstado;
import com.partvision.catalog.domain.SkuImportado;
import com.partvision.catalog.dto.ProductoRequest;
import com.partvision.catalog.dto.ProductoResponse;
import com.partvision.catalog.repository.ProductoRepository;
import com.partvision.catalog.service.ProductoService;
import com.partvision.common.exception.BusinessException;
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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * Cuantos numeros de la secuencia se prueban antes de rendirse. Solo se saltea uno si ya
     * existe un producto con ese codigo, que solo puede haber llegado por la carga masiva
     * (que escribe SQL directo); con el prefijo reservado en todos los demas caminos, en la
     * practica el primero sirve siempre.
     */
    private static final int INTENTOS_SKU = 100;

    private final CompraLineaRepository lineaRepo;
    private final ProductoRepository productoRepo;
    private final ProductoService productoService;
    private final StockService stockService;
    private final UbicacionService ubicacionService;

    @Transactional(readOnly = true)
    public Page<ImportadoPendienteResponse> listarPendientes(Pageable pageable) {
        Page<CompraLinea> pagina = lineaRepo.findSinProductoPorCodigo(LectorSheet.CODIGO_IMPORTADO, pageable);

        Set<String> codigos = pagina.getContent().stream()
                .map(l -> codigoEnLaDescripcion(l.getDescripcion()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, List<Producto>> porSku = codigos.isEmpty() ? Map.of()
                : productoRepo.findBySkuIn(codigos).stream()
                        .collect(Collectors.groupingBy(p -> p.getSku().toUpperCase(Locale.ROOT)));

        return pagina.map(linea -> ImportadoPendienteResponse.from(linea, sugerir(linea, porSku)));
    }

    /**
     * El codigo que la planilla escribe al principio de la descripcion. El cliente anota estas
     * piezas a mano, con el codigo adelante y el nombre corto atras: "bie0199 biela",
     * "jt1232 junta tapa om926". Se pide al menos un digito para no confundir la primera
     * palabra de una descripcion comun ("termotato perkins") con un codigo.
     */
    static String codigoEnLaDescripcion(String descripcion) {
        if (descripcion == null) {
            return null;
        }
        String primera = descripcion.trim().split("\\s+", 2)[0].toUpperCase(Locale.ROOT);
        boolean pareceCodigo = primera.length() >= 4 && primera.length() <= 40
                && primera.chars().anyMatch(Character::isDigit)
                && primera.chars().anyMatch(Character::isLetter);
        return pareceCodigo ? primera : null;
    }

    /**
     * Entre los productos con ese SKU gana el del proveedor de la factura. Si ninguno es de ese
     * proveedor igual se muestra uno, marcado como de otro proveedor: sirve para que la persona
     * lo vea, y es justo el caso en el que asociar a ciegas seria un error.
     */
    private ImportadoPendienteResponse.Sugerencia sugerir(CompraLinea linea,
                                                          Map<String, List<Producto>> porSku) {
        String codigo = codigoEnLaDescripcion(linea.getDescripcion());
        if (codigo == null) {
            return null;
        }
        List<Producto> candidatos = porSku.getOrDefault(codigo, List.of());
        if (candidatos.isEmpty()) {
            return null;
        }
        String proveedor = linea.getCompra().getProveedor();
        Producto elegido = candidatos.stream()
                .filter(p -> p.getProveedor() != null && p.getProveedor().equalsIgnoreCase(proveedor))
                .findFirst()
                .orElse(candidatos.get(0));
        return ImportadoPendienteResponse.Sugerencia.from(elegido, proveedor);
    }

    /**
     * El codigo que le va a tocar al proximo importado, para mostrarlo en el panel. No lo
     * reserva: si otra persona da de alta uno antes, el que se asigne va a ser el siguiente, y
     * la respuesta del alta dice cual fue.
     */
    @Transactional(readOnly = true)
    public String proponerSku() {
        return SkuImportado.formatear(productoRepo.proximoNumeroImportado());
    }

    /**
     * Toma el codigo de la secuencia. Lo asigna el sistema y no la persona que da de alta: si
     * viniera del panel, se podria escribir cualquier cosa, o cambiar en el pedido aunque el
     * campo no fuera editable. La secuencia no reparte dos veces el mismo numero aunque dos
     * altas ocurran a la vez, y el indice unico de la base frena cualquier duplicado igual.
     */
    private String asignarSku() {
        for (int i = 0; i < INTENTOS_SKU; i++) {
            String sku = SkuImportado.formatear(productoRepo.siguienteNumeroImportado());
            if (!productoRepo.existsBySkuIgnoreCase(sku)) {
                return sku;
            }
            log.warn("El codigo {} ya estaba tomado por fuera de la secuencia: se saltea", sku);
        }
        throw new IllegalStateException("No se encontro un codigo " + SkuImportado.PREFIJO
                + " libre en " + INTENTOS_SKU + " intentos");
    }

    /**
     * Crea el producto y le asocia la linea. El codigo lo asigna el sistema ({@link #asignarSku}):
     * es unico en todo el catalogo, no solo dentro de una marca, porque es un codigo inventado y
     * no puede confundirse con ningun otro.
     */
    @Transactional
    public ImportadoResueltoResponse darDeAlta(Long lineaId, AltaImportadoRequest request) {
        CompraLinea linea = pendiente(lineaId);
        Compra compra = linea.getCompra();
        exigirUbicacionSiYaIngreso(compra, request.ubicacionId());

        String sku = asignarSku();
        ProductoResponse creado = productoService.crearImportado(new ProductoRequest(
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
