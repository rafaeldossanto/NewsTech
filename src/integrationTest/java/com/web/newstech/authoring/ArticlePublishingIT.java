package com.web.newstech.authoring;

import com.web.newstech.TestcontainersConfiguration;
import com.web.newstech.authoring.repository.ArticleRepository;
import com.web.newstech.authoring.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ArticlePublishingIT {

	@Autowired
	private ArticleService articleService;

	@Autowired
	private ArticleRepository articleRepository;

	@Autowired
	private UserRepository userRepository;

	private User autor;

	@BeforeEach
	void preparar() {
		articleRepository.deleteAll();
		autor = userRepository.save(User.builder()
				.username("autor-" + UUID.randomUUID().toString().substring(0, 8))
				.email(UUID.randomUUID() + "@exemplo.test")
				.passwordHash("irrelevante")
				.createdAt(Instant.now())
				.build());
	}

	@Test
	@DisplayName("segundo artigo dentro de 24h é recusado, com prazo na mensagem")
	void limiteDiario() {
		articleService.publish(autor, "Primeiro artigo", null, "Texto do primeiro.", List.of("ia"));

		assertThatThrownBy(() -> articleService.publish(autor, "Segundo artigo", null, "Texto.", List.of()))
				.isInstanceOf(PublishingException.class)
				.hasMessageContaining("Pode publicar de novo em cerca de");

		assertThat(articleRepository.findAll()).hasSize(1);
	}

	@Test
	@DisplayName("passada a janela, o autor volta a publicar")
	void janelaDeslizante() {
		Article primeiro = articleService.publish(autor, "Primeiro artigo", null, "Texto.", List.of());
		primeiro.setPublishedAt(Instant.now().minus(Duration.ofHours(25)));
		articleRepository.save(primeiro);

		assertThatCode(() -> articleService.publish(autor, "Segundo artigo", null, "Texto.", List.of()))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("conta nova publica fora do fluxo até completar a quarentena")
	void quarentenaDeContaNova() {
		Article primeiro = publicarBurlandoOLimite("Artigo um");
		Article segundo = publicarBurlandoOLimite("Artigo dois");

		assertThat(primeiro.isHomeEligible()).isFalse();
		assertThat(segundo.isHomeEligible()).isFalse();

		Article terceiro = publicarBurlandoOLimite("Artigo três");

		assertThat(terceiro.isHomeEligible())
				.as("o terceiro artigo tira a conta da quarentena")
				.isTrue();
		assertThat(articleRepository.findById(primeiro.getId()).orElseThrow().isHomeEligible())
				.as("os anteriores são liberados junto: quarentena não pode virar punição permanente")
				.isTrue();
		assertThat(articleRepository.findById(segundo.getId()).orElseThrow().isHomeEligible()).isTrue();
	}

	@Test
	@DisplayName("editar não muda o slug")
	void edicaoPreservaSlug() {
		Article article = articleService.publish(autor, "Título original", null, "Texto.", List.of());
		String slugOriginal = article.getSlug();

		articleService.update(article, "Título completamente diferente", "Novo subtítulo", "Outro texto.",
				List.of("ia"));

		assertThat(articleRepository.findById(article.getId()).orElseThrow())
				.satisfies(atualizado -> {
					assertThat(atualizado.getSlug())
							.as("url publicada é permanente; mudar quebra link e perde indexação")
							.isEqualTo(slugOriginal);
					assertThat(atualizado.getTitle()).isEqualTo("Título completamente diferente");
					assertThat(atualizado.getUpdatedAt()).isNotNull();
				});
	}

	@Test
	@DisplayName("artigo sem título ou sem texto é recusado")
	void validacao() {
		assertThatThrownBy(() -> articleService.publish(autor, "  ", null, "Texto.", List.of()))
				.hasMessageContaining("título");

		assertThatThrownBy(() -> articleService.publish(autor, "Título", null, "   ", List.of()))
				.hasMessageContaining("texto");
	}

	@Test
	@DisplayName("a assinatura é gravada junto, sem depender de consulta a users depois")
	void assinaturaDesnormalizada() {
		Article article = articleService.publish(autor, "Artigo assinado", null, "Texto.", List.of());

		assertThat(article.getAuthorUsername()).isEqualTo(autor.getUsername());
		assertThat(article.getAuthorId()).isEqualTo(autor.getId());
		assertThat(article.getAuthorDisplayName()).isNotBlank();
	}

	/** O limite diário não é o objeto deste teste; recua a data para poder seguir. */
	private Article publicarBurlandoOLimite(String titulo) {
		Article article = articleService.publish(autor, titulo, null, "Texto.", List.of());
		article.setPublishedAt(Instant.now().minus(Duration.ofHours(30)));
		articleRepository.save(article);
		autor = userRepository.findById(autor.getId()).orElseThrow();
		return article;
	}

}
