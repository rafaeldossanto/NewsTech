package com.web.newstech.ingest;

import java.time.Instant;
import java.util.List;

/**
 * Resultado do estagio 1 do pipeline, embutido no proprio {@link RawItem}.
 *
 * <p>Embutido e nao em colecao separada porque a relacao e 1:1 e o dado nunca e
 * consultado sozinho - separar so geraria um lookup por leitura.
 *
 * <p>Guarda modelo e tokens usados: e a materia-prima do painel de custo, e permite
 * reprocessar o arquivo sabendo qual versao do modelo classificou o que.
 */
public record Triage(

		List<String> topics,

		List<String> entities,

		String language,

		/** 0 a 100. Abaixo do corte configurado, o item vai para DISCARDED. */
		int relevanceScore,

		/** Justificativa curta do modelo - serve para calibrar o corte lendo casos reais. */
		String reasoning,

		String model,

		long inputTokens,

		long outputTokens,

		long cachedInputTokens,

		Instant triagedAt) {

}
