package com.web.newstech.curation;

public record TriageOutcome(

		TriageResult result,
		String model,
		long inputTokens,
		long outputTokens,
		long cachedInputTokens) {

}
