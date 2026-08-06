package com.web.newstech.curation.cluster;

import com.web.newstech.ingest.RawItem;
import lombok.experimental.UtilityClass;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decide o quanto dois itens brutos falam do mesmo fato.
 *
 * <p>O desenho inicial exigia similaridade de titulo <em>e</em> entidade em comum.
 * Medido contra os clusters reais do primeiro lote coletado, esse "e" perderia quase
 * todos: titulos sobre o mesmo fato compartilham pouquissimas palavras.
 *
 * <pre>
 *   testes do UK (3 fontes)     titulo 0,17   entidades 0,67
 *   Nikita Bier (2 fontes)      titulo 0,33   entidades 1,00
 *   Gemini Robotics (2 pecas)   titulo 0,13   entidades 1,00
 * </pre>
 *
 * <p>Dai a pontuacao ponderada: o titulo confirma, a entidade e quem realmente liga.
 */
@UtilityClass
public class SimilarityScorer {

	/** O titulo pesa menos porque varia muito entre veiculos que cobrem o mesmo fato. */
	private static final double TITLE_WEIGHT = 0.6;

	private static final double ENTITY_WEIGHT = 0.4;

	/**
	 * Penalidade quando os dois titulos trazem versao e nenhuma coincide.
	 *
	 * <p>Em noticia de tecnologia, numero em titulo quase sempre e versao, e versoes
	 * diferentes sao fatos diferentes por definicao. Sem isto, dois releases seguidos
	 * do Node viram um cluster so - o texto em volta e praticamente igual.
	 */
	private static final double VERSION_MISMATCH_PENALTY = 0.25;

	public static double score(RawItem a, RawItem b) {
		double titleScore = jaccard(TitleTokenizer.tokens(a.getTitle()), TitleTokenizer.tokens(b.getTitle()));
		double entityScore = jaccard(entities(a), entities(b));

		double score = TITLE_WEIGHT * titleScore + ENTITY_WEIGHT * entityScore;
		return score * versionFactor(a, b);
	}

	/**
	 * @return 1.0 quando não há conflito de versão, ou a penalidade quando ambos os
	 *         títulos trazem número e nenhum coincide
	 */
	private static double versionFactor(RawItem a, RawItem b) {
		Set<String> versionsA = TitleTokenizer.versions(a.getTitle());
		Set<String> versionsB = TitleTokenizer.versions(b.getTitle());

		if (versionsA.isEmpty() || versionsB.isEmpty()) {
			return 1.0;
		}
		boolean compartilhamAlguma = versionsA.stream().anyMatch(versionsB::contains);
		return compartilhamAlguma ? 1.0 : VERSION_MISMATCH_PENALTY;
	}

	/**
	 * Entidades da triagem, normalizadas: o modelo devolve o nome como aparece no texto,
	 * então "OpenAI" e "openai" precisam casar.
	 */
	private static Set<String> entities(RawItem item) {
		if (Objects.isNull(item.getTriage()) || Objects.isNull(item.getTriage().entities())) {
			return Set.of();
		}
		return item.getTriage().entities().stream()
				.filter(Objects::nonNull)
				.map(entity -> entity.toLowerCase(Locale.ROOT).trim())
				.filter(entity -> !entity.isBlank())
				.collect(Collectors.toSet());
	}

	/**
	 * Interseção sobre união. Dois conjuntos vazios devolvem 0, e não 1: item sem
	 * entidade nenhuma não tem semelhança a declarar, e tratá-lo como idêntico a outro
	 * item vazio ligaria tudo que a triagem não conseguiu classificar.
	 */
	static double jaccard(Set<String> a, Set<String> b) {
		if (a.isEmpty() || b.isEmpty()) {
			return 0.0;
		}
		Set<String> intersection = new HashSet<>(a);
		intersection.retainAll(b);
		if (intersection.isEmpty()) {
			return 0.0;
		}
		Set<String> union = new HashSet<>(a);
		union.addAll(b);
		return (double) intersection.size() / union.size();
	}

	/** Exposto para o teste e para o admin poderem explicar por que dois itens casaram. */
	public static Explanation explain(RawItem a, RawItem b) {
		return new Explanation(
				jaccard(TitleTokenizer.tokens(a.getTitle()), TitleTokenizer.tokens(b.getTitle())),
				jaccard(entities(a), entities(b)),
				versionFactor(a, b),
				score(a, b));
	}

	public record Explanation(double titleScore, double entityScore, double versionFactor, double total) {

		public List<String> asLines() {
			return List.of(
					"titulo    %.3f".formatted(titleScore),
					"entidades %.3f".formatted(entityScore),
					"versao    x%.2f".formatted(versionFactor),
					"total     %.3f".formatted(total));
		}

	}

}
