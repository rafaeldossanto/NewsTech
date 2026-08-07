package com.web.newstech.curation;

import com.web.newstech.TestcontainersConfiguration;
import com.web.newstech.content.Importance;
import com.web.newstech.content.Story;
import com.web.newstech.content.StorySource;
import com.web.newstech.content.repository.StoryRepository;
import com.web.newstech.curation.editorial.EditorialDecision;
import com.web.newstech.curation.editorial.EditorialService;
import com.web.newstech.curation.editorial.ModelRefusedException;
import com.web.newstech.curation.editorial.StubEditorialModel;
import com.web.newstech.ingest.RawItem;
import com.web.newstech.ingest.Source;
import com.web.newstech.ingest.enums.ConnectorType;
import com.web.newstech.ingest.enums.RawItemStatus;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O pipeline inteiro: item bruto entra, peca publicada sai.
 *
 * <p>Os dois estagios de modelo sao dubles. Nenhuma chamada paga acontece aqui, e o
 * teste nao depende de rede nem de servico no ar - foi para isso que as duas portas
 * foram extraidas.
 */
@Import({ TestcontainersConfiguration.class, PipelineIT.StubsConfig.class })
@SpringBootTest
class PipelineIT {

	@Autowired
	private TriageService triageService;

	@Autowired
	private EditorialService editorialService;

	@Autowired
	private StubTriageModel triageStub;

	@Autowired
	private StubEditorialModel editorialStub;

	@Autowired
	private RawItemRepository rawItemRepository;

	@Autowired
	private StoryRepository storyRepository;

	@Autowired
	private SourceRepository sourceRepository;

	private String sourceId;

	@BeforeEach
	void preparar() {
		rawItemRepository.deleteAll();
		// Preserva as pecas do seed, que outros testes no mesmo contexto usam.
		storyRepository.deleteAll(storyRepository.findAll().stream()
				.filter(story -> story.getModel() != null)
				.toList());

		sourceId = sourceRepository.save(Source.builder()
				.name("Ars Technica")
				.feedUrl("https://exemplo.test/" + UUID.randomUUID())
				.connectorType(ConnectorType.RSS)
				.trustWeight(80)
				.active(true)
				.build()).getId();
	}

	@Test
	@DisplayName("três itens do mesmo fato percorrem o pipeline e viram uma peça com três créditos")
	void fluxoCompleto() {
		coletar("Anthropic's AI used fake identities in rogue attack", 3);
		coletar("AI models shock UK testers by using fake identities", 2);
		coletar("Fake identities used by AI models, say UK testers", 1);

		triarComRelevancia(90);

		assertThat(rawItemRepository.countByStatus(RawItemStatus.TRIAGED))
				.as("os três passaram do corte de relevância")
				.isEqualTo(3);

		editorialStub.responder(cluster -> decisao("Modelos de IA usaram identidades falsas em teste do Reino Unido",
				Importance.MANCHETE, List.of(1, 2, 3)));

		assertThat(editorialService.publishPending()).isEqualTo(1);

		Story story = publicadasNoTeste().getFirst();
		assertThat(story.getSources()).hasSize(3);
		assertThat(story.getSources()).extracting(StorySource::sourceName).containsOnly("Ars Technica");
		assertThat(story.getSources()).allSatisfy(fonte -> assertThat(fonte.articleUrl()).isNotBlank());
		assertThat(story.getModel()).isEqualTo("stub");

		assertThat(rawItemRepository.findAll())
				.allSatisfy(item -> assertThat(item.getStatus()).isEqualTo(RawItemStatus.PUBLISHED));
	}

	@Test
	@DisplayName("item abaixo do corte é descartado e nunca chega ao estágio caro")
	void itemIrrelevanteNaoChegaAoEditorial() {
		coletar("Galaxy Watch em promoção histórica", 2);
		triarComRelevancia(8);

		assertThat(rawItemRepository.countByStatus(RawItemStatus.DISCARDED)).isEqualTo(1);

		// O dublê do estágio 2 lança se for chamado: nada deve chegar até ele.
		editorialStub.lancar(new IllegalStateException("o estágio editorial não deveria ter sido chamado"));

		assertThat(editorialService.publishPending())
				.as("descartar cedo é o que mantém o custo do Opus baixo")
				.isZero();
		assertThat(publicadasNoTeste()).isEmpty();
	}

