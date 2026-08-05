package com.web.newstech.ingest.connector;

import java.util.List;

/**
 * Retorno de uma coleta, incluindo os cabecalhos de cache que devem ser devolvidos
 * a fonte na proxima requisicao.
 *
 * @param notModified resposta 304: a fonte confirmou que nada mudou desde a ultima
 *                    coleta. Nao e erro nem coleta vazia - e o caminho barato e o
 *                    esperado na maioria dos ciclos.
 */
public record FetchResult(List<FetchedItem> items, String etag, String lastModified, boolean notModified) {

	/** Resposta 304: a fonte confirmou que nada mudou. */
	public static FetchResult unchanged() {
		return new FetchResult(List.of(), null, null, true);
	}

	public static FetchResult of(List<FetchedItem> items, String etag, String lastModified) {
		return new FetchResult(items, etag, lastModified, false);
	}

}
