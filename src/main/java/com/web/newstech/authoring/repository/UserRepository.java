package com.web.newstech.authoring.repository;

import com.web.newstech.authoring.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

	Optional<User> findByUsername(String username);

	Optional<User> findByEmailIgnoreCase(String email);

	boolean existsByUsername(String username);

	boolean existsByEmailIgnoreCase(String email);

}
