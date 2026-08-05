package com.web.newstech.ingest;

import com.web.newstech.ingest.connector.FetchResult;
import com.web.newstech.ingest.connector.FetchedItem;
import com.web.newstech.ingest.connector.SourceConnector;
import com.web.newstech.shared.config.NewsTechProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Orquestra um ciclo de coleta: escolhe as fontes devidas, chama o conector de cada
 * uma, deduplica e persiste.
 *
 * <p>Falha de uma fonte nao derruba o ciclo. Uma fonte fora do ar nao pode impedir
 * as outras trinta de serem coletadas.
 */
@Slf4j
@Service
public class IngestService {

	private final SourceRepository sourceRepository;
	private final RawItemRepository rawItemRepository;
	private final NewsTechProperties properties;
	private final Map<ConnectorType, SourceConnector> connectors;

	public IngestService(SourceRepository sourceRepository, RawItemRepository rawItemRepository,
			NewsTechProperties properties, List<SourceConnector> connectors) {
		this.sourceRepository = sourceRepository;
		this.rawItemRepository = rawItemRepository;
		this.properties = properties;
		this.connectors = new EnumMap<>(ConnectorType.class);
		connectors.forEach(connector -> this.connectors.put(connector.type(), connector));
	}

	public IngestReport collectAll() {
		Instant now = Instant.now();
		List<Source> due = sourceRepository.findByActiveTrue().stream()
				.filter(source -> source.isDueAt(now))
				.toList();

		int items = 0;
		int duplicates = 0;
		int notModified = 0;
		int failures = 0;

		for (Source source : due) {
			try {
				SourceOutcome outcome = collect(source);
				items += outcome.collected();
				duplicates += outcome.duplicates();
				if (outcome.notModified()) {
					notModified++;
				}
			}
			catch (RuntimeException ex) {
				failures++;
				registerFailure(source, ex);
			}
		}

		IngestReport report = new IngestReport(due.size(), items, duplicates, notModified, failures);
		log.info("Coleta concluida: {}", report);
		return report;
	}

	/**
	 * Coleta uma fonte. Propaga excecao para o chamador decidir o backoff - assim este
	 * metodo tambem serve ao "coletar agora" do admin, onde o erro precisa ser visivel.
	 */
	public SourceOutcome collect(Source source) {
		SourceConnector connector = connectors.get(source.getConnectorType());
		if (Objects.isNull(connector)) {
			throw new IllegalStateException(
					"Nenhum conector registrado para %s".formatted(source.getConnectorType()));
		}

		FetchResult result = connector.fetch(source);
		int collected = 0;
		int duplicates = 0;

		if (!result.notModified()) {
			for (FetchedItem item : result.items()) {
				if (persist(source, item)) {
					collected++;
				}
				else {
					duplicates++;
				}
			}
		}

		registerSuccess(source, result);
		return new SourceOutcome(collected, duplicates, result.notModified());
	}

	/**
	 * @return {@code true} se o item era novo e foi gravado
	 */
	private boolean persist(Source source, FetchedItem item) {
		String contentHash = ContentHasher.hash(item.title(), item.url());

		// Checagem em codigo evita a exception no caminho normal; os indices unicos
		// continuam sendo a garantia real, inclusive contra dois ciclos concorrentes.
		if (rawItemRepository.existsBySourceIdAndExternalId(source.getId(), item.externalId())
				|| rawItemRepository.existsByContentHash(contentHash)) {
			return false;
		}

		RawItem rawItem = RawItem.builder()
				.sourceId(source.getId())
				.externalId(item.externalId())
				.title(item.title())
				.url(item.url())
				// Feed sem data e comum. Cair para "agora" mantem o item na janela do pipeline
				// em vez de descarta-lo por falta de metadado da fonte.
				.publishedAt(Objects.requireNonNullElseGet(item.publishedAt(), Instant::now))
				.summary(item.summary())
				.contentHash(contentHash)
				.status(RawItemStatus.COLLECTED)
				.fetchedAt(Instant.now())
				.build();

		try {
			rawItemRepository.save(rawItem);
			return true;
		}
		catch (DuplicateKeyException ex) {
			log.debug("Item duplicado barrado pelo indice unico: {}", item.url());
			return false;
		}
	}

	private void registerSuccess(Source source, FetchResult result) {
		if (!result.notModified()) {
			source.setEtag(result.etag());
			source.setLastModified(result.lastModified());
		}
		source.setLastFetchedAt(Instant.now());
		source.setConsecutiveFailures(0);
		source.setNextAttemptAt(null);
		sourceRepository.save(source);
	}

	private void registerFailure(Source source, RuntimeException ex) {
		int failures = source.getConsecutiveFailures() + 1;
		source.setConsecutiveFailures(failures);
		source.setNextAttemptAt(Instant.now().plus(backoffFor(failures)));
		sourceRepository.save(source);

		log.warn("Falha ao coletar '{}' ({}a seguida). Proxima tentativa em {}: {}",
				source.getName(), failures, source.getNextAttemptAt(), ex.getMessage());
	}

	private Duration backoffFor(int consecutiveFailures) {
		Duration ceiling = Duration.ofHours(properties.ingest().backoffMaxHours());
		long minutes = (long) properties.ingest().backoffBaseMinutes() * (1L << Math.min(consecutiveFailures - 1, 16));
		Duration backoff = Duration.ofMinutes(minutes);
		return backoff.compareTo(ceiling) > 0 ? ceiling : backoff;
	}

	public record SourceOutcome(int collected, int duplicates, boolean notModified) {
	}

}
