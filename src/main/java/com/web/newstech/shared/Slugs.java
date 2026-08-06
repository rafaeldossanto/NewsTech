package com.web.newstech.shared;

import lombok.experimental.UtilityClass;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;


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

		String cortado = slug.substring(0, MAX_LENGTH);
		int ultimoHifen = cortado.lastIndexOf('-');
		return ultimoHifen > MAX_LENGTH / 2 ? cortado.substring(0, ultimoHifen) : cortado;
	}

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
