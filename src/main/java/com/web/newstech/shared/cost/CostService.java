package com.web.newstech.shared.cost;

import com.web.newstech.shared.config.NewsTechProperties;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;

/**
 * Consolida o que o pipeline gastou, a partir dos tokens que cada etapa ja grava.
 *
 * <p>Agrega no banco em vez de carregar tudo em memoria: o volume cresce com o tempo,
 * e um painel que fica mais lento a cada mes deixa de ser consultado justamente quando
 * passa a importar.
 */
@Service
@RequiredArgsConstructor
public class CostService {

	private static final String TRIAGEM = "triagem";
	private static final String EDITORIAL = "curadoria";

	private final MongoTemplate mongoTemplate;
	private final NewsTechProperties properties;

	public CostReport report(int days) {
		Instant desde = Instant.now().minus(days, ChronoUnit.DAYS);

		List<ModelUsage> usages = new ArrayList<>();
		usages.addAll(agregar("rawItems", "triage.model", "triage.", "triage.triagedAt", desde, TRIAGEM));
		usages.addAll(agregar("stories", "model", "", "publishedAt", desde, EDITORIAL));

		usages.sort(Comparator.comparingDouble(ModelUsage::cost).reversed());

		double total = usages.stream().mapToDouble(ModelUsage::cost).sum();
		long stories = usages.stream().filter(u -> EDITORIAL.equals(u.stage())).mapToLong(ModelUsage::calls).sum();
		long triados = usages.stream().filter(u -> TRIAGEM.equals(u.stage())).mapToLong(ModelUsage::calls).sum();

		return new CostReport(days, List.copyOf(usages), total, stories, triados);
	}

	private List<ModelUsage> agregar(String collection, String modelField, String prefix, String dateField,
			Instant desde, String stage) {

		Aggregation aggregation = newAggregation(
				match(Criteria.where(modelField).ne(null).and(dateField).gte(Date.from(desde))),
				group(modelField)
						.count().as("calls")
						.sum(prefix + "inputTokens").as("inputTokens")
						.sum(prefix + "outputTokens").as("outputTokens")
						.sum(prefix + "cachedInputTokens").as("cachedInputTokens"));

		AggregationResults<Document> results =
				mongoTemplate.aggregate(aggregation, collection, Document.class);

		return results.getMappedResults().stream()
				.map(doc -> toUsage(doc, stage))
				.toList();
	}

	private ModelUsage toUsage(Document doc, String stage) {
		String model = Objects.toString(doc.get("_id"), "desconhecido");
		long input = numero(doc, "inputTokens");
		long output = numero(doc, "outputTokens");
		long cached = numero(doc, "cachedInputTokens");

		return new ModelUsage(stage, model, numero(doc, "calls"), input, output, cached,
				custo(model, input, output, cached));
	}

	/**
	 * Modelo sem preco cadastrado entra com custo zero em vez de estourar: o painel
	 * mostrando um numero incompleto e melhor do que nao abrir - e a linha com zero
	 * denuncia o preco que falta.
	 */
	private double custo(String model, long input, long output, long cached) {
		NewsTechProperties.ModelPrice price = properties.pricing().get(model);
		if (Objects.isNull(price)) {
			return 0;
		}
		return input / 1_000_000.0 * price.inputPerMillion()
				+ output / 1_000_000.0 * price.outputPerMillion()
				+ cached / 1_000_000.0 * price.cachedInputPerMillion();
	}

	private long numero(Document doc, String campo) {
		Object valor = doc.get(campo);
		return valor instanceof Number numero ? numero.longValue() : 0L;
	}

}
