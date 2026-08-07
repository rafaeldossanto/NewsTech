package com.web.newstech.shared.cost;

public record ModelUsage(

		String stage,
		String model,
		long calls,
		long inputTokens,
		long outputTokens,
		long cachedInputTokens,
		double cost) {

	public long totalTokens() {
		return inputTokens + outputTokens + cachedInputTokens;
	}

	/**
	 * Proporcao do input que veio de cache. Numero baixo com prompt estavel e o sinal
	 * de que o cache nao esta pegando - e o que mais encarece a conta em silencio.
	 */
	public int cacheHitPercent() {
		long totalInput = inputTokens + cachedInputTokens;
		return totalInput == 0 ? 0 : (int) Math.round(100.0 * cachedInputTokens / totalInput);
	}

}
