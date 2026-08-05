package com.web.newstech.web;

import com.web.newstech.content.Importance;
import com.web.newstech.content.Story;
import com.web.newstech.content.StorySource;

import java.util.List;
import java.util.Objects;

/**
 * A story como o template precisa dela: horario ja formatado, fonte principal
 * resolvida e o resto do que a pagina exibe.
 *
 * <p>Existe para o Thymeleaf nao ter que chamar logica no meio do HTML - o template
 * so escreve valor, o que mantem a regra em Java, onde da para testar.
 */
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
