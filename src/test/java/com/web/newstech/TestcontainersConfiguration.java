package com.web.newstech;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Fica em {@code src/test} (e nao em {@code src/integrationTest}) de proposito:
 * o sourceSet de integracao enxerga a saida de test, entao os testes de integracao
 * reusam esta configuracao e o {@code bootTestRun} continua funcionando.
 *
 * <p>Imagem fixada em vez de {@code mongo:latest} para o teste nao mudar de comportamento
 * sozinho quando a tag latest for atualizada.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	MongoDBContainer mongoDbContainer() {
		return new MongoDBContainer(DockerImageName.parse("mongo:8.0"));
	}

}
