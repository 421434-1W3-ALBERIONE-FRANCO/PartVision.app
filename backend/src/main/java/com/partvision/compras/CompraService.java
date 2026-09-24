package com.partvision.compras;

import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.repository.ProductoRepository;
import com.partvision.common.exception.BusinessException;
import com.partvision.common.exception.DuplicateResourceException;
import com.partvision.compras.ResultadoSincronizacion.Tipo;
import com.partvision.compras.domain.Compra;
import com.partvision.compras.domain.CompraEstado;
import com.partvision.compras.domain.CompraLinea;
import com.partvision.compras.dto.*;
import com.partvision.compras.repository.CompraRepository;
import com.partvision.inventory.domain.Stock;
import com.partvision.inventory.dto.EntradaRequest;
import com.partvision.inventory.dto.SalidaRequest;
import com.partvision.inventory.repository.StockRepository;
import com.partvision.inventory.service.StockService;
import com.partvision.location.domain.Ubicacion;
import com.partvision.location.service.UbicacionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompraService {

    /** Separador al comparar lineas: un caracter de control que no aparece en los datos. */
    private static final String SEP = String.valueOf((char) 1);

    private final CompraRepository compraRepo;
    private final ProductoRepository productoRepo;
    private final StockService stockService;
    private final StockRepository stockRepository;
    private final UbicacionService ubicacionService;
    private final ProveedorResolver proveedores;

    /** Una factura por request. Mismo criterio que las filas de la planilla, con errores HTTP. */
    @Transactional
    public CompraResponse registrarRecepcion(RecepcionCompraRequest request) {
        FacturaEntrante factura = new FacturaEntrante(
                request.factura().trim(),
                LectorSheet.fecha(request.fechaFactura()),
                proveedores.resolver(request.proveedor()),
                LectorSheet.dicenIngresada(request.estatus()) ? CompraEstado.POR_UBICAR : CompraEstado.EN_TRANSITO,
                request.lineas().stream()
                        .map(l -> new FacturaEntrante.Linea(
                                LectorSheet.codigo(l.codigo()), LectorSheet.limpio(l.descripcion()), l.cantidad()))
                        .toList());

        ResultadoSincronizacion resultado = sincronizar(factura);
        if (resultado.tipo() == Tipo.CONFLICTO) {
            throw new DuplicateResourceException(resultado.mensaje());
        }
        return CompraResponse.from(resultado.compra(), true);
    }

    /**
     * Registra la factura o la pone al dia con lo que dice la planilla. Se reenvia a menudo
     * (el flujo relee la planilla), asi que recibirla igual no hace nada; lo que cambia es el
     * estado cuando la planilla la pasa a INGRESADA, y el proveedor si antes no lo tenia.
     *
     * <p>Una factura con el mismo numero y otras lineas, fecha o proveedor no se pisa: queda
     * como estaba y se informa el conflicto. Tampoco se vuelve a transito una compra cuyo
     * stock ya se cargo.
     */
    @Transactional
    public ResultadoSincronizacion sincronizar(FacturaEntrante factura) {
        Optional<Compra> existente = compraRepo.findByNumeroFactura(factura.numero());
        if (existente.isEmpty()) {
            Compra compra = crear(factura);
            log.info("Factura {} registrada: {} lineas, estado {}",
                    compra.getNumeroFactura(), compra.getLineas().size(), compra.getEstado());
            return resultado(Tipo.CREADA, compra, "registrada");
        }

        Compra compra = existente.get();
        if (!mismoContenido(compra, factura)) {
            log.warn("Factura {} ya existe con contenido distinto: no se modifica", factura.numero());
            return resultado(Tipo.CONFLICTO, compra,
                    "La factura " + factura.numero() + " ya esta registrada con otro contenido");
        }
        boolean laPlanillaCambio = compra.getEstadoPlanilla() != factura.estado();

        // La planilla se contradice y el stock ya esta cargado: alguien la edito hacia atras
        // despues de que entrara la mercaderia. No se toca nada y se avisa en cada envio, a
        // proposito: queda sin resolver hasta que se arregle la planilla o se revierta aca.
        if (compra.getEstado() == CompraEstado.INGRESADA
                && factura.estado() == CompraEstado.EN_TRANSITO && laPlanillaCambio) {
            return resultado(Tipo.CONFLICTO, compra,
                    "La planilla la volvio a EN TRANSITO pero su stock ya se cargo en PartVision");
        }

        List<String> cambios = new ArrayList<>();
        if (compra.getProveedor() == null && factura.proveedor() != null) {
            // Llego antes de que la planilla tuviera proveedor: se completa. Con el proveedor
            // se pueden resolver los SKU repetidos, salvo que el stock ya se haya cargado.
            compra.setProveedor(factura.proveedor());
            if (compra.getEstado() != CompraEstado.INGRESADA) {
                asignarProductos(compra.getLineas(), factura.proveedor());
            }
            cambios.add("se completo el proveedor");
        }

        if (laPlanillaCambio) {
            compra.setEstadoPlanilla(factura.estado());
        }

        // La planilla pisa el estado solo cuando la planilla CAMBIA. Si no cambio y igual
        // diferimos, es porque alguien lo movio desde el panel: eso se respeta, o el cambio
        // hecho a mano se desharia solo en el siguiente envio, quince minutos despues.
        if (laPlanillaCambio && compra.getEstado() != CompraEstado.INGRESADA
                && compra.getEstado() != factura.estado()) {
            compra.setEstado(factura.estado());
            cambios.add(factura.estado() == CompraEstado.POR_UBICAR
                    ? "llego: queda por ubicar"
                    : "la planilla la volvio a EN TRANSITO");
        }

        if (cambios.isEmpty()) {
            if (laPlanillaCambio) {
                compraRepo.save(compra);   // cambio lo que dice la planilla, aunque no el estado
            }
            return resultado(Tipo.SIN_CAMBIOS, compra, seDiferencia(compra, factura)
                    ? "en el panel esta como %s; la planilla dice %s"
                            .formatted(compra.getEstado(), factura.estado())
                    : "ya estaba registrada");
        }
        compra = compraRepo.save(compra);
        log.info("Factura {} actualizada: {}", compra.getNumeroFactura(), cambios);
        return resultado(Tipo.ACTUALIZADA, compra, String.join("; ", cambios));
    }

    /**
     * Carga el stock de cada linea en la ubicacion elegida en el panel.
     *
     * <p>Lo normal es hacerlo cuando la planilla ya la marco INGRESADA, pero tambien se puede
     * adelantar: si la mercaderia esta en el deposito, obligar a esperar a que el cliente
     * actualice la celda solo retrasa el stock. La planilla no lo pisa despues:
     * solo cambia el estado cuando ella misma cambia de valor.
     */
    @Transactional
    public CompraResponse marcarIngresada(Long compraId, CambiarEstadoRequest request) {
        Compra compra = compraRepo.findWithLineasById(compraId)
                .orElseThrow(() -> new BusinessException("Compra no encontrada"));

        if (compra.getEstado() == CompraEstado.INGRESADA) {
            throw new BusinessException("La compra ya fue marcada como ingresada");
        }
        if (compra.getEstado() == CompraEstado.EN_TRANSITO) {
            log.info("Compra {} se ingresa desde el panel aunque la planilla dice EN TRANSITO",
                    compra.getNumeroFactura());
        }

        Map<Long, Long> ubicacionPorLinea = request.asignaciones().stream()
                .collect(Collectors.toMap(
                        CambiarEstadoRequest.LineaUbicacion::lineaId,
                        CambiarEstadoRequest.LineaUbicacion::ubicacionId
                ));

        Set<Long> ubicacionIds = new HashSet<>(ubicacionPorLinea.values());
        Map<Long, Ubicacion> ubicacionesCache = ubicacionIds.stream()
                .collect(Collectors.toMap(Function.identity(), ubicacionService::getEntity));

        compra.setEstado(CompraEstado.INGRESADA);

        int cargados = 0;
        for (CompraLinea linea : compra.getLineas()) {
            Long ubicacionId = ubicacionPorLinea.get(linea.getId());
            if (ubicacionId == null) continue;

            Ubicacion ubicacion = ubicacionesCache.get(ubicacionId);
            linea.setUbicacionIngreso(ubicacion);

            if (linea.getProducto() == null) continue;

            stockService.registrarEntrada(new EntradaRequest(
                    linea.getProducto().getId(),
                    ubicacionId,
                    linea.getCantidad(),
                    "Compra factura #" + compra.getNumeroFactura()
            ));
            cargados++;
        }

        compraRepo.save(compra);
        log.info("Compra {} marcada INGRESADA: {} líneas con stock cargado (por ubicación individual)",
                compra.getNumeroFactura(), cargados);

        return CompraResponse.from(compra, true);
    }

    /**
     * Cambia el estado a mano desde el panel, sin pasar por la planilla.
     *
     * <p>Volver atras una compra INGRESADA devuelve el stock que habia cargado: se registra la
     * salida de cada linea desde la ubicacion en la que entro, asi el movimiento queda en el
     * historial en vez de desaparecer. Si esa mercaderia ya no esta —se vendio o se movio— la
     * salida falla y no se revierte nada: es preferible a dejar el stock en negativo.
     *
     * <p>El cambio se respeta aunque la planilla siga diciendo otra cosa: solo la pisa un
     * valor NUEVO de la planilla. Si no, el cambio hecho a mano se desharia en el envio
     * siguiente, minutos despues.
     */
    @Transactional
    public CompraResponse cambiarEstado(Long compraId, CompraEstado destino) {
        if (destino == CompraEstado.INGRESADA) {
            throw new BusinessException(
                    "Para ingresar una compra hay que asignarle una ubicacion a cada linea");
        }

        Compra compra = compraRepo.findWithLineasById(compraId)
                .orElseThrow(() -> new BusinessException("Compra no encontrada"));

        if (compra.getEstado() == destino) {
            throw new BusinessException("La compra ya esta en ese estado");
        }

        boolean revertido = compra.getEstado() == CompraEstado.INGRESADA;
        if (revertido) {
            devolverStock(compra);
        }

        compra.setEstado(destino);
        compraRepo.save(compra);

        log.info("Compra {} pasada a {} desde el panel{}", compra.getNumeroFactura(), destino,
                revertido ? " (se devolvio el stock que habia cargado)" : "");
        return CompraResponse.from(compra, true);
    }

    /** Saca de cada ubicacion lo que la compra habia cargado al ingresarse. */
    private void devolverStock(Compra compra) {
        int devueltas = 0;
        for (CompraLinea linea : compra.getLineas()) {
            if (linea.getProducto() == null || linea.getUbicacionIngreso() == null) {
                continue;
            }
            try {
                stockService.registrarSalida(new SalidaRequest(
                        linea.getProducto().getId(),
                        linea.getUbicacionIngreso().getId(),
                        linea.getCantidad(),
                        "Se revirtio el ingreso de la factura #" + compra.getNumeroFactura()
                ));
            } catch (BusinessException noSePuede) {
                throw new BusinessException(
                        "No se puede revertir: el stock de %s en %s ya no esta como quedo al ingresar (%s)"
                                .formatted(linea.getCodigo(), linea.getUbicacionIngreso().getCodigo(),
                                        noSePuede.getMessage()));
            }
            linea.setUbicacionIngreso(null);
            devueltas++;
        }
        log.info("Compra {}: se devolvieron {} lineas al revertir el ingreso",
                compra.getNumeroFactura(), devueltas);
    }

    @Transactional(readOnly = true)
    public Page<CompraResponse> listar(CompraEstado estado, Pageable pageable) {
        Page<Compra> page = estado != null
                ? compraRepo.findByEstadoOrderByCreatedAtDesc(estado, pageable)
                : compraRepo.findAllByOrderByCreatedAtDesc(pageable);

        return page.map(c -> CompraResponse.from(c, false));
    }

    @Transactional(readOnly = true)
    public CompraResponse detalle(Long id) {
        Compra compra = compraRepo.findWithLineasById(id)
                .orElseThrow(() -> new BusinessException("Compra no encontrada"));

        Map<Long, CompraResponse.UbicacionSugerida> stockSugerido = calcularSugerencias(compra);
        return CompraResponse.from(compra, true, stockSugerido);
    }

    private Compra crear(FacturaEntrante factura) {
        Compra compra = new Compra();
        compra.setNumeroFactura(factura.numero());
        compra.setFechaFactura(factura.fecha());
        compra.setProveedor(factura.proveedor());
        compra.setEstado(factura.estado());
        compra.setEstadoPlanilla(factura.estado());
        for (FacturaEntrante.Linea l : factura.lineas()) {
            CompraLinea linea = new CompraLinea();
            linea.setCodigo(l.codigo());
            linea.setDescripcion(l.descripcion());
            linea.setCantidad(l.cantidad());
            compra.addLinea(linea);
        }
        asignarProductos(compra.getLineas(), factura.proveedor());
        return compraRepo.save(compra);
    }

    /**
     * Busca el producto de las lineas que todavia no lo tienen. Las que ya lo tienen no se
     * tocan: puede ser un importado que alguien asocio a mano, y el codigo IMPORTADOS no
     * encontraria nada y le borraria el producto.
     */
    private void asignarProductos(List<CompraLinea> lineas, String proveedor) {
        List<CompraLinea> sinProducto = lineas.stream().filter(l -> l.getProducto() == null).toList();
        Set<String> codigos = sinProducto.stream()
                .map(l -> Objects.toString(l.getCodigo(), "").toUpperCase())
                .collect(Collectors.toSet());

        Map<String, List<Producto>> candidatosPorSku = productoRepo.findBySkuIn(codigos).stream()
                .collect(Collectors.groupingBy(p -> p.getSku().toUpperCase()));

        for (CompraLinea linea : sinProducto) {
            String codigo = Objects.toString(linea.getCodigo(), "").toUpperCase();
            linea.setProducto(elegirProducto(candidatosPorSku.getOrDefault(codigo, List.of()), proveedor));
        }
    }

    /** El panel y la planilla no dicen lo mismo: alguien movio el estado a mano. */
    private boolean seDiferencia(Compra compra, FacturaEntrante factura) {
        return compra.getEstado() != factura.estado();
    }

    private ResultadoSincronizacion resultado(Tipo tipo, Compra compra, String mensaje) {
        int matcheadas = (int) compra.getLineas().stream().filter(l -> l.getProducto() != null).count();
        return new ResultadoSincronizacion(tipo, compra, compra.getLineas().size(), matcheadas, mensaje);
    }

    private Map<Long, CompraResponse.UbicacionSugerida> calcularSugerencias(Compra compra) {
        List<Long> productoIds = compra.getLineas().stream()
                .filter(l -> l.getProducto() != null)
                .map(l -> l.getProducto().getId())
                .distinct()
                .toList();

        if (productoIds.isEmpty()) return Map.of();

        Map<Long, List<Stock>> stockPorProducto = stockRepository.findByProductoIdIn(productoIds)
                .stream()
                .filter(s -> s.getCantidad() > 0)
                .collect(Collectors.groupingBy(s -> s.getProducto().getId()));

        Map<Long, CompraResponse.UbicacionSugerida> result = new HashMap<>();
        for (var entry : stockPorProducto.entrySet()) {
            entry.getValue().stream()
                    .max(Comparator.comparingInt(Stock::getCantidad))
                    .ifPresent(best -> result.put(entry.getKey(),
                            new CompraResponse.UbicacionSugerida(
                                    best.getUbicacion().getId(),
                                    best.getUbicacion().getCodigo())));
        }
        return result;
    }

    /**
     * Un mismo SKU puede estar cargado una vez por proveedor (hay ~10.300 repetidos entre
     * EGSA y Autopartes del Sur). Con un solo candidato no hay nada que decidir. Con varios,
     * se queda con el del proveedor de la factura; si ninguno o mas de uno coinciden, la
     * linea queda sin producto: el panel la muestra sin match y la resuelve una persona.
     * Adivinar cargaria el stock en el producto de otro proveedor sin que nadie lo note.
     */
    private static Producto elegirProducto(List<Producto> candidatos, String proveedor) {
        if (candidatos.size() == 1) {
            return candidatos.get(0);
        }
        // Un proveedor en blanco ya llega como null: lo resuelve ProveedorResolver.
        if (candidatos.isEmpty() || proveedor == null) {
            return null;
        }
        List<Producto> delProveedor = candidatos.stream()
                .filter(p -> p.getProveedor() != null
                        && p.getProveedor().trim().equalsIgnoreCase(proveedor.trim()))
                .toList();
        return delProveedor.size() == 1 ? delProveedor.get(0) : null;
    }

    /**
     * Compara una factura guardada contra lo que llega, para distinguir un reenvio de un
     * intento de pisarla. No cuenta el estado (lo cambia la planilla con el tiempo), ni el
     * orden de las lineas, ni un proveedor que falta de alguno de los dos lados.
     */
    private boolean mismoContenido(Compra guardada, FacturaEntrante factura) {
        if (!Objects.equals(guardada.getFechaFactura(), factura.fecha())) return false;
        if (guardada.getProveedor() != null && factura.proveedor() != null
                && !guardada.getProveedor().equalsIgnoreCase(factura.proveedor())) return false;
        if (guardada.getLineas().size() != factura.lineas().size()) return false;

        List<String> deLaBase = guardada.getLineas().stream()
                .map(l -> huellaLinea(l.getCodigo(), l.getDescripcion(), l.getCantidad()))
                .sorted()
                .toList();
        List<String> recibidas = factura.lineas().stream()
                .map(l -> huellaLinea(l.codigo(), l.descripcion(), l.cantidad()))
                .sorted()
                .toList();
        return deLaBase.equals(recibidas);
    }

    private String huellaLinea(String codigo, String descripcion, int cantidad) {
        return (codigo == null ? "" : codigo.toUpperCase())
                + SEP + (descripcion == null ? "" : descripcion.trim())
                + SEP + cantidad;
    }
}
