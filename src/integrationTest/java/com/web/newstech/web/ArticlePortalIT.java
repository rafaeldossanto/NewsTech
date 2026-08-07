package com.web.newstech.web;

import com.web.newstech.TestcontainersConfiguration;
import com.web.newstech.authoring.Article;
import com.web.newstech.authoring.ArticleService;
import com.web.newstech.authoring.User;
import com.web.newstech.authoring.enums.Role;
import com.web.newstech.authoring.repository.ArticleRepository;
import com.web.newstech.authoring.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ArticlePortalIT {

	private static final String TITULO_QUARENTENA = "Artigo de conta recem-criada";
	private static final String TITULO_LIBERADO = "Artigo de autor estabelecido";

	private MockMvc mockMvc;

	@Autowired
	private ArticleService articleService;

	@Autowired
	private ArticleRepository articleRepository;

	@Autowired
	private UserRepository userRepository;

	private User novato;

	@BeforeEach
	void preparar(WebApplicationContext context) {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		articleRepository.deleteAll();

		// Preserva o admin, que o bootstrap cria uma vez por contexto e nao recria.
		userRepository.findAll().stream()
				.filter(user -> !user.hasRole(Role.ADMIN))
				.forEach(userRepository::delete);

		novato = criarAutor("novato");
	}

	@Test
	@DisplayName("artigo de conta nova aparece em /artigos mas não na home")
	void quarentenaNaoAlcancaAHome() throws Exception {
		articleService.publish(novato, TITULO_QUARENTENA, "Subtítulo", "Texto do artigo.", List.of("ia"));

		mockMvc.perform(get("/artigos"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString(TITULO_QUARENTENA)));

		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString(TITULO_QUARENTENA))));

		mockMvc.perform(get("/ia"))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString(TITULO_QUARENTENA))));
	}

	@Test
	@DisplayName("artigo de autor fora da quarentena entra no radar e no tópico")
	void artigoLiberadoEntraNoFluxo() throws Exception {
		User veterano = criarAutor("veterano");
		veterano.setPublishedCount(5);
		userRepository.save(veterano);

		articleService.publish(veterano, TITULO_LIBERADO, "Subtítulo", "Texto.", List.of("ia"));

		mockMvc.perform(get("/"))
				.andExpect(content().string(containsString(TITULO_LIBERADO)))
				.andExpect(content().string(containsString("badge-article")));

		mockMvc.perform(get("/ia"))
				.andExpect(content().string(containsString(TITULO_LIBERADO)));
	}

	@Test
	@DisplayName("artigo recente não desloca a manchete da curadoria")
	void artigoNaoOcupaManchete() throws Exception {
		User veterano = criarAutor("veterano-2");
		veterano.setPublishedCount(5);
		userRepository.save(veterano);

		// Publicado agora; a manchete do seed tem horas de vida.
		articleService.publish(veterano, "Artigo publicado agora mesmo", null, "Texto.", List.of("ia"));

		String home = mockMvc.perform(get("/")).andReturn().getResponse().getContentAsString();

		int posicaoManchete = home.indexOf("lead-story");
		int posicaoArtigo = home.indexOf("Artigo publicado agora mesmo");

		org.assertj.core.api.Assertions.assertThat(posicaoManchete)
				.as("a manchete vem da curadoria e aparece antes, mesmo com artigo mais novo")
				.isLessThan(posicaoArtigo);
	}

	@Test
	@DisplayName("a página do artigo renderiza o Markdown")
	void renderizaMarkdown() throws Exception {
		Article article = articleService.publish(novato, "Artigo com formatação", null, """
				## Um subtítulo

				Texto com **negrito** e [link](https://exemplo.test).

				- item da lista
				""", List.of());

		mockMvc.perform(get("/artigo/" + article.getSlug()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("<h2>Um subtítulo</h2>")))
				.andExpect(content().string(containsString("<strong>negrito</strong>")))
				.andExpect(content().string(containsString("<li>item da lista</li>")))
				.andExpect(content().string(containsString("artigo assinado")));
	}

	@Test
	@DisplayName("hub do autor lista os artigos dele")
	void hubDoAutor() throws Exception {
		articleService.publish(novato, "Artigo do novato", null, "Texto.", List.of());

		mockMvc.perform(get("/autor/" + novato.getUsername()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Artigo do novato")));

		mockMvc.perform(get("/autor/ninguem-com-esse-nome")).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("anônimo não escreve")
	void anonimoNaoEscreve() throws Exception {
		mockMvc.perform(get("/escrever")).andExpect(status().is3xxRedirection());
		mockMvc.perform(post("/escrever").with(csrf())).andExpect(status().is3xxRedirection());
	}

	@Test
	@WithMockUser(username = "outro-autor", roles = "AUTHOR")
	@DisplayName("autor não edita artigo de outro autor")
	void naoEditaArtigoAlheio() throws Exception {
		criarAutor("outro-autor");
		Article doNovato = articleService.publish(novato, "Artigo alheio", null, "Texto.", List.of());

		mockMvc.perform(get("/escrever/" + doNovato.getSlug()))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("artigo inexistente é 404")
	void artigoInexistente() throws Exception {
		mockMvc.perform(get("/artigo/nao-existe")).andExpect(status().isNotFound());
	}

	private User criarAutor(String prefixo) {
		return userRepository.save(User.builder()
				.username(prefixo)
				.email(UUID.randomUUID() + "@exemplo.test")
				.passwordHash("irrelevante")
				.createdAt(Instant.now().minus(Duration.ofDays(1)))
				.build());
	}

}
