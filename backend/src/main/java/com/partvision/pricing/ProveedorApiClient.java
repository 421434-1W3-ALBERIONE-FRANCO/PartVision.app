package com.partvision.pricing;

import com.partvision.pricing.dto.ProveedorCatalogoResponse;
import com.partvision.pricing.dto.ProveedorLoginResponse;
import com.partvision.pricing.dto.ProveedorProducto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class ProveedorApiClient {

    private static final String CATALOGO_FIELDS =
            "Codigo,Titulo,Marca,PrecioListaSinIVA,PrecioCostoMostradorSinIVA,PrecioCostoMostradorConIVA,TasaIVA";

    private final RestClient restClient;
    private final ProveedorProperties props;

    public ProveedorApiClient(ProveedorProperties props) {
        this.props = props;
        this.restClient = RestClient.builder()
                .baseUrl(props.url())
                .build();
    }

    public String login() {
        ProveedorLoginResponse response = restClient.post()
                .uri("/auth/login/")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", props.username(), "password", props.password()))
                .retrieve()
                .body(ProveedorLoginResponse.class);

        if (response == null || response.data() == null || response.data().token() == null) {
            throw new IllegalStateException("No se pudo autenticar con el proveedor");
        }
        log.info("Autenticación exitosa con proveedor {}", props.url());
        return response.data().token();
    }

    public Optional<ProveedorProducto> buscarPorCodigo(String token, String codigo) {
        ProveedorCatalogoResponse response = restClient.get()
                .uri(uri -> uri.path("/api/catalogo/")
                        .queryParam("fields", CATALOGO_FIELDS)
                        .queryParam("query", codigo)
                        .queryParam("page", 0)
                        .queryParam("pageSize", 10)
                        .build())
                .header("Authorization", "Token " + token)
                .retrieve()
                .body(ProveedorCatalogoResponse.class);

        if (response == null || response.data() == null || response.data().productos() == null) {
            return Optional.empty();
        }

        return response.data().productos().stream()
                .filter(p -> p.codigo() != null && p.codigo().equalsIgnoreCase(codigo))
                .findFirst();
    }

    public List<ProveedorProducto> listarPagina(String token, int page, int pageSize) {
        ProveedorCatalogoResponse response = restClient.get()
                .uri(uri -> uri.path("/api/catalogo/")
                        .queryParam("fields", CATALOGO_FIELDS)
                        .queryParam("page", page)
                        .queryParam("pageSize", pageSize)
                        .build())
                .header("Authorization", "Token " + token)
                .retrieve()
                .body(ProveedorCatalogoResponse.class);

        if (response == null || response.data() == null) {
            return List.of();
        }
        return response.data().productos();
    }
}
