package com.web.newstech.curation.editorial;

/**
 * A decisão do modelo junto com o que ela custou.
 *
 * <p>Custo volta com a decisão, e não guardado em campo do serviço, porque estado
 * mutável compartilhado num objeto de aplicação é a forma mais fácil de atribuir o
 * consumo de uma chamada à peça errada.
 */
public record EditorialOutcome(

		EditorialDecision decision,
		String model,
		long inputTokens,
		long outputTokens,
		long cachedInputTokens) {

}
