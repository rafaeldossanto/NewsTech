package com.web.newstech.content;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TrackedEntityRepository extends MongoRepository<TrackedEntity, String> {

	Optional<TrackedEntity> findBySlug(String slug);

	/**
	 * Resolve o texto livre que o modelo devolveu ("@AnthropicAI") para a entidade canonica.
	 * Usa {@code idx_aliases}.
	 */
	Optional<TrackedEntity> findByAliasesContainingIgnoreCase(String alias);

	List<TrackedEntity> findByType(TrackedEntity.EntityType type);

}