	@Test
	@DisplayName("recusa na triagem manda o item para revisão, sem seguir no pipeline")
	void recusaNaTriagem() {
		coletar("Detalhes técnicos de uma vulnerabilidade crítica", 2);
		triageStub.lancar(new TriageRefusedException("classificador recusou"));

		triageService.triageBatch();

		assertThat(rawItemRepository.findAll())
				.singleElement()
				.satisfies(item -> {
					assertThat(item.getStatus()).isEqualTo(RawItemStatus.NEEDS_REVIEW);
					assertThat(item.getTriage()).as("sem triagem gravada, não há o que clusterizar").isNull();
				});

		assertThat(editorialService.publishPending()).isZero();
	}

	@Test
	@DisplayName("recusa no estágio editorial não publica e não perde o item")
	void recusaNoEditorial() {
		coletar("Uma notícia qualquer de segurança", 2);
		triarComRelevancia(85);

		editorialStub.lancar(new ModelRefusedException("classificador recusou"));

		assertThat(editorialService.publishPending()).isZero();
		assertThat(publicadasNoTeste()).isEmpty();
		assertThat(rawItemRepository.findAll())
				.allSatisfy(item -> assertThat(item.getStatus()).isEqualTo(RawItemStatus.NEEDS_REVIEW));
	}

	@Test
	@DisplayName("o custo de cada etapa fica registrado para o painel")
	void custoRegistrado() {
		coletar("Uma notícia relevante", 2);
		triarComRelevancia(85);

		assertThat(rawItemRepository.findAll().getFirst().getTriage())
				.satisfies(triage -> {
					assertThat(triage.model()).isEqualTo("stub-haiku");
					assertThat(triage.inputTokens()).isEqualTo(300);
					assertThat(triage.outputTokens()).isEqualTo(60);
					assertThat(triage.cachedInputTokens()).isEqualTo(200);
				});

		editorialStub.responder(cluster -> decisao("Peça publicada", Importance.RADAR, List.of(1)));
		editorialService.publishPending();

		assertThat(publicadasNoTeste().getFirst())
				.satisfies(story -> {
					assertThat(story.getInputTokens()).isEqualTo(1000);
					assertThat(story.getOutputTokens()).isEqualTo(200);
					assertThat(story.getCachedInputTokens()).isEqualTo(800);
				});
	}

	@Test
	@DisplayName("nenhuma peça é publicada sem fonte, mesmo o modelo não indicando itens")
	void nuncaPublicaSemFonte() {
		coletar("Uma notícia relevante", 2);
		triarComRelevancia(85);

		// itemsUsed vazio: o serviço assume o cluster inteiro em vez de gravar sem crédito.
		editorialStub.responder(cluster -> decisao("Peça sem indicação de itens", Importance.RADAR, List.of()));
		editorialService.publishPending();

		assertThat(publicadasNoTeste())
				.singleElement()
				.satisfies(story -> assertThat(story.getSources()).isNotEmpty());
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

	private void coletar(String titulo, int horasAtras) {
		rawItemRepository.save(RawItem.builder()
				.sourceId(sourceId)
				.externalId(UUID.randomUUID().toString())
				.title(titulo)
				.url("https://exemplo.test/artigo/" + UUID.randomUUID())
				.contentHash(UUID.randomUUID().toString())
				.publishedAt(Instant.now().minus(Duration.ofHours(horasAtras)))
				.fetchedAt(Instant.now())
				.status(RawItemStatus.COLLECTED)
				.build());
	}

	private void triarComRelevancia(int score) {
		triageStub.responder(item -> new TriageResult(
				List.of("ia", "seguranca"), List.of("Anthropic", "OpenAI"), "en", score, "gabarito de teste"));
		triageService.triageBatch();
	}

	private EditorialDecision decisao(String headline, Importance importance, List<Integer> usados) {
		return new EditorialDecision(headline, "Primeira frase do resumo. Segunda com o dado.",
				"Por que importa.", importance, List.of("ia"), List.of("anthropic"), usados, null);
	}

	@TestConfiguration
	static class StubsConfig {

		@Bean
		@Primary
		StubTriageModel stubTriageModel() {
			return new StubTriageModel();
		}

		@Bean
		@Primary
		StubEditorialModel stubEditorialModel() {
			return new StubEditorialModel();
		}

	}

}
