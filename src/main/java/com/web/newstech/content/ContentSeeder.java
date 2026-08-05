package com.web.newstech.content;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Carrega taxonomia e as stories de exemplo.
 *
 * <p>As stories do seed foram escritas a mao a partir de manchetes reais coletadas
 * pelo pipeline. Servem a dois propositos: dar conteudo de verdade para desenvolver
 * as telas antes de o estagio editorial existir, e depois virar os exemplos do prompt
 * do Opus - e o gabarito de como um resumo bom deve sair.
 *
 * <p>Como todo seed do projeto, so insere o que falta: nunca sobrescreve o que ja esta
 * no banco. Assim que o pipeline comecar a publicar de verdade, este arquivo deixa de
 * ter efeito sobre o conteudo existente.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentSeeder implements InitializingBean {

	private final TopicRepository topicRepository;
	private final TrackedEntityRepository entityRepository;
	private final StoryRepository storyRepository;
	private final ObjectMapper objectMapper;

	@Override
	public void afterPropertiesSet() throws Exception {
		seedTopics();
		seedEntities();
		seedStories();
	}

	private void seedTopics() throws Exception {
		int criados = 0;
		for (TopicSeed seed : read("seed/topics.json", TopicSeed[].class)) {
			if (topicRepository.findBySlug(seed.slug()).isPresent()) {
				continue;
			}
			topicRepository.save(Topic.builder()
					.slug(seed.slug())
					.name(seed.name())
					.description(seed.description())
					.displayOrder(seed.displayOrder())
					.active(true)
					.build());
			criados++;
		}
		if (criados > 0) {
			log.info("Seed de topicos: {} criados", criados);
		}
	}

	private void seedEntities() throws Exception {
		int criados = 0;
		for (EntitySeed seed : read("seed/entities.json", EntitySeed[].class)) {
			if (entityRepository.findBySlug(seed.slug()).isPresent()) {
				continue;
			}
			entityRepository.save(TrackedEntity.builder()
					.slug(seed.slug())
					.name(seed.name())
					.type(seed.type())
					.aliases(seed.aliases())
					.description(seed.description())
					.build());
			criados++;
		}
		if (criados > 0) {
			log.info("Seed de entidades: {} criadas", criados);
		}
	}

	private void seedStories() throws Exception {
		int criadas = 0;
		Instant agora = Instant.now();

		for (StorySeed seed : read("seed/stories.json", StorySeed[].class)) {
			if (storyRepository.existsBySlug(seed.slug())) {
				continue;
			}

			// O seed guarda a idade em horas, nao a data: assim as pecas continuam
			// parecendo recentes meses depois, sem precisar reeditar o arquivo.
			Instant publicadaEm = agora.minus(seed.hoursAgo(), ChronoUnit.HOURS);

			storyRepository.save(Story.builder()
					.slug(seed.slug())
					.headline(seed.headline())
					.summary(seed.summary())
					.angle(seed.angle())
					.importance(seed.importance())
					.publishedAt(publicadaEm)
					.topics(seed.topics())
					.entities(seed.entities())
					.sources(seed.sources().stream()
							.map(s -> new StorySource(s.sourceName(), s.sourceUrl(), s.articleUrl(), publicadaEm))
							.toList())
					.build());
			criadas++;
		}

		if (criadas > 0) {
			log.info("Seed de stories: {} criadas", criadas);
		}
	}

	private <T> List<T> read(String caminho, Class<T[]> tipo) throws Exception {
		try (InputStream stream = new ClassPathResource(caminho).getInputStream()) {
			return List.of(objectMapper.readValue(stream, tipo));
		}
	}

	record TopicSeed(String slug, String name, int displayOrder, String description) {
	}

	record EntitySeed(String slug, String name, TrackedEntity.EntityType type, List<String> aliases,
					  String description) {
	}

	record StorySeed(String slug, String headline, String summary, String angle, Importance importance,
					 long hoursAgo, List<String> topics, List<String> entities, List<SourceSeed> sources) {
	}

	record SourceSeed(String sourceName, String sourceUrl, String articleUrl) {
	}

}
