package com.web.newstech.ingest.model;

public record IngestReport(
		int sourcesProcessed,
		 int itemsCollected,
		int duplicatesSkipped,
		int notModified,
		int failures
) {

	public static IngestReport empty() {
		return new IngestReport(0, 0, 0, 0, 0);
	}

}
