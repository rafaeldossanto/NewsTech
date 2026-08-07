package com.web.newstech.shared.cost;

import java.util.List;

public record CostReport(

		int days,
		List<ModelUsage> usages,
		double total,
		long storiesPublished,
		long itemsTriaged) {

	/** Projecao mensal simples a partir do periodo observado. */
	public double projectedMonthly() {
		return days == 0 ? 0 : total / days * 30;
	}

	public double costPerStory() {
		return storiesPublished == 0 ? 0 : total / storiesPublished;
	}

	public boolean isEmpty() {
		return usages.isEmpty();
	}

}
