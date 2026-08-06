package com.web.newstech.curation.cluster;

import com.web.newstech.ingest.RawItem;
import com.web.newstech.ingest.RawItemRepository;
import com.web.newstech.ingest.RawItemStatus;
import com.web.newstech.shared.config.NewsTechProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Agrupa itens já triados que falam do mesmo fato, antes de o estágio editorial gastar
 * uma chamada de modelo.
 *
 * <p>É a etapa mais barata do pipeline: roda em Java puro, sem API. Ela não precisa
 * acertar sozinha — o estágio 2 recebe o cluster e pode rejeitar o agrupamento. O que
 * ela precisa é juntar candidatos plausíveis e não deixar passar o óbvio.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterService {

	private final RawItemRepository rawItemRepository;
	private final NewsTechProperties properties;

	/** Agrupa o que está pronto para curadoria dentro da janela configurada. */
	public List<ItemCluster> pendingClusters() {
		Instant since = Instant.now().minus(properties.cluster().windowHours(), ChronoUnit.HOURS);
		List<RawItem> triados = rawItemRepository.findByStatusAndPublishedAtAfter(RawItemStatus.TRIAGED, since);
		return cluster(triados);
	}

	/**
	 * Agrupamento por união transitiva: se A casa com B e B casa com C, os três ficam
	 * juntos mesmo que A e C não casassem diretamente. É o comportamento desejado numa
	 * cobertura em cadeia, onde cada veículo escreve o título do seu jeito.
	 *
	 * <p>Comparação par a par. Com algumas centenas de itens por ciclo isso é trivial;
	 * se o volume crescer uma ordem de grandeza, o caminho é pré-filtrar por entidade
	 * antes de comparar, e não trocar o algoritmo.
	 */
	public List<ItemCluster> cluster(List<RawItem> items) {
		List<RawItem> ordenados = items.stream()
				.filter(item -> Objects.nonNull(item.getPublishedAt()))
				.sorted(Comparator.comparing(RawItem::getPublishedAt))
				.toList();

		if (ordenados.size() < 2) {
			return ordenados.stream().map(item -> new ItemCluster(List.of(item))).toList();
		}

		int[] parent = new int[ordenados.size()];
		for (int i = 0; i < parent.length; i++) {
			parent[i] = i;
		}

		Duration janela = Duration.ofHours(properties.cluster().windowHours());
		double limiar = properties.cluster().threshold();

		for (int i = 0; i < ordenados.size(); i++) {
			for (int j = i + 1; j < ordenados.size(); j++) {
				RawItem a = ordenados.get(i);
				RawItem b = ordenados.get(j);

				// A lista está ordenada por data: passou da janela, os seguintes também passam.
				if (Duration.between(a.getPublishedAt(), b.getPublishedAt()).compareTo(janela) > 0) {
					break;
				}
				if (SimilarityScorer.score(a, b) >= limiar) {
					union(parent, i, j);
				}
			}
		}

		return montarClusters(ordenados, parent);
	}

	private List<ItemCluster> montarClusters(List<RawItem> ordenados, int[] parent) {
		Map<Integer, List<RawItem>> porRaiz = new LinkedHashMap<>();
		for (int i = 0; i < ordenados.size(); i++) {
			porRaiz.computeIfAbsent(find(parent, i), raiz -> new ArrayList<>()).add(ordenados.get(i));
		}

		List<ItemCluster> clusters = porRaiz.values().stream()
				.map(List::copyOf)
				.map(ItemCluster::new)
				.toList();

		long agrupados = clusters.stream().filter(cluster -> !cluster.isSingleton()).count();
		if (agrupados > 0) {
			log.debug("Clusterizacao: {} itens em {} grupos, {} deles com mais de uma fonte",
					ordenados.size(), clusters.size(), agrupados);
		}
		return clusters;
	}

	private int find(int[] parent, int node) {
		while (parent[node] != node) {
			parent[node] = parent[parent[node]];
			node = parent[node];
		}
		return node;
	}

	private void union(int[] parent, int a, int b) {
		int raizA = find(parent, a);
		int raizB = find(parent, b);
		if (raizA != raizB) {
			parent[raizB] = raizA;
		}
	}

	/** Só para diagnóstico: explica por que dois itens específicos casaram (ou não). */
	public SimilarityScorer.Explanation explain(RawItem a, RawItem b) {
		return SimilarityScorer.explain(a, b);
	}

}
