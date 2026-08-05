package com.web.newstech.web;

import com.web.newstech.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * As páginas públicas renderizadas de verdade, contra o banco com o seed carregado.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PortalIT {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp(WebApplicationContext context) {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	@DisplayName("home é pública e traz manchete, destaques e radar")
	void home() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(view().name("home"))
				.andExpect(content().string(containsString("Claude chega oficialmente ao Brasil")))
				.andExpect(content().string(containsString("Destaques")))
				.andExpect(content().string(containsString("Radar")));
	}

	@Test
	@DisplayName("a peça mostra o crédito com link para a fonte original")
	void story() throws Exception {
		mockMvc.perform(get("/n/dependabot-periodo-de-espera"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Dependabot")))
				.andExpect(content().string(containsString("GitHub Blog")))
				.andExpect(content().string(containsString("github.blog")))
				// Os chips mostram o nome cadastrado, não o slug cru guardado na peça.
				.andExpect(content().string(containsString(">Segurança<")))
				.andExpect(content().string(containsString(">GitHub<")));
	}

	@Test
	@DisplayName("página de tópico filtra pelas peças do tópico")
	void topico() throws Exception {
		mockMvc.perform(get("/seguranca"))
				.andExpect(status().isOk())
				.andExpect(view().name("topic"))
				.andExpect(content().string(containsString("Segurança")));
	}

	@Test
	@DisplayName("hub de empresa lista o que saiu sobre ela")
	void hubDeEmpresa() throws Exception {
		mockMvc.perform(get("/empresa/anthropic"))
				.andExpect(status().isOk())
				.andExpect(view().name("entity"))
				.andExpect(content().string(containsString("Anthropic")))
				.andExpect(content().string(containsString("Claude chega oficialmente ao Brasil")));
	}

	@Test
	@DisplayName("slug desconhecido na raiz vira 404, e não uma página vazia")
	void slugDesconhecido() throws Exception {
		mockMvc.perform(get("/isto-nao-existe")).andExpect(status().isNotFound());
		mockMvc.perform(get("/n/peca-inexistente")).andExpect(status().isNotFound());
		mockMvc.perform(get("/empresa/empresa-inexistente")).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("carregar mais devolve o fragmento de cards, não a página inteira")
	void paginaSeguinte() throws Exception {
		mockMvc.perform(get("/ia/pagina/0"))
				.andExpect(status().isOk())
				// Fragmento puro: sem cabeçalho, sem navegação, pronto para injetar na grade.
				.andExpect(content().string(containsString("class=\"card\"")))
				.andExpect(content().string(org.hamcrest.Matchers.not(containsString("<html"))));
	}

	@Test
	@DisplayName("página além do fim volta vazia, e é assim que o botão sabe que acabou")
	void paginaAlemDoFim() throws Exception {
		String corpo = mockMvc.perform(get("/ia/pagina/99"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(corpo.trim()).isEmpty();
	}

	@Test
	@DisplayName("paginação de tópico inexistente é 404")
	void paginaDeTopicoInexistente() throws Exception {
		mockMvc.perform(get("/nao-existe/pagina/0")).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("o CSS do portal é servido sem autenticação")
	void cssPublico() throws Exception {
		mockMvc.perform(get("/css/newstech.css")).andExpect(status().isOk());
	}

}
