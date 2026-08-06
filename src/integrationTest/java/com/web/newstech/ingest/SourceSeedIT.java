package com.web.newstech.ingest;

import com.web.newstech.TestcontainersConfiguration;
import com.web.newstech.ingest.repository.SourceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confere que o seed carrega e que as fontes desativadas continuam desativadas.
 *
 * <p>O segundo ponto importa mais do que parece: se o seeder ligasse tudo, as tres
 * fontes que sabidamente nao respondem voltariam a ser consultadas a cada ciclo,
 * acumulando falha e ruido no log para sempre.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SourceSeedIT {

	@Autowired
	private SourceRepository sourceRepository;

	@Autowired
	private tools.jackson.databind.ObjectMapper objectMapper;

	@Test
	@DisplayName("seed cadastra as fontes do arquivo")
	void carregaSeed() {
		List<Source> all = sourceRepository.findAll();

		assertThat(all).hasSizeGreaterThanOrEqualTo(25);
		assertThat(all).allSatisfy(source -> {
			assertThat(source.getFeedUrl()).startsWith("http");
			assertThat(source.getName()).isNotBlank();
			assertThat(source.getConnectorType()).isNotNull();
		});
	}

	@Test
	@DisplayName("fontes com feed comprovadamente quebrado nascem desativadas e com motivo")
	void mantemDesativadasAsFontesQuebradas() {
		List<Source> inativas = sourceRepository.findAll().stream()
				.filter(source -> !source.isActive())
				.toList();

		assertThat(inativas)
				.isNotEmpty()
				.allSatisfy(source -> assertThat(source.getNote())
						.as("fonte desativada precisa registrar o motivo")
						.isNotBlank());

		assertThat(sourceRepository.findByActiveTrue())
				.extracting(Source::getName)
				.doesNotContain("Meta AI Blog", "InfoWorld", "InfoQ Brasil");
	}

	@Test
	@DisplayName("rodar o seed de novo nao duplica nem sobrescreve ajuste manual")
	void seedEIdempotente() throws Exception {
		Source alvo = sourceRepository.findByActiveTrue().getFirst();
		long antes = sourceRepository.count();

		// Simula um ajuste feito pelo operador no admin.
		alvo.setTrustWeight(13);
		sourceRepository.save(alvo);

		new SourceSeeder(sourceRepository, objectMapper).afterPropertiesSet();

		assertThat(sourceRepository.count()).isEqualTo(antes);
		assertThat(sourceRepository.findByFeedUrl(alvo.getFeedUrl()))
				.get()
				.extracting(Source::getTrustWeight)
				.as("o seed nao pode desfazer o que foi ajustado no admin")
				.isEqualTo(13);
	}

}
