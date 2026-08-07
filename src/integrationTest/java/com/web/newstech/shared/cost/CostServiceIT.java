package com.web.newstech.shared.cost;

import com.web.newstech.TestcontainersConfiguration;
import com.web.newstech.content.Importance;
import com.web.newstech.content.Story;
import com.web.newstech.content.StorySource;
import com.web.newstech.content.repository.StoryRepository;
import com.web.newstech.ingest.RawItem;
import com.web.newstech.ingest.enums.RawItemStatus;
import com.web.newstech.ingest.model.Triage;
import com.web.newstech.ingest.repository.RawItemRepository;
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
import static org.assertj.core.api.Assertions.within;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CostServiceIT {

	@Autowired
	private CostService costService;

	@Autowired
	private RawItemRepository rawItemRepository;

	@Autowired
	private StoryRepository storyRepository;

	@BeforeEach
	void limpar() {
		rawItemRepository.deleteAll();
		// Apaga so o que tem modelo gravado - as pecas do seed nao tem, e sao usadas por
		// outros testes no mesmo contexto. deleteAll() aqui quebra o PortalIT.
		storyRepository.deleteAll(storyRepository.findAll().stream()
				.filter(story -> story.getModel() != null)
				.toList());
	}

	@Test
	@DisplayName("sem chamada registrada, o relatório vem zerado em vez de quebrar")
	void semDados() {
		CostReport report = costService.report(30);

		assertThat(report.isEmpty()).isTrue();
		assertThat(report.total()).isZero();
		assertThat(report.costPerStory()).isZero();
		assertThat(report.projectedMonthly()).isZero();
	}

	@Test
	@DisplayName("calcula o custo de cada etapa pelos tokens gravados")
	void calculaCusto() {
		// 1 milhão de input e 1 milhão de output no Haiku: US$ 1 + US$ 5.
		triado("claude-haiku-4-5", 1_000_000, 1_000_000, 0);
		// 1 milhão de cada no Opus: US$ 5 + US$ 25.
		publicada("claude-opus-5", 1_000_000, 1_000_000, 0);

		CostReport report = costService.report(30);

		assertThat(report.total()).isCloseTo(36.0, within(0.01));
		assertThat(report.usages()).hasSize(2);
		assertThat(report.usages().getFirst().stage())
				.as("a linha mais cara aparece primeiro")
				.isEqualTo("curadoria");
	}

	@Test
	@DisplayName("token lido de cache custa um décimo do input")
	void custoDeCache() {
		triado("claude-haiku-4-5", 0, 0, 1_000_000);

		assertThat(costService.report(30).total()).isCloseTo(0.10, within(0.001));
	}

	@Test
	@DisplayName("a proporção de cache é calculada para denunciar cache inativo")
	void proporcaoDeCache() {
		triado("claude-haiku-4-5", 200_000, 0, 800_000);

		ModelUsage usage = costService.report(30).usages().getFirst();

		assertThat(usage.cacheHitPercent()).isEqualTo(80);
	}

	@Test
	@DisplayName("modelo sem preço cadastrado entra com custo zero, sem derrubar o painel")
	void modeloSemPreco() {
		triado("modelo-que-nao-existe", 1_000_000, 1_000_000, 0);

		CostReport report = costService.report(30);

		assertThat(report.usages()).hasSize(1);
		assertThat(report.usages().getFirst().cost())
				.as("melhor um número incompleto do que um painel que não abre")
				.isZero();
	}

	@Test
	@DisplayName("o período filtra o que entra na conta")
	void filtraPorPeriodo() {
		triadoEm("claude-haiku-4-5", Instant.now().minus(Duration.ofDays(60)));
		triadoEm("claude-haiku-4-5", Instant.now().minus(Duration.ofDays(2)));

		assertThat(costService.report(7).usages().getFirst().calls()).isEqualTo(1);
		assertThat(costService.report(90).usages().getFirst().calls()).isEqualTo(2);
	}

	@Test
	@DisplayName("custo por peça usa só as peças publicadas")
	void custoPorPeca() {
		publicada("claude-opus-5", 1_000_000, 0, 0);
		publicada("claude-opus-5", 1_000_000, 0, 0);

		CostReport report = costService.report(30);

		assertThat(report.storiesPublished()).isEqualTo(2);
		assertThat(report.costPerStory()).isCloseTo(5.0, within(0.01));
	}

	private void triado(String model, long input, long output, long cached) {
		rawItemRepository.save(item(model, input, output, cached, Instant.now()));
	}

	private void triadoEm(String model, Instant quando) {
		rawItemRepository.save(item(model, 1000, 100, 0, quando));
	}

	private RawItem item(String model, long input, long output, long cached, Instant quando) {
		return RawItem.builder()
				.sourceId(UUID.randomUUID().toString())
				.externalId(UUID.randomUUID().toString())
				.title("Item de teste")
				.url("https://exemplo.test/" + UUID.randomUUID())
				.contentHash(UUID.randomUUID().toString())
				.publishedAt(quando)
				.fetchedAt(Instant.now())
				.status(RawItemStatus.TRIAGED)
				.triage(new Triage(List.of(), List.of(), "pt", 80, "", model, input, output, cached, quando))
				.build();
	}

	private void publicada(String model, long input, long output, long cached) {
		storyRepository.save(Story.builder()
				.headline("Peça de teste")
				.summary("Resumo.")
				.importance(Importance.RADAR)
				.slug("peca-" + UUID.randomUUID())
				.publishedAt(Instant.now())
				.sources(List.of(new StorySource("Fonte", null, "https://exemplo.test/artigo", Instant.now())))
				.model(model)
				.inputTokens(input)
				.outputTokens(output)
				.cachedInputTokens(cached)
				.build());
	}

}
