package com.web.newstech;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Sobe o contexto inteiro contra um MongoDB real. Cobre, de quebra, o
 * {@code MongoBootstrap}: se a criacao de indices ou o validador da colecao
 * {@code stories} estiver quebrado, este teste falha.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class NewsTechApplicationIT {

	@Test
	void contextLoads() {
	}

}
