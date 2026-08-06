package com.web.newstech.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SlugsTest {

	@Test
	@DisplayName("remove acento, pontuação e caixa")
	void normaliza() {
		assertThat(Slugs.from("Modelos de IA usaram identidades falsas em teste do Reino Unido"))
				.isEqualTo("modelos-de-ia-usaram-identidades-falsas-em-teste-do-reino-unido");
		assertThat(Slugs.from("Rust 1.97.1 corrige miscompilação!")).isEqualTo("rust-1-97-1-corrige-miscompilacao");
	}

	@Test
	@DisplayName("corta na palavra inteira, não no meio dela")
	void cortaEmPalavraInteira() {
		String longo = "Uma manchete extremamente longa que passa do limite estabelecido e "
				+ "precisa ser cortada em algum ponto razoavel para continuar legivel";

		String slug = Slugs.from(longo);

		assertThat(slug).hasSizeLessThanOrEqualTo(80).doesNotEndWith("-");
		assertThat(longo.toLowerCase().replace(" ", "-")).startsWith(slug);
	}

	@Test
	@DisplayName("acrescenta sufixo quando o slug já existe")
	void garanteUnicidade() {
		Set<String> existentes = Set.of("rust-1-97-1", "rust-1-97-1-2");

		assertThat(Slugs.unique("Rust 1.97.1", existentes::contains)).isEqualTo("rust-1-97-1-3");
	}

	@Test
	@DisplayName("título sem caractere aproveitável ainda gera slug utilizável")
	void tituloDegenerado() {
		assertThat(Slugs.unique("!!! ???", slug -> false)).isEqualTo("peca");
		assertThat(Slugs.from("")).isEmpty();
		assertThat(Slugs.from(null)).isEmpty();
	}

}
