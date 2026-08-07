package com.web.newstech.curation.editorial;

import com.web.newstech.TestcontainersConfiguration;
import com.web.newstech.content.Importance;
import com.web.newstech.content.Story;
import com.web.newstech.content.repository.StoryRepository;
import com.web.newstech.content.StorySource;
import com.web.newstech.curation.cluster.ItemCluster;
import com.web.newstech.ingest.RawItem;
import com.web.newstech.ingest.Source;
import com.web.newstech.ingest.enums.ConnectorType;
import com.web.newstech.ingest.enums.RawItemStatus;
import com.web.newstech.ingest.model.Triage;
import com.web.newstech.ingest.repository.RawItemRepository;
import com.web.newstech.ingest.repository.SourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O estágio editorial de ponta a ponta, com as decisões do modelo gravadas.
 *
 * <p>Nenhuma chamada de API acontece aqui: o {@link EditorialModel} é substituído por um
 * dublê. É para isso que a porta existe — sem ela, cada execução do CI custaria dinheiro
 * e dependeria de a rede e o serviço estarem no ar.
 */
@Import({ TestcontainersConfiguration.class, EditorialServiceIT.StubConfig.class })
@SpringBootTest
class EditorialServiceIT {

	@Autowired
	private EditorialService editorialService;

	@Autowired
	private StubEditorialModel stub;

	@Autowired
	private RawItemRepository rawItemRepository;

	@Autowired
	private StoryRepository storyRepository;

	@Autowired
	private SourceRepository sourceRepository;

	private String arsId;
	private String guardianId;

	@BeforeEach
	void preparar() {
		rawItemRepository.deleteAll();
		// Preserva as pecas do seed, que outros testes no mesmo contexto usam.
		storyRepository.deleteAll(storyRepository.findAll().stream()
				.filter(story -> story.getModel() != null)
				.toList());

		arsId = salvarFonte("Ars Technica", 80).getId();
		guardianId = salvarFonte("The Guardian", 70).getId();
	}

	@Test
	@DisplayName("cluster de três itens vira uma peça com os três créditos")
	void publicaComTodosOsCreditos() {
		ItemCluster cluster = clusterDeTres();
		stub.responder(c -> decisao("Modelos de IA usaram identidades falsas em teste do Reino Unido",
				Importance.MANCHETE, List.of(1, 2, 3), null));

		assertThat(editorialService.publish(cluster)).isTrue();

		Story story = publicadasNoTeste().getFirst();
		assertThat(story.getSources()).hasSize(3);
		assertThat(story.getRawItemIds()).hasSize(3);
		assertThat(story.getSlug()).isEqualTo("modelos-de-ia-usaram-identidades-falsas-em-teste-do-reino-unido");
		assertThat(story.getImportance()).isEqualTo(Importance.MANCHETE);

		assertThat(rawItemRepository.findAll())
				.allSatisfy(item -> {
					assertThat(item.getStatus()).isEqualTo(RawItemStatus.PUBLISHED);
					assertThat(item.getStoryId()).isEqualTo(story.getId());
				});
	}

	@Test
	@DisplayName("a fonte de maior confiabilidade encabeça os créditos")
	void fontePrincipalEhADeMaiorPeso() {
		// O item do Guardian (peso 70) é o primeiro na ordem cronológica; o do Ars (80) vem
		// depois. Quem deve aparecer como fonte principal é o Ars.
		RawItem guardian = salvarItem(guardianId, "AI models shock UK testers", Instant.now().minus(Duration.ofHours(4)));
		RawItem ars = salvarItem(arsId, "Anthropic's AI used fake identities", Instant.now().minus(Duration.ofHours(3)));
		ItemCluster cluster = new ItemCluster(List.of(guardian, ars));

		stub.responder(c -> decisao("Peça de teste sobre identidades", Importance.DESTAQUE, List.of(1, 2), null));

		editorialService.publish(cluster);

		assertThat(publicadasNoTeste().getFirst().getSources())
				.extracting(StorySource::sourceName)
				.containsExactly("Ars Technica", "The Guardian");
	}

	@Test
	@DisplayName("item que o modelo não usou volta para a fila, não vira crédito errado")
	void itemNaoUsadoVoltaParaAFila() {
		ItemCluster cluster = clusterDeTres();
		// O modelo conclui que o terceiro item não é o mesmo fato.
		stub.responder(c -> decisao("Peça com dois itens", Importance.RADAR, List.of(1, 2), null));

		editorialService.publish(cluster);

		Story story = publicadasNoTeste().getFirst();
		assertThat(story.getSources())
				.as("crédito errado é pior que crédito nenhum")
				.hasSize(2);

		RawItem terceiro = rawItemRepository.findById(cluster.items().get(2).getId()).orElseThrow();
		assertThat(terceiro.getStatus()).isEqualTo(RawItemStatus.TRIAGED);
		assertThat(terceiro.getEditorialAttempts()).isEqualTo(1);
	}

