package com.web.newstech.curation.editorial;

import com.web.newstech.content.Story;
import com.web.newstech.content.repository.StoryRepository;
import com.web.newstech.content.StorySource;
import com.web.newstech.curation.cluster.ClusterService;
import com.web.newstech.curation.cluster.ItemCluster;
import com.web.newstech.ingest.RawItem;

import com.web.newstech.ingest.Source;
import com.web.newstech.ingest.enums.RawItemStatus;
import com.web.newstech.ingest.repository.RawItemRepository;
import com.web.newstech.ingest.repository.SourceRepository;
import com.web.newstech.shared.Slugs;
import com.web.newstech.shared.config.NewsTechProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Estágio 2 do pipeline: transforma clusters em peças publicadas.
 *
 * <p>Orquestra apenas — a decisão vem do {@link EditorialModel}. Aqui mora o que
 * acontece com cada item depois dela, que é a parte que precisa estar certa mesmo
 * quando o modelo erra.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EditorialService {

	/** Depois disto, o item para de voltar para a fila e vai para revisão. */
	private static final int MAX_EDITORIAL_ATTEMPTS = 2;

	private final ClusterService clusterService;
	private final EditorialModel editorialModel;
	private final StoryRepository storyRepository;
	private final RawItemRepository rawItemRepository;
	private final SourceRepository sourceRepository;
	private final NewsTechProperties properties;

	/**
	 * Processa os clusters pendentes. Falha em um não derruba os demais: cada cluster
	 * é uma chamada independente, e uma recusa do modelo não pode travar a publicação
	 * do resto do ciclo.
	 *
	 * @return quantas peças foram publicadas
	 */
	public int publishPending() {
		List<ItemCluster> clusters = clusterService.pendingClusters().stream()
				.limit(properties.claude().editorialBatchSize())
				.toList();

		int publicadas = 0;
		for (ItemCluster cluster : clusters) {
			try {
				if (publish(cluster)) {
					publicadas++;
				}
			}
			catch (ModelRefusedException ex) {
				log.warn("Modelo recusou o cluster; itens vão para revisão: {}", ex.getMessage());
				marcarTodos(cluster, RawItemStatus.NEEDS_REVIEW);
			}
			catch (RuntimeException ex) {
				log.error("Falha ao editorializar cluster iniciado por {}: {}",
						cluster.earliest().getUrl(), ex.getMessage());
				marcarTodos(cluster, RawItemStatus.NEEDS_REVIEW);
			}
		}

		if (!clusters.isEmpty()) {
			log.info("Estágio editorial: {} peças publicadas de {} clusters", publicadas, clusters.size());
		}
		return publicadas;
	}

	/**
	 * @return {@code true} se o cluster virou peça publicada
	 */
	public boolean publish(ItemCluster cluster) {
		EditorialOutcome outcome = editorialModel.decide(cluster);
		EditorialDecision decision = outcome.decision();

		if (decision.rejected()) {
			log.debug("Cluster rejeitado pelo modelo: {}", decision.rejectionReason());
			marcarTodos(cluster, RawItemStatus.DISCARDED);
			return false;
		}

		List<RawItem> usados = itensUsados(cluster, decision);
		if (usados.isEmpty()) {
			log.warn("Modelo não indicou nenhum item utilizável; cluster vai para revisão");
			marcarTodos(cluster, RawItemStatus.NEEDS_REVIEW);
			return false;
		}

		Story story = gravarStory(cluster, decision, outcome, usados);
		concluirItens(cluster, usados, story);
		return true;
	}

	/**
	 * Resolve as posições devolvidas pelo modelo em itens.
	 *
	 * <p>Índice fora da faixa é ignorado em silêncio, e não tratado como erro: o modelo
	 * errar uma posição não deve impedir a publicação de uma peça que está correta no
	 * resto. Se ele errar todas, o chamador manda o cluster para revisão.
	 */
	private List<RawItem> itensUsados(ItemCluster cluster, EditorialDecision decision) {
		List<RawItem> items = cluster.items();

		if (Objects.isNull(decision.itemsUsed()) || decision.itemsUsed().isEmpty()) {
			// Sem indicação explícita, o cluster inteiro compõe a peça.
			return items;
		}

		return decision.itemsUsed().stream()
				.filter(Objects::nonNull)
				.map(posicao -> posicao - 1)
				.filter(indice -> indice >= 0 && indice < items.size())
				.distinct()
				.map(items::get)
				.toList();
	}

	private Story gravarStory(ItemCluster cluster, EditorialDecision decision, EditorialOutcome outcome,
			List<RawItem> usados) {

		Map<String, Source> fontes = carregarFontes(usados);

		// A fonte de maior confiabilidade encabeça a lista de créditos: é ela que aparece
		// como "fonte principal" nos cards. É o único uso do trustWeight no pipeline.
		List<StorySource> creditos = usados.stream()
				.sorted(Comparator.comparingInt((RawItem item) -> peso(fontes, item)).reversed())
				.map(item -> credito(fontes, item))
				.toList();

		Story story = Story.builder()
				.headline(decision.headline())
				.summary(decision.summary())
				.angle(decision.angle())
				.importance(decision.importance())
				.slug(Slugs.unique(decision.headline(), storyRepository::existsBySlug))
				// A data do fato, não a do processamento: um item recuperado com atraso
				// deve aparecer com a data em que a notícia saiu.
				.publishedAt(cluster.mostRecentPublication())
				.topics(Objects.requireNonNullElse(decision.topics(), List.of()))
				.entities(Objects.requireNonNullElse(decision.entities(), List.of()))
				.sources(creditos)
				.rawItemIds(usados.stream().map(RawItem::getId).toList())
				.model(outcome.model())
				.inputTokens(outcome.inputTokens())
				.outputTokens(outcome.outputTokens())
				.cachedInputTokens(outcome.cachedInputTokens())
				.build();

		return storyRepository.save(story);
	}

	private Map<String, Source> carregarFontes(List<RawItem> itens) {
		List<String> ids = itens.stream().map(RawItem::getSourceId).filter(Objects::nonNull).distinct().toList();
		return sourceRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(Source::getId, Function.identity()));
	}

	private int peso(Map<String, Source> fontes, RawItem item) {
		Source source = fontes.get(item.getSourceId());
		return Objects.isNull(source) ? 0 : source.getTrustWeight();
	}

	private StorySource credito(Map<String, Source> fontes, RawItem item) {
		Source source = fontes.get(item.getSourceId());
		// Fonte apagada depois da coleta não pode impedir o crédito: o link do artigo
		// continua sendo a informação essencial, e ele está no próprio item.
		String nome = Objects.isNull(source) ? "Fonte original" : source.getName();
		String home = Objects.isNull(source) ? null : source.getFeedUrl();
		return new StorySource(nome, home, item.getUrl(), item.getPublishedAt());
	}

	/** Marca os usados como publicados e devolve os demais à fila, com limite de tentativas. */
	private void concluirItens(ItemCluster cluster, List<RawItem> usados, Story story) {
		List<String> idsUsados = usados.stream().map(RawItem::getId).toList();

		usados.forEach(item -> {
			item.setStatus(RawItemStatus.PUBLISHED);
			item.setStoryId(story.getId());
			rawItemRepository.save(item);
		});

		cluster.items().stream()
				.filter(item -> !idsUsados.contains(item.getId()))
				.forEach(this::devolverAFila);
	}

	private void devolverAFila(RawItem item) {
		int tentativas = item.getEditorialAttempts() + 1;
		item.setEditorialAttempts(tentativas);

		if (tentativas >= MAX_EDITORIAL_ATTEMPTS) {
			log.debug("Item {} rejeitado {} vezes pelo estágio editorial; vai para revisão",
					item.getId(), tentativas);
			item.setStatus(RawItemStatus.NEEDS_REVIEW);
		}
		rawItemRepository.save(item);
	}

	private void marcarTodos(ItemCluster cluster, RawItemStatus status) {
		cluster.items().forEach(item -> {
			item.setStatus(status);
			rawItemRepository.save(item);
		});
	}

}
