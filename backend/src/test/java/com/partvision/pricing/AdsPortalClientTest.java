package com.partvision.pricing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.partvision.pricing.AdsPortalClient.AdsPortalException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Contra un portal falso en localhost: nunca contra el de ADS. */
class AdsPortalClientTest {

    private static final byte[] EXCEL = "PK\u0003\u0004 contenido de un xlsx".getBytes(StandardCharsets.ISO_8859_1);

    private HttpServer server;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Lo que responde el portal falso, en orden, a cada pedido de la lista. */
    private final ConcurrentLinkedQueue<Respuesta> respuestasLista = new ConcurrentLinkedQueue<>();
    private volatile Respuesta respuestaLogin = new Respuesta(200, "{\"meta\":{\"allowed\":true},\"data\":{\"token\":\"tok-1\"}}");
    private final AtomicInteger logins = new AtomicInteger();
    private final List<String> authHeaders = new ArrayList<>();
    private final List<String> cuerposLogin = new ArrayList<>();

    private record Respuesta(int status, byte[] cuerpo) {
        Respuesta(int status, String cuerpo) {
            this(status, cuerpo.getBytes(StandardCharsets.UTF_8));
        }
    }

    @BeforeEach
    void levantarPortalFalso() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth/login/", ex -> {
            logins.incrementAndGet();
            synchronized (cuerposLogin) {
                cuerposLogin.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            }
            responder(ex, respuestaLogin);
        });
        server.createContext("/api/catalogo/generarXLS/", ex -> {
            synchronized (authHeaders) {
                authHeaders.add(ex.getRequestHeaders().getFirst("Authorization"));
            }
            Respuesta r = respuestasLista.poll();
            responder(ex, r != null ? r : new Respuesta(200, EXCEL));
        });
        server.start();
    }

    @AfterEach
    void apagar() {
        server.stop(0);
    }

    private static void responder(HttpExchange ex, Respuesta r) throws IOException {
        ex.sendResponseHeaders(r.status(), r.cuerpo().length == 0 ? -1 : r.cuerpo().length);
        if (r.cuerpo().length > 0) ex.getResponseBody().write(r.cuerpo());
        ex.close();
    }

    private AdsSyncProperties props(String usuario, String password, long maxBytes) {
        return new AdsSyncProperties(usuario, password, "http://127.0.0.1:" + server.getAddress().getPort(),
                "Autopartes del Sur", "Código", "Precio de Lista", 60, 35, 10, 80, 1000, 5, 50, 3, 5, 5, maxBytes);
    }

    private AdsPortalClient cliente() {
        return new AdsPortalClient(props("20111111112", "secreta", 1_000_000), mapper);
    }

    @Test
    void bajaLaListaConElTokenDelLogin() throws Exception {
        byte[] lista = cliente().descargarListaDelCliente();

        assertThat(lista).isEqualTo(EXCEL);
        assertThat(authHeaders).containsExactly("Token tok-1");
        assertThat(cuerposLogin.getFirst()).contains("\"username\":\"20111111112\"").contains("\"password\":\"secreta\"");
    }

    @Test
    void reusaElTokenEntreCorridas() throws Exception {
        AdsPortalClient cliente = cliente();

        cliente.descargarListaDelCliente();
        cliente.descargarListaDelCliente();

        assertThat(logins.get()).isEqualTo(1);
        assertThat(authHeaders).containsExactly("Token tok-1", "Token tok-1");
    }

    @Test
    void siElPortalRechazaElToken_iniciaSesionDeNuevoYReintenta() throws Exception {
        AdsPortalClient cliente = cliente();
        cliente.descargarListaDelCliente();
        respuestaLogin = new Respuesta(200, "{\"meta\":{\"allowed\":true},\"data\":{\"token\":\"tok-2\"}}");
        respuestasLista.add(new Respuesta(401, "{\"detail\":\"Token inválido.\"}"));

        byte[] lista = cliente.descargarListaDelCliente();

        assertThat(lista).isEqualTo(EXCEL);
        assertThat(logins.get()).isEqualTo(2);
        assertThat(authHeaders).containsExactly("Token tok-1", "Token tok-1", "Token tok-2");
    }

    @Test
    void siRechazaAunDespuesDelLogin_fallaYOlvidaElToken() {
        AdsPortalClient cliente = cliente();
        respuestasLista.add(new Respuesta(403, "prohibido"));
        respuestasLista.add(new Respuesta(403, "prohibido"));

        assertThatThrownBy(cliente::descargarListaDelCliente)
                .isInstanceOf(AdsPortalException.class)
                .hasMessageContaining("aun después de iniciar sesión (HTTP 403)");

        // La proxima corrida vuelve a iniciar sesion en vez de reusar el token rechazado.
        assertThat(logins.get()).isEqualTo(2);
    }

    @Test
    void loginRechazado_conElMotivoDelPortal() {
        respuestaLogin = new Respuesta(400, "{\"meta\":{\"allowed\":false},\"errors\":{\"error\":\"Usuario o contraseña incorrectos\"}}");

        assertThatThrownBy(() -> cliente().descargarListaDelCliente())
                .isInstanceOf(AdsPortalException.class)
                .hasMessageContaining("ADS rechazó el usuario o la contraseña: Usuario o contraseña incorrectos");
        assertThat(authHeaders).isEmpty();
    }

    @Test
    void loginRechazado_conDetail() {
        respuestaLogin = new Respuesta(401, "{\"detail\":\"Cuenta bloqueada\"}");

        assertThatThrownBy(() -> cliente().descargarListaDelCliente())
                .hasMessageContaining(": Cuenta bloqueada.");
    }

    @Test
    void loginSinTokenNiMotivo() {
        respuestaLogin = new Respuesta(200, "{\"meta\":{\"allowed\":true},\"data\":{\"token\":\"\"}}");

        assertThatThrownBy(() -> cliente().descargarListaDelCliente())
                .hasMessage("ADS rechazó el usuario o la contraseña. Revisá que sigan siendo los del portal.");
    }

    @Test
    void loginQueNoEsJson() {
        respuestaLogin = new Respuesta(502, "<html>Bad Gateway</html>");

        assertThatThrownBy(() -> cliente().descargarListaDelCliente())
                .hasMessageContaining("algo inesperado al iniciar sesión (HTTP 502): <html>Bad Gateway</html>");
    }

    @Test
    void loginConJsonQueNoEsObjeto() {
        respuestaLogin = new Respuesta(200, "[]");

        assertThatThrownBy(() -> cliente().descargarListaDelCliente())
                .hasMessageContaining("algo inesperado al iniciar sesión (HTTP 200): []");
    }

    @Test
    void sinCredenciales_noLlamaAlPortal() {
        AdsPortalClient cliente = new AdsPortalClient(props("", "", 1000), mapper);

        assertThatThrownBy(cliente::descargarListaDelCliente)
                .hasMessageContaining("Falta configurar el usuario y la contraseña");
        assertThat(logins.get()).isZero();
    }

    @Test
    void errorDelPortalAlPedirLaLista() {
        respuestasLista.add(new Respuesta(500, "Internal Server Error"));

        assertThatThrownBy(() -> cliente().descargarListaDelCliente())
                .hasMessage("ADS respondió con error al pedir la lista (HTTP 500): Internal Server Error");
    }

    @Test
    void respuestaQueNoEsUnExcel() {
        respuestasLista.add(new Respuesta(200, "<html>\n\tMantenimiento programado\n</html>"));

        assertThatThrownBy(() -> cliente().descargarListaDelCliente())
                .hasMessage("ADS no devolvió un Excel. Respondió: <html> Mantenimiento programado </html>");
    }

    @Test
    void respuestaVacia() {
        respuestasLista.add(new Respuesta(200, new byte[0]));

        assertThatThrownBy(() -> cliente().descargarListaDelCliente())
                .hasMessage("ADS no devolvió un Excel. Respondió: (vacío)");
    }

    @Test
    void listaDemasiadoGrande() {
        AdsPortalClient cliente = new AdsPortalClient(props("u", "p", 10), mapper);

        assertThatThrownBy(cliente::descargarListaDelCliente)
                .hasMessageContaining("pesa más de 0 MB");
    }

    @Test
    void portalCaido() {
        AdsPortalClient cliente = new AdsPortalClient(new AdsSyncProperties("u", "p", "http://127.0.0.1:1/",
                "Autopartes del Sur", "Código", "Precio de Lista", 60, 35, 10, 80, 1000, 5, 50, 3, 5, 5, 100), mapper);

        assertThatThrownBy(cliente::descargarListaDelCliente)
                .isInstanceOf(AdsPortalException.class)
                .hasMessageContaining("No se pudo conectar con el portal de ADS para iniciar sesión");
    }

    @Test
    @SuppressWarnings("unchecked")
    void portalQueNoRespondeATiempo() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("timed out"));
        AdsPortalClient cliente = new AdsPortalClient(props("u", "p", 100), mapper, http);

        assertThatThrownBy(cliente::descargarListaDelCliente)
                .hasMessage("ADS no respondió a tiempo al iniciar sesión.");
    }

    @Test
    @SuppressWarnings("unchecked")
    void conexionInterrumpida_restauraLaMarca() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException());
        AdsPortalClient cliente = new AdsPortalClient(props("u", "p", 100), mapper, http);

        assertThatThrownBy(cliente::descargarListaDelCliente).hasMessage("Se interrumpió la conexión con ADS.");
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void descargaQueSeCortaEnElMedio() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<InputStream> resp = mock(HttpResponse.class);
        InputStream roto = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("connection reset");
            }
        };
        when(resp.body()).thenReturn(roto);
        HttpResponse<String> login = mock(HttpResponse.class);
        when(login.body()).thenReturn("{\"meta\":{\"allowed\":true},\"data\":{\"token\":\"t\"}}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn((HttpResponse) login, (HttpResponse) resp);
        AdsPortalClient cliente = new AdsPortalClient(props("u", "p", 100), mapper, http);

        assertThatThrownBy(cliente::descargarListaDelCliente).hasMessage("Se cortó la descarga de la lista de ADS.");
    }

    @Test
    void recortar_textoLargoYVacio() {
        assertThat(AdsPortalClient.recortar(null)).isEqualTo("(vacío)");
        assertThat(AdsPortalClient.recortar("  ")).isEqualTo("(vacío)");
        assertThat(AdsPortalClient.recortar("x".repeat(200))).hasSize(161).endsWith("…");
    }

    @Test
    void props_noMuestranLaContraseña() {
        AdsSyncProperties p = props("20111111112", "secreta", 10);

        assertThat(p.toString()).doesNotContain("secreta").doesNotContain("20111111112").contains("habilitada=true");
        assertThat(p.base()).endsWith("/");
        assertThat(new AdsSyncProperties(null, "x", "http://a/", "p", "c", "p", 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)
                .habilitada()).isFalse();
        assertThat(new AdsSyncProperties("u", null, "http://a/", "p", "c", "p", 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)
                .habilitada()).isFalse();
        assertThat(new AdsSyncProperties("u", null, "http://a/", "p", "c", "p", 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)
                .base()).isEqualTo("http://a/");
    }
}
