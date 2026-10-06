package com.partvision.pricing;

import com.partvision.pricing.dto.SincronizacionResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Donde el robot del cliente (el equipo que corre EGSA CAT) deja la lista de precios de EGSA.
 *
 * <p>Esta ruta es publica (el robot no se loguea), asi que la API key es lo unico que la
 * protege. Falla CERRADA a proposito: sin {@code EGSA_API_KEY} configurada el endpoint se apaga.
 * La clave se revisa ANTES de leer el cuerpo, asi un pedido sin clave no cuesta nada.
 *
 * <p>El archivo va crudo en el cuerpo ({@code Content-Type: application/octet-stream}), que es lo
 * mas facil de mandar desde cualquier lado (PowerShell, Python, Power Automate). Se responde 202
 * con el numero de constancia y el resultado se consulta con {@code GET /{id}}.
 */
@RestController
@RequestMapping("/api/v1/precios/recepcion")
public class PreciosRecepcionController {

    private final RecepcionListaService service;

    @Value("${partvision.precios.egsa.api-key:}")
    private String apiKey;

    @Value("${partvision.precios.egsa.max-bytes:26214400}")
    private long maxBytes;

    public PreciosRecepcionController(RecepcionListaService service) {
        this.service = service;
    }

    @PostMapping("/egsa")
    public ResponseEntity<SincronizacionResponse> recibir(
            @RequestHeader(value = "X-API-Key", required = false) String key,
            @RequestHeader(value = "X-Filename", required = false) String nombre,
            HttpServletRequest request) throws IOException {

        validarApiKey(key);
        if (request.getContentLengthLong() > maxBytes) {
            throw tooLarge();
        }
        byte[] cuerpo;
        try (InputStream in = request.getInputStream()) {
            // Tope aunque no venga el largo (transferencia en trozos): no se lee mas de lo permitido.
            cuerpo = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maxBytes + 1));
        }
        if (cuerpo.length > maxBytes) {
            throw tooLarge();
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.recibir(cuerpo, nombre));
    }

    @GetMapping("/{id}")
    public SincronizacionResponse resultado(
            @RequestHeader(value = "X-API-Key", required = false) String key,
            @PathVariable long id) {
        validarApiKey(key);
        return service.resultado(id);
    }

    private ResponseStatusException tooLarge() {
        return new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                "El archivo pesa más de " + (maxBytes / (1024 * 1024)) + " MB");
    }

    private void validarApiKey(String key) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Recepción de listas de precios no configurada");
        }
        if (key == null || !MessageDigest.isEqual(
                key.getBytes(StandardCharsets.UTF_8), apiKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "API key inválida");
        }
    }
}
