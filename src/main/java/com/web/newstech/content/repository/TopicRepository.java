package com.web.newstech.content.repository;

import com.web.newstech.content.Topic;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TopicRepository extends MongoRepository<Topic, String> {

	Optional<Topic> findBySlug(String slug);

	List<Topic> findByActiveTrueOrderByDisplayOrderAsc();

}
