package com.partvision.pricing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Habla con el portal de Autopartes del Sur (hecho por Paradigma). Lo que se sabe de su API
 * sale del codigo publico del sitio:
 * <ul>
 *   <li>{@code POST auth/login/} con {@code {username, password}} responde
 *       {@code {meta: {allowed}, data: {token}}} o el motivo en {@code errors.error} / {@code detail}.</li>
 *   <li>{@code GET api/catalogo/generarXLS/} con {@code Authorization: Token <token>} devuelve el
 *       Excel "Catalogo Autopartes del Sur - DD-MM-YYYY.xlsx" del boton "Lista de precios":
 *       Codigo, Descripcion y Precio de Lista. Al 2026-10-01 llega igual con o sin sesion; se
 *       pide con sesion por si ADS algun dia lo personaliza por cuenta.</li>
 * </ul>
 * Nunca llama a {@code auth/logoff/}: el portal guarda ahi el carrito del cliente.
 */
@Slf4j
@Component
public class AdsPortalClient {

    static final String RUTA_LOGIN = "auth/login/";
    static final String RUTA_LISTA = "api/catalogo/generarXLS/";
    private static final String USER_AGENT = "PartVision/1.0 (actualizacion de precios)";

    private final AdsSyncProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http;

    /** Se reusa entre corridas: se vuelve a iniciar sesion solo si el portal lo rechaza. */
    private volatile String token;

    @Autowired
    public AdsPortalClient(AdsSyncProperties props, ObjectMapper mapper) {
        this(props, mapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(props.timeoutConexionSegundos()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    AdsPortalClient(AdsSyncProperties props, ObjectMapper mapper, HttpClient http) {
        this.props = props;
        this.mapper = mapper;
        this.http = http;
    }

    /** Un problema con el portal, con un mensaje que se le puede mostrar al cliente tal cual. */
    public static class AdsPortalException extends Exception {
        public AdsPortalException(String mensaje) {
            super(mensaje);
        }

        public AdsPortalException(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }

    /** El Excel de la lista de precios, pedido con la sesion del cliente. */
    public byte[] descargarListaDelCliente() throws AdsPortalException {
        if (!props.habilitada()) {
            throw new AdsPortalException("Falta configurar el usuario y la contraseña del portal de ADS en el servidor.");
        }
        if (token == null) token = iniciarSesion();
        HttpResponse<InputStream> resp = pedirLista(token);
        if (resp.statusCode() == 401 || resp.statusCode() == 403) {
            cerrar(resp);
            log.info("ADS rechazo la sesion guardada ({}); se inicia sesion de nuevo", resp.statusCode());
            token = iniciarSesion();
            resp = pedirLista(token);
            if (resp.statusCode() == 401 || resp.statusCode() == 403) {
                cerrar(resp);
                token = null;
                throw new AdsPortalException("ADS no dejó bajar la lista aun después de iniciar sesión (HTTP "
                        + resp.statusCode() + ").");
            }
        }
        return leerExcel(resp);
    }

    private String iniciarSesion() throws AdsPortalException {
        String cuerpo;
        try {
            cuerpo = mapper.writeValueAsString(Map.of("username", props.usuario().trim(), "password", props.password()));
        } catch (IOException e) {
            throw new AdsPortalException("No se pudo armar el pedido de inicio de sesión.", e);
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(props.base() + RUTA_LOGIN))
                .timeout(Duration.ofSeconds(props.timeoutConexionSegundos() * 3L))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .build();

        HttpResponse<String> resp = enviar(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8), "iniciar sesión");
        JsonNode json;
        try {
            json = mapper.readTree(resp.body());
        } catch (IOException e) {
            json = null;
        }
        if (json == null || !json.isObject()) {
            throw new AdsPortalException("ADS respondió algo inesperado al iniciar sesión (HTTP "
                    + resp.statusCode() + "): " + recortar(resp.body()));
        }
        String nuevo = json.path("data").path("token").asText("");
        if (json.path("meta").path("allowed").asBoolean(false) && !nuevo.isBlank()) {
            log.info("Sesion iniciada en el portal de ADS");
            return nuevo;
        }
        String motivo = json.path("errors").path("error").asText("");
        if (motivo.isBlank()) motivo = json.path("detail").asText("");
        throw new AdsPortalException("ADS rechazó el usuario o la contraseña"
                + (motivo.isBlank() ? "" : ": " + recortar(motivo)) + ". Revisá que sigan siendo los del portal.");
    }

    private HttpResponse<InputStream> pedirLista(String conToken) throws AdsPortalException {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(props.base() + RUTA_LISTA))
                .timeout(Duration.ofSeconds(props.timeoutDescargaSegundos()))
                .header("User-Agent", USER_AGENT)
                .GET();
        if (conToken != null) req.header("Authorization", "Token " + conToken);
        return enviar(req.build(), HttpResponse.BodyHandlers.ofInputStream(), "bajar la lista de precios");
    }

    private <T> HttpResponse<T> enviar(HttpRequest req, HttpResponse.BodyHandler<T> handler, String accion)
            throws AdsPortalException {
        try {
            return http.send(req, handler);
        } catch (HttpTimeoutException e) {
            throw new AdsPortalException("ADS no respondió a tiempo al " + accion + ".", e);
        } catch (IOException e) {
            throw new AdsPortalException("No se pudo conectar con el portal de ADS para " + accion
                    + " (" + e.getClass().getSimpleName() + ").", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AdsPortalException("Se interrumpió la conexión con ADS.", e);
        }
    }

    /** Solo se acepta un Excel (xlsx = zip, empieza con "PK") y de un tamaño razonable. */
    private byte[] leerExcel(HttpResponse<InputStream> resp) throws AdsPortalException {
        byte[] cuerpo;
        try (InputStream in = resp.body()) {
            cuerpo = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, props.maxBytes() + 1));
        } catch (IOException e) {
            throw new AdsPortalException("Se cortó la descarga de la lista de ADS.", e);
        }
        if (resp.statusCode() != 200) {
            throw new AdsPortalException("ADS respondió con error al pedir la lista (HTTP " + resp.statusCode()
                    + "): " + recortar(new String(cuerpo, StandardCharsets.UTF_8)));
        }
        if (cuerpo.length > props.maxBytes()) {
            throw new AdsPortalException("La lista de ADS pesa más de " + (props.maxBytes() / (1024 * 1024))
                    + " MB: no se procesa por las dudas.");
        }
        if (cuerpo.length < 4 || cuerpo[0] != 'P' || cuerpo[1] != 'K') {
            throw new AdsPortalException("ADS no devolvió un Excel. Respondió: "
                    + recortar(new String(cuerpo, StandardCharsets.UTF_8)));
        }
        return cuerpo;
    }

    private static void cerrar(HttpResponse<InputStream> resp) {
        try {
            resp.body().close();
        } catch (IOException ignored) {
            // nada que hacer: la respuesta se descarta igual
        }
    }

    /** Lo que responda el portal va a la pantalla: corto y sin caracteres de control. */
    static String recortar(String texto) {
        if (texto == null || texto.isBlank()) return "(vacío)";
        String limpio = texto.replaceAll("[\\p{Cntrl}]+", " ").replaceAll("\\s+", " ").trim();
        return limpio.length() > 160 ? limpio.substring(0, 160) + "…" : limpio;
    }
}
