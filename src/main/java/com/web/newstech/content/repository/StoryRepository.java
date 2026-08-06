package com.web.newstech.content.repository;

import com.web.newstech.content.Importance;
import com.web.newstech.content.Story;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StoryRepository extends MongoRepository<Story, String> {

	Optional<Story> findBySlug(String slug);

	boolean existsBySlug(String slug);

	List<Story> findByImportanceOrderByPublishedAtDesc(Importance importance, Pageable pageable);

	List<Story> findByPublishedAtAfterOrderByPublishedAtDesc(Instant since, Pageable pageable);

	List<Story> findByTopicsContainingOrderByPublishedAtDesc(String topicSlug, Pageable pageable);

	List<Story> findByEntitiesContainingOrderByPublishedAtDesc(String entitySlug, Pageable pageable);

	List<Story> findAllByOrderByPublishedAtDesc(Pageable pageable);

}
