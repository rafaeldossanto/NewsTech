package com.web.newstech.web;

import com.web.newstech.content.Importance;
import com.web.newstech.content.Story;
import com.web.newstech.content.StorySource;

import java.util.List;
import java.util.Objects;

public record StoryView(

		String slug,
		String headline,
		String summary,
		String angle,
		String mainSourceName,
		int sourceCount,
		String relativeTime,
		String publishedAtIso,
		boolean headliner,
		List<String> topics,
		List<String> entities,
		List<StorySource> sources) {

	public static StoryView from(Story story) {
		List<StorySource> fontes = Objects.requireNonNullElse(story.getSources(), List.of());

		return new StoryView(
				story.getSlug(),
				story.getHeadline(),
				story.getSummary(),
				story.getAngle(),
				fontes.isEmpty() ? "" : fontes.getFirst().sourceName(),
				fontes.size(),
				RelativeTime.format(story.getPublishedAt()),
				Objects.toString(story.getPublishedAt(), ""),
				story.getImportance() == Importance.MANCHETE,
				Objects.requireNonNullElse(story.getTopics(), List.of()),
				Objects.requireNonNullElse(story.getEntities(), List.of()),
				fontes);
	}

	public static List<StoryView> from(List<Story> stories) {
		return stories.stream().map(StoryView::from).toList();
	}

}
