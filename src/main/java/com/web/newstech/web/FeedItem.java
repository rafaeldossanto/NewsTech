package com.web.newstech.web;

import com.web.newstech.authoring.Article;
import com.web.newstech.content.Importance;
import com.web.newstech.content.Story;
import com.web.newstech.content.StorySource;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Entrada da linha do tempo, vinda de uma peca curada ou de um artigo assinado.
 *
 * <p>Os dois convivem na mesma lista, mas o leitor precisa distinguir na hora: peca
 * agregada mostra FONTE, artigo mostra AUTOR. Sem isso alguem pode achar que o portal
 * assina conteudo de terceiros, ou que um artigo de opiniao e apuracao da redacao.
 */
public record FeedItem(

		Kind kind,
		String title,
		String summary,
		String href,
		String byline,
		String bylineHref,
		int sourceCount,
		boolean headliner,
		String relativeTime,
		String publishedAtIso,
		Instant publishedAt) {

	public enum Kind {
		CURADORIA,
		ARTIGO
	}

	public boolean isArticle() {
		return kind == Kind.ARTIGO;
	}

	public static FeedItem of(Story story) {
		List<StorySource> fontes = Objects.requireNonNullElse(story.getSources(), List.of());
		return new FeedItem(
				Kind.CURADORIA,
				story.getHeadline(),
				story.getSummary(),
				"/n/" + story.getSlug(),
				fontes.isEmpty() ? "" : fontes.getFirst().sourceName(),
				null,
				fontes.size(),
				story.getImportance() == Importance.MANCHETE,
				RelativeTime.format(story.getPublishedAt()),
				Objects.toString(story.getPublishedAt(), ""),
				story.getPublishedAt());
	}

	public static FeedItem of(Article article) {
		return new FeedItem(
				Kind.ARTIGO,
				article.getTitle(),
				article.getSubtitle(),
				"/artigo/" + article.getSlug(),
				Objects.toString(article.getAuthorDisplayName(), article.getAuthorUsername()),
				"/autor/" + article.getAuthorUsername(),
				1,
				false,
				RelativeTime.format(article.getPublishedAt()),
				Objects.toString(article.getPublishedAt(), ""),
				article.getPublishedAt());
	}

	/** Intercala os dois tipos por data, do mais recente para o mais antigo. */
	public static List<FeedItem> merge(List<Story> stories, List<Article> articles, int limit) {
		return Stream.concat(stories.stream().map(FeedItem::of), articles.stream().map(FeedItem::of))
				.filter(item -> Objects.nonNull(item.publishedAt()))
				.sorted(Comparator.comparing(FeedItem::publishedAt).reversed())
				.limit(limit)
				.toList();
	}

}
