package com.web.newstech.content;

import com.mongodb.MongoWriteException;
import com.web.newstech.TestcontainersConfiguration;
import jakarta.validation.ConstraintViolationException;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prova que o invariante de atribuicao vale nas duas camadas.
 *
 * <p>O desenho inteiro do modelo editorial se apoia em "nenhuma story existe sem fonte".
 * Se o validador estivesse malformado, o MongoDB aceitaria a escrita em silencio e a
 * garantia nao existiria - por isso ela e testada, e nao apenas documentada.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class StoryAttributionIT {

	@Autowired
	private StoryRepository storyRepository;

	@Autowired
	private MongoTemplate mongoTemplate;

	@Test
	@DisplayName("camada de aplicacao: repositorio recusa story sem fonte")
	void repositorioRecusaStorySemFonte() {
		Story semFonte = storyBase().sources(List.of()).build();

		assertThatThrownBy(() -> storyRepository.save(semFonte))
				.isInstanceOf(ConstraintViolationException.class)
				.hasMessageContaining("ao menos uma fonte");
	}

	@Test
	@DisplayName("camada de banco: insercao direta sem fonte e barrada pelo $jsonSchema")
	void bancoRecusaInsercaoDiretaSemFonte() {
		// Escreve pelo driver, contornando o Bean Validation do Spring Data.
		// E o caminho de um script de migracao ou de uma correcao manual no Compass.
		Document semFonte = new Document()
				.append("headline", "Story escrita por fora da aplicacao")
				.append("summary", "Deveria ser barrada pelo validador da colecao")
				.append("slug", "story-sem-fonte")
				.append("publishedAt", Date.from(Instant.now()))
				.append("sources", List.of());

		assertThatThrownBy(() -> mongoTemplate.getDb().getCollection("stories").insertOne(semFonte))
				.isInstanceOf(MongoWriteException.class)
				.hasMessageContaining("validation");
	}

	@Test
	@DisplayName("camada de banco: fonte sem link tambem e barrada")
	void bancoRecusaFonteSemLink() {
		Document fonteIncompleta = new Document()
				.append("headline", "Credito sem link nao e credito")
				.append("summary", "A entrada de fonte precisa de nome e url do artigo")
				.append("slug", "fonte-incompleta")
				.append("publishedAt", Date.from(Instant.now()))
				.append("sources", List.of(new Document("sourceName", "Anthropic")));

		assertThatThrownBy(() -> mongoTemplate.getDb().getCollection("stories").insertOne(fonteIncompleta))
				.isInstanceOf(MongoWriteException.class);
	}

	@Test
	@DisplayName("story com fonte completa e aceita nas duas camadas")
	void aceitaStoryComFonte() {
		Story valida = storyBase()
				.slug("story-valida-" + System.nanoTime())
				.sources(List.of(new StorySource(
						"Anthropic",
						"https://www.anthropic.com",
						"https://www.anthropic.com/news/exemplo",
						Instant.now())))
				.build();

		assertThatCode(() -> storyRepository.save(valida)).doesNotThrowAnyException();
		assertThat(storyRepository.findBySlug(valida.getSlug())).isPresent();
	}

	private Story.StoryBuilder storyBase() {
		return Story.builder()
				.headline("Titulo de teste")
				.summary("Resumo de teste com tamanho suficiente para parecer real.")
				.importance(Importance.RADAR)
				.slug("story-teste-" + System.nanoTime())
				.publishedAt(Instant.now());
	}

}
