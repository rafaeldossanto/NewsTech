package com.web.newstech.curation.triage;

public record TriageOutcome(

		TriageResult result,
		String model,
		long inputTokens,
		long outputTokens,
		long cachedInputTokens) {

}
