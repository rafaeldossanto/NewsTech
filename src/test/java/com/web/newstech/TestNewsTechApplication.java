package com.web.newstech;

import org.springframework.boot.SpringApplication;

/**
 * Sobe a aplicacao com o MongoDB do Testcontainers, sem precisar de docker compose na mao.
 * Util para desenvolver o front sem infra local: {@code ./gradlew bootTestRun}.
 */
public class TestNewsTechApplication {

	public static void main(String[] args) {
		SpringApplication.from(NewsTechApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
