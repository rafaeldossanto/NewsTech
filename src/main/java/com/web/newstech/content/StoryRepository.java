package com.web.newstech.content;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StoryRepository extends MongoRepository<Story, String> {

	Optional<Story> findBySlug(String slug);

	boolean existsBySlug(String slug);

	/** Home: faixas por importancia. Usa {@code idx_publishedAt}. */
	List<Story> findByImportanceOrderByPublishedAtDesc(Importance importance, Pageable pageable);

	/** Radar das ultimas 24h. */
	List<Story> findByPublishedAtAfterOrderByPublishedAtDesc(Instant since, Pageable pageable);

	/** Pagina de topico. Usa {@code idx_topics_publishedAt} (multikey). */
	List<Story> findByTopicsContainingOrderByPublishedAtDesc(String topicSlug, Pageable pageable);

	/** Hub de entidade. Usa {@code idx_entities_publishedAt} (multikey). */
	List<Story> findByEntitiesContainingOrderByPublishedAtDesc(String entitySlug, Pageable pageable);

	List<Story> findAllByOrderByPublishedAtDesc(Pageable pageable);

}
