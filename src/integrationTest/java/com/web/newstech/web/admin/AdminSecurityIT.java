package com.web.newstech.web.admin;

import com.web.newstech.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O admin liga e desliga fontes e dispara coleta. Se estivesse aberto, qualquer um
 * poderia usar o servidor para bater em terceiros - por isso o acesso e testado, e
 * nao apenas configurado.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AdminSecurityIT {

	private MockMvc mockMvc;

	/**
	 * O MockMvc e montado na mao com {@code springSecurity()} em vez de vir de
	 * {@code @AutoConfigureMockMvc}: no Spring Boot 4 nao ha mais a auto-configuracao
	 * que aplicava esse configurer. Sem ele o filtro de seguranca ate roda - anonimo
	 * leva 302 - mas o usuario de teste nunca chega ao SecurityContext, e todo teste
	 * autenticado falharia parecendo bug de permissao.
	 */
	@BeforeEach
	void setUp(WebApplicationContext context) {
		mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.apply(springSecurity())
				.build();
	}

	@Test
	@DisplayName("admin sem autenticacao e redirecionado para o login")
	void bloqueiaAnonimo() throws Exception {
		mockMvc.perform(get("/admin/sources"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/entrar"));
	}

	@Test
	@DisplayName("acao de estado sem autenticacao tambem e bloqueada")
	void bloqueiaAcaoAnonima() throws Exception {
		mockMvc.perform(post("/admin/sources/qualquer/toggle").with(csrf()))
				.andExpect(status().is3xxRedirection());
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	@DisplayName("autenticado ve a lista de fontes")
	void permiteAdmin() throws Exception {
		mockMvc.perform(get("/admin/sources"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("OpenAI Blog")));
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	@DisplayName("acao de estado sem token CSRF e recusada mesmo autenticado")
	void exigeCsrf() throws Exception {
		mockMvc.perform(post("/admin/sources/qualquer/toggle"))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("health continua publico - o monitoramento nao pode depender de login")
	void healthPublico() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

}
