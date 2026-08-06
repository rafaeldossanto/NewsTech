package com.web.newstech.web;

import lombok.experimental.UtilityClass;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

@UtilityClass
public class RelativeTime {

	private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

	private static final DateTimeFormatter DIA_MES =
			DateTimeFormatter.ofPattern("d 'de' MMM", Locale.forLanguageTag("pt-BR"));

	public static String format(Instant moment) {
		if (Objects.isNull(moment)) {
			return "";
		}

		Duration passado = Duration.between(moment, Instant.now());

		if (passado.isNegative() || passado.toMinutes() < 1) {
			return "agora";
		}
		if (passado.toMinutes() < 60) {
			return "há " + passado.toMinutes() + " min";
		}
		if (passado.toHours() < 24) {
			return "há " + passado.toHours() + "h";
		}
		if (passado.toDays() == 1) {
			return "ontem";
		}
		if (passado.toDays() < 7) {
			return "há " + passado.toDays() + " dias";
		}
		return DIA_MES.format(moment.atZone(FUSO));
	}

}
