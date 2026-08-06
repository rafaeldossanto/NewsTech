package com.web.newstech.authoring;

import com.web.newstech.authoring.enums.ArticleStatus;
import com.web.newstech.authoring.repository.ArticleRepository;
import com.web.newstech.authoring.repository.UserRepository;
import com.web.newstech.shared.Slugs;
import com.web.newstech.shared.config.NewsTechProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleService {

	private final ArticleRepository articleRepository;
	private final UserRepository userRepository;
	private final NewsTechProperties properties;

	public Article publish(User author, String title, String subtitle, String bodyMarkdown, List<String> topics) {
		validate(title, bodyMarkdown);
		ensureWithinDailyLimit(author);

		Instant agora = Instant.now();
		boolean forageDaQuarentena = author.getPublishedCount() >= properties.authoring().quarantineArticles() - 1;

		Article article = articleRepository.save(Article.builder()
				.slug(Slugs.unique(title, articleRepository::existsBySlug))
				.title(title.trim())
				.subtitle(subtitle == null ? null : subtitle.trim())
				.bodyMarkdown(bodyMarkdown)
				.authorId(author.getId())
				.authorUsername(author.getUsername())
				.authorDisplayName(author.nameForDisplay())
				.topics(topics == null ? List.of() : topics)
				.status(ArticleStatus.PUBLISHED)
				.publishedAt(agora)
				.updatedAt(agora)
				.homeEligible(forageDaQuarentena)
				.build());

		author.setPublishedCount(author.getPublishedCount() + 1);
		userRepository.save(author);

		if (forageDaQuarentena) {
			liberarArtigosAnteriores(author);
		}
		return article;
	}

	public Article update(Article article, String title, String subtitle, String bodyMarkdown, List<String> topics) {
		validate(title, bodyMarkdown);

		// O slug nao e recalculado: uma vez publicado, mudar a url quebra link e perde
		// o que os buscadores ja conheciam.
		article.setTitle(title.trim());
		article.setSubtitle(subtitle == null ? null : subtitle.trim());
		article.setBodyMarkdown(bodyMarkdown);
		article.setTopics(topics == null ? List.of() : topics);
		article.setUpdatedAt(Instant.now());
		return articleRepository.save(article);
	}

	/**
	 * Janela deslizante de 24h, e nao dia civil: evita a rajada na virada da meia-noite
	 * e permite dizer com precisao quanto falta para poder publicar de novo.
	 */
	private void ensureWithinDailyLimit(User author) {
		Duration janela = properties.authoring().publishWindow();
		Instant desde = Instant.now().minus(janela);

		if (articleRepository.countByAuthorIdAndPublishedAtAfter(author.getId(), desde) == 0) {
			return;
		}

		Instant ultimo = articleRepository.findByAuthorIdOrderByPublishedAtDesc(author.getId()).getFirst()
				.getPublishedAt();
		long horasRestantes = Math.max(1, janela.minus(Duration.between(ultimo, Instant.now())).toHours());

		throw new PublishingException(
				"Você já publicou nas últimas %d horas. Pode publicar de novo em cerca de %d h."
						.formatted(janela.toHours(), horasRestantes));
	}

	/**
	 * Ao sair da quarentena, o que a conta ja publicou passa a valer para o fluxo. Sem
	 * isso, os primeiros artigos ficariam invisiveis para sempre - punicao permanente
	 * por ter sido novo um dia.
	 */
	private void liberarArtigosAnteriores(User author) {
		List<Article> anteriores = articleRepository.findByAuthorIdOrderByPublishedAtDesc(author.getId()).stream()
				.filter(artigo -> !artigo.isHomeEligible())
				.toList();

		if (anteriores.isEmpty()) {
			return;
		}
		anteriores.forEach(artigo -> artigo.setHomeEligible(true));
		articleRepository.saveAll(anteriores);
		log.info("Autor '{}' saiu da quarentena; {} artigos liberados para o fluxo",
				author.getUsername(), anteriores.size());
	}

	private void validate(String title, String bodyMarkdown) {
		if (title == null || title.isBlank()) {
			throw new PublishingException("O artigo precisa de um título.");
		}
		if (title.trim().length() > properties.authoring().maxTitleLength()) {
			throw new PublishingException(
					"O título passa de %d caracteres.".formatted(properties.authoring().maxTitleLength()));
		}
		if (bodyMarkdown == null || bodyMarkdown.isBlank()) {
			throw new PublishingException("O artigo precisa de um texto.");
		}
	}

}
