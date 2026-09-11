package com.partvision.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Corta los cuerpos demasiado grandes en las rutas donde se registra.
 *
 * <p>Hace falta porque Spring deserializa y valida el body ANTES de ejecutar el metodo del
 * controller: en un endpoint publico como la recepcion de compras, quien no tiene la API key
 * igual consigue que el servidor construya el grafo de objetos completo antes del 401. Con el
 * heap acotado a 1 GB (limite que comparte VPS con otro proyecto) eso alcanza para tirarlo.
 *
 * <p>Mira el {@code Content-Length} cuando viene, y si no viene (transfer chunked) cuenta los
 * bytes a medida que se leen y corta al pasarse.
 */
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;
    private final ObjectMapper objectMapper;

    public RequestSizeLimitFilter(long maxBytes, ObjectMapper objectMapper) {
        this.maxBytes = maxBytes;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long declarado = request.getContentLengthLong();
        if (declarado > maxBytes) {
            demasiadoGrande(request, response);
            return;
        }
        if (declarado >= 0) {
            filterChain.doFilter(request, response);
            return;
        }
        // Sin Content-Length: se cuenta al leer.
        try {
            filterChain.doFilter(new LimitedRequest(request, maxBytes), response);
        } catch (CuerpoDemasiadoGrandeException ex) {
            if (!response.isCommitted()) {
                demasiadoGrande(request, response);
            }
        }
    }

    private void demasiadoGrande(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.PAYLOAD_TOO_LARGE.value());
        body.put("error", HttpStatus.PAYLOAD_TOO_LARGE.getReasonPhrase());
        body.put("message", "El cuerpo del pedido supera el maximo de " + maxBytes + " bytes.");
        body.put("path", request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }

    /** Se lanza desde el stream, atravesando el resto de la cadena. */
    static class CuerpoDemasiadoGrandeException extends RuntimeException {
        CuerpoDemasiadoGrandeException(long max) {
            super("Cuerpo mayor a " + max + " bytes");
        }
    }

    private static class LimitedRequest extends HttpServletRequestWrapper {
        private final long max;

        LimitedRequest(HttpServletRequest request, long max) {
            super(request);
            this.max = max;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            ServletInputStream delegate = super.getInputStream();
            return new ServletInputStream() {
                private long leidos = 0;

                private int contar(int n) {
                    if (n > 0 && (leidos += n) > max) {
                        throw new CuerpoDemasiadoGrandeException(max);
                    }
                    return n;
                }

                @Override
                public int read() throws IOException {
                    int b = delegate.read();
                    if (b != -1) contar(1);
                    return b;
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    return contar(delegate.read(b, off, len));
                }

                @Override
                public boolean isFinished() {
                    return delegate.isFinished();
                }

                @Override
                public boolean isReady() {
                    return delegate.isReady();
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    delegate.setReadListener(readListener);
                }
            };
        }
    }
}
