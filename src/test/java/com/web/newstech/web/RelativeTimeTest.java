package com.web.newstech.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RelativeTimeTest {

	@Test
	@DisplayName("menos de um minuto vira 'agora'")
	void agora() {
		assertThat(RelativeTime.format(Instant.now().minusSeconds(20))).isEqualTo("agora");
	}

	@Test
	@DisplayName("minutos e horas usam a forma curta")
	void minutosEHoras() {
		assertThat(RelativeTime.format(Instant.now().minus(Duration.ofMinutes(25)))).isEqualTo("há 25 min");
		assertThat(RelativeTime.format(Instant.now().minus(Duration.ofHours(6)))).isEqualTo("há 6h");
	}

	@Test
	@DisplayName("um dia vira 'ontem'")
	void ontem() {
		assertThat(RelativeTime.format(Instant.now().minus(Duration.ofHours(30)))).isEqualTo("ontem");
	}

	@Test
	@DisplayName("dentro da semana conta em dias")
	void dias() {
		assertThat(RelativeTime.format(Instant.now().minus(Duration.ofDays(4)))).isEqualTo("há 4 dias");
	}

	@Test
	@DisplayName("passada uma semana a data absoluta volta a ser mais util")
	void dataAbsoluta() {
		String saida = RelativeTime.format(Instant.now().minus(Duration.ofDays(40)));

		assertThat(saida).doesNotContain("há").matches(".*\\d+ de \\w+.*");
	}

	@Test
	@DisplayName("relogio da fonte adiantado nao vira tempo negativo")
	void toleraDataNoFuturo() {
		assertThat(RelativeTime.format(Instant.now().plus(Duration.ofMinutes(10)))).isEqualTo("agora");
	}

	@Test
	@DisplayName("data ausente nao quebra o template")
	void toleraNulo() {
		assertThat(RelativeTime.format(null)).isEmpty();
	}

}