	@Test
	@DisplayName("item rejeitado repetidamente para de voltar e vai para revisão")
	void itemRejeitadoDuasVezesSaiDaFila() {
		RawItem sozinho = salvarItem(arsId, "Item que ninguém quer", Instant.now().minus(Duration.ofHours(1)));
		sozinho.setEditorialAttempts(1);
		rawItemRepository.save(sozinho);

		RawItem principal = salvarItem(arsId, "Item principal", Instant.now().minus(Duration.ofHours(2)));
		stub.responder(c -> decisao("Só o principal", Importance.RADAR, List.of(1), null));

		editorialService.publish(new ItemCluster(List.of(principal, sozinho)));

		assertThat(rawItemRepository.findById(sozinho.getId()).orElseThrow().getStatus())
				.as("sem teto de tentativas, ele voltaria para sempre gastando uma chamada por ciclo")
				.isEqualTo(RawItemStatus.NEEDS_REVIEW);
	}

	@Test
	@DisplayName("cluster rejeitado pelo modelo não publica nada")
	void clusterRejeitado() {
		ItemCluster cluster = clusterDeTres();
		stub.responder(c -> decisao(null, null, List.of(),
				"compilado promocional de anúncios já publicados, sem fato novo"));

		assertThat(editorialService.publish(cluster)).isFalse();
		assertThat(publicadasNoTeste()).isEmpty();
		assertThat(rawItemRepository.findAll())
				.allMatch(item -> item.getStatus() == RawItemStatus.DISCARDED);
	}

	@Test
	@DisplayName("recusa do modelo manda os itens para revisão, sem derrubar o ciclo")
	void modeloRecusa() {
		clusterDeTres();
		stub.lancar(new ModelRefusedException("classificador recusou"));

		assertThat(editorialService.publishPending()).isZero();
		assertThat(publicadasNoTeste()).isEmpty();
		assertThat(rawItemRepository.findAll())
				.as("recusa não é bug: o item precisa de olhar humano, não de nova tentativa")
				.allMatch(item -> item.getStatus() == RawItemStatus.NEEDS_REVIEW);
	}

	@Test
	@DisplayName("resposta truncada não publica peça pela metade")
	void respostaTruncada() {
		clusterDeTres();
		stub.lancar(new EditorialException("Resposta truncada por max_tokens"));

		assertThat(editorialService.publishPending()).isZero();
		assertThat(publicadasNoTeste()).isEmpty();
	}

	// ---------- apoio ----------

	/**
	 * So o que este teste publicou. O banco tambem carrega as pecas do seed, que outros
	 * testes usam no mesmo contexto - findAll() aqui traria as duas coisas misturadas.
	 */
	private List<Story> publicadasNoTeste() {
		return storyRepository.findAll().stream()
				.filter(story -> story.getModel() != null)
				.toList();
	}

	private ItemCluster clusterDeTres() {
		Instant agora = Instant.now();
		return new ItemCluster(List.of(
				salvarItem(arsId, "Anthropic's AI used fake identities in rogue attack", agora.minus(Duration.ofHours(3))),
				salvarItem(guardianId, "AI models shock UK testers by using fake identities", agora.minus(Duration.ofHours(2))),
				salvarItem(guardianId, "Third-party cyber evaluations involving OpenAI models", agora.minus(Duration.ofHours(1)))));
	}

	private EditorialDecision decisao(String headline, Importance importance, List<Integer> usados, String rejeicao) {
		return new EditorialDecision(headline, "Resumo de duas frases. Segunda frase com o dado.",
				"Por que importa.", importance, List.of("ia", "seguranca"), List.of("anthropic"), usados, rejeicao);
	}

	private Source salvarFonte(String nome, int peso) {
		return sourceRepository.save(Source.builder()
				.name(nome)
				.feedUrl("https://exemplo.test/" + UUID.randomUUID())
				.connectorType(ConnectorType.RSS)
				.trustWeight(peso)
				.active(true)
				.build());
	}

	private RawItem salvarItem(String sourceId, String titulo, Instant publicadoEm) {
		return rawItemRepository.save(RawItem.builder()
				.sourceId(sourceId)
				.externalId(UUID.randomUUID().toString())
				.title(titulo)
				.url("https://exemplo.test/artigo/" + UUID.randomUUID())
				.contentHash(UUID.randomUUID().toString())
				.publishedAt(publicadoEm)
				.fetchedAt(Instant.now())
				.status(RawItemStatus.TRIAGED)
				.triage(new Triage(List.of("ia"), List.of("Anthropic", "OpenAI"), "en", 85, "", "manual", 0, 0, 0,
						Instant.now()))
				.build());
	}

	/**
	 * Substitui o {@link ClaudeEditorialModel} pelo dublê. {@code @Primary} porque a
	 * implementação real continua no contexto — ela só não pode ser a escolhida aqui.
	 */
	@TestConfiguration
	static class StubConfig {

		@Bean
		@Primary
		StubEditorialModel stubEditorialModel() {
			return new StubEditorialModel();
		}

	}

}
