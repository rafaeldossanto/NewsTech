package com.web.newstech.ingest;

import com.web.newstech.ingest.enums.ConnectorType;
import com.web.newstech.ingest.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.List;

/**
 * Carrega a lista inicial de fontes de {@code seed/sources.json}.
 *
 * <p>Faz apenas insercao do que ainda nao existe, comparando por {@code feedUrl}.
 * Fonte ja cadastrada nunca e sobrescrita: caso contrario cada restart apagaria o
 * que foi ajustado no admin - peso alterado, fonte desativada na mao, etag da ultima
 * coleta - e a aplicacao brigaria com o operador.
 *
 * <p>Consequencia disso: mudar o JSON nao atualiza fonte existente. Para reeditar uma
 * fonte ja cadastrada, use o admin.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SourceSeeder implements InitializingBean {

	private static final String SEED_FILE = "seed/sources.json";

	private final SourceRepository sourceRepository;
	private final ObjectMapper objectMapper;

	@Override
	public void afterPropertiesSet() throws Exception {
		List<SeedEntry> entries = read();
		int created = 0;

		for (SeedEntry entry : entries) {
			if (sourceRepository.findByFeedUrl(entry.feedUrl()).isPresent()) {
				continue;
			}
			sourceRepository.save(Source.builder()
					.name(entry.name())
					.feedUrl(entry.feedUrl())
					.connectorType(entry.connectorType())
					.trustWeight(entry.trustWeight())
					.active(entry.active())
					.note(entry.note())
					.build());
			created++;
		}

		if (created > 0) {
			log.info("Seed de fontes: {} novas cadastradas de {} no arquivo", created, entries.size());
		}
	}

	private List<SeedEntry> read() throws Exception {
		try (InputStream stream = new ClassPathResource(SEED_FILE).getInputStream()) {
			// Array em vez de CollectionType: dispensa TypeFactory e nao muda entre versoes do Jackson.
			return List.of(objectMapper.readValue(stream, SeedEntry[].class));
		}
	}

	record SeedEntry(String name, String feedUrl, ConnectorType connectorType, int trustWeight, boolean active,
                     String note) {
	}

}
