package com.web.newstech.shared;

import lombok.experimental.UtilityClass;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Gera o identificador que vai na URL.
 *
 * <p>O slug é permanente: uma vez publicado e indexado, mudá-lo quebra link e perde o
 * que os buscadores já conheciam. Por isso ele nasce da manchete e não é recalculado
 * quando a peça é editada.
 */
@UtilityClass
public class Slugs {

	private static final int MAX_LENGTH = 80;

	public static String from(String text) {
		if (Objects.isNull(text) || text.isBlank()) {
			return "";
		}

		String slug = Normalizer.normalize(text, Normalizer.Form.NFD)
				.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
				.toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9]+", "-")
				.replaceAll("^-+|-+$", "");

		if (slug.length() <= MAX_LENGTH) {
			return slug;
		}

		// Corta na última palavra inteira: slug terminado em palavra picada fica ilegível
		// justamente onde ele é lido, que é a barra de endereço.
		String cortado = slug.substring(0, MAX_LENGTH);
		int ultimoHifen = cortado.lastIndexOf('-');
		return ultimoHifen > MAX_LENGTH / 2 ? cortado.substring(0, ultimoHifen) : cortado;
	}

	/**
	 * Garante unicidade acrescentando sufixo numérico.
	 *
	 * @param exists teste de existência, normalmente o repositório
	 */
	public static String unique(String text, Predicate<String> exists) {
		String base = from(text);
		if (base.isBlank()) {
			base = "peca";
		}
		if (!exists.test(base)) {
			return base;
		}
		for (int i = 2; i < 100; i++) {
			String candidato = base + "-" + i;
			if (!exists.test(candidato)) {
				return candidato;
			}
		}
		return base + "-" + System.currentTimeMillis();
	}

}
