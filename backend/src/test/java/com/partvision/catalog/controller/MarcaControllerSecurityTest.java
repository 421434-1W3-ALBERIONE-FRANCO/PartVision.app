package com.partvision.catalog.controller;

import com.partvision.auth.security.JwtAuthenticationFilter;
import com.partvision.auth.security.JwtService;
import com.partvision.auth.security.RestAuthenticationEntryPoint;
import com.partvision.auth.security.SecurityConfig;
import com.partvision.auth.security.TokenRevocationService;
import com.partvision.catalog.service.MarcaService;
import com.partvision.common.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MarcaController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, RestAuthenticationEntryPoint.class})
@TestPropertySource(properties = {
        "security.jwt.secret=0123456789012345678901234567890123456789012345678901234567890123",
        "security.jwt.expiration-ms=3600000"
})
class MarcaControllerSecurityTest {

    @Autowired
    private MockMvc mvc;
    @MockBean
    private MarcaService marcaService;
    @MockBean
    private TokenRevocationService tokenRevocationService;

    private UsernamePasswordAuthenticationToken auth(String rol) {
        return new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(1L, "u"), null, List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    @Test
    void eliminarMarca_comoAdmin_devuelve204() throws Exception {
        mvc.perform(delete("/api/v1/marcas/5").with(authentication(auth("ADMIN"))))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "OPERARIO")
    void eliminarMarca_comoOperario_devuelve403() throws Exception {
        mvc.perform(delete("/api/v1/marcas/5"))
                .andExpect(status().isForbidden());
    }
}
