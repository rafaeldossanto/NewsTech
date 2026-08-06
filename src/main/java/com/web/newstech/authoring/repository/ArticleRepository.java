package com.web.newstech.authoring.repository;

import com.web.newstech.authoring.Article;
import com.web.newstech.authoring.enums.ArticleStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ArticleRepository extends MongoRepository<Article, String> {

	Optional<Article> findBySlug(String slug);

	boolean existsBySlug(String slug);

	long countByAuthorIdAndPublishedAtAfter(String authorId, Instant since);

	List<Article> findByAuthorIdOrderByPublishedAtDesc(String authorId);

	List<Article> findByAuthorUsernameAndStatusOrderByPublishedAtDesc(String authorUsername, ArticleStatus status,
			Pageable pageable);

	List<Article> findByStatusOrderByPublishedAtDesc(ArticleStatus status, Pageable pageable);

	List<Article> findByStatusAndHomeEligibleTrueAndPublishedAtAfterOrderByPublishedAtDesc(ArticleStatus status,
			Instant since, Pageable pageable);

	List<Article> findByStatusAndHomeEligibleTrueAndTopicsContainingOrderByPublishedAtDesc(ArticleStatus status,
			String topic, Pageable pageable);

}
