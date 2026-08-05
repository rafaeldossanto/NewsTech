package com.web.newstech.ingest;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SourceRepository extends MongoRepository<Source, String> {

	Optional<Source> findByFeedUrl(String feedUrl);

	/**
	 * O filtro de backoff fica em {@code Source.isDueAt}, em memoria, e nao numa derived
	 * query: sao dezenas de fontes, nao milhares, e a regra fica legivel num lugar so.
	 */
	List<Source> findByActiveTrue();

}
