package com.web.newstech.ingest;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface RawItemRepository extends MongoRepository<RawItem, String> {

	boolean existsByContentHash(String contentHash);

	boolean existsBySourceIdAndExternalId(String sourceId, String externalId);

	/** Fila do estagio 1. Usa o indice {@code idx_status_publishedAt}. */
	List<RawItem> findByStatusOrderByPublishedAtDesc(RawItemStatus status, Pageable pageable);

	/**
	 * Candidatos a clusterizacao: ja triados e dentro da janela temporal.
	 * O agrupamento heuristico roda sobre este conjunto, sem custo de API.
	 */
	List<RawItem> findByStatusAndPublishedAtAfter(RawItemStatus status, Instant since);

	long countByStatus(RawItemStatus status);

}
