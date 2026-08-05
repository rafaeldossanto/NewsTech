package com.web.newstech.ingest.connector;

import java.time.Instant;

/**
 * Item como o conector o entrega, antes de virar {@code RawItem}.
 *
 * <p>Existe para isolar o formato de cada fonte do modelo persistido: o que muda quando
 * chega o conector do X.com e apenas como se preenche este record.
 */
public record FetchedItem(

		/** Identificador do item na origem: guid do RSS, id do post, tag da release. */
		String externalId,

		String title,

		String url,

		Instant publishedAt,

		/** Resumo publicado pela fonte, ja sem HTML. */
		String summary) {

}
