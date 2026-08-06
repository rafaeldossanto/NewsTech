package com.web.newstech.curation.cluster;

import com.web.newstech.ingest.ContentHasher;
import lombok.experimental.UtilityClass;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Quebra um titulo nas duas coisas que interessam para comparar noticias:
 * as palavras que carregam significado e os numeros de versao.
 */
@UtilityClass
public class TitleTokenizer {

	/**
	 * Palavras vazias em portugues e ingles juntas, porque as fontes sao mistas e um
	 * titulo em ingles nao pode ser comparado com um conjunto de stopwords em portugues.
	 */
	// Stream.of(...).collect em vez de Set.of(...): as duas listas se sobrepõem ("as" existe
	// nos dois idiomas) e Set.of lança em elemento repetido. Numa lista feita para crescer,
	// isso vira uma armadilha para quem só quer acrescentar uma palavra.
	private static final Set<String> STOPWORDS = Stream.of(
			// portugues
			"a", "as", "ao", "aos", "com", "como", "da", "das", "de", "do", "dos", "e", "em", "entre",
			"na", "nas", "no", "nos", "o", "os", "ou", "para", "pela", "pelo", "por", "que", "se",
			"sem", "sobre", "um", "uma", "sao", "foi", "ser", "tem", "mais", "seu", "sua",
			// ingles
			"the", "of", "to", "in", "on", "at", "by", "for", "with", "from", "and", "or", "but",
			"is", "are", "was", "were", "be", "been", "as", "its", "it", "this", "that", "these",
			"has", "have", "had", "will", "can", "may", "new", "now", "after", "before", "into",
			"out", "over", "under", "than", "then", "not", "all", "one", "two", "you", "your")
			.collect(Collectors.toUnmodifiableSet());

	/** Versoes e numeros: "1.94.0", "27", "3.14.7", "v2". */
	private static final Pattern VERSION = Pattern.compile("\\bv?(\\d+(?:[.+]\\d+)*)\\b");

	/**
	 * Datas no formato ISO, removidas antes de procurar versao.
	 *
	 * <p>Varios feeds de release colocam a data no titulo ("2026-08-05, Version 26.7.0").
	 * Sem tirar a data primeiro, dois releases do mesmo dia ou do mesmo mes compartilham
	 * "2026" e "08" - e a comparacao conclui que as versoes coincidem justamente nos
	 * casos que a regra existe para separar.
	 */
	private static final Pattern ISO_DATE = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");

	/**
	 * Tokens significativos do titulo.
	 *
	 * <p>Reusa a normalizacao do {@link ContentHasher} - a mesma que ja garante que
	 * acento e pontuacao nao dupliquem item na coleta.
	 *
	 * <p>Mantem tokens de duas letras porque "AI", "ML" e "UK" carregam significado
	 * em titulo de tecnologia; o corte de ruido fica por conta das stopwords.
	 */
	public static Set<String> tokens(String title) {
		if (Objects.isNull(title)) {
			return Set.of();
		}
		return Arrays.stream(ContentHasher.normalizeTitle(title).split(" "))
				.filter(token -> token.length() >= 2)
				.filter(token -> !STOPWORDS.contains(token))
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	/**
	 * Numeros de versao, extraidos <strong>antes</strong> da normalizacao - ela quebra
	 * "26.7.0" em tres tokens soltos e a informacao se perde.
	 *
	 * <p>Serve a um caso que so aparece com dados reais: dois releases seguidos da mesma
	 * ferramenta tem titulos quase identicos ("Version 26.7.0" e "Version 26.6.0") e
	 * seriam agrupados como se fossem o mesmo fato. Sao fatos diferentes, e o que os
	 * distingue e exatamente o numero.
	 */
	public static Set<String> versions(String title) {
		if (Objects.isNull(title)) {
			return Set.of();
		}
		String semData = ISO_DATE.matcher(title.toLowerCase(Locale.ROOT)).replaceAll(" ");

		Set<String> found = new LinkedHashSet<>();
		Matcher matcher = VERSION.matcher(semData);
		while (matcher.find()) {
			found.add(matcher.group(1));
		}
		return found;
	}

}
