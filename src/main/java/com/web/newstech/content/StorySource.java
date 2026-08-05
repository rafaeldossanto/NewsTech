package com.web.newstech.content;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/**
 * Credito a uma fonte, embutido na propria {@link Story}.
 *
 * <p>Embutido e nao referenciado de proposito: a atribuicao e parte do agregado,
 * viaja junto com a peca e e renderizada no template sem nenhum join. E o que
 * torna estruturalmente impossivel publicar sem dar credito.
 */
public record StorySource(

		@NotBlank String sourceName,

		/** Home da fonte - vira o link do selo de credito. */
		String sourceUrl,

		/** Link canonico para o artigo original. E o destino do "leia na fonte". */
		@NotBlank String articleUrl,

		Instant publishedAt) {

}
