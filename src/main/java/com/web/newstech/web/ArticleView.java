package com.web.newstech.web;

import com.web.newstech.authoring.Article;

import java.util.List;
import java.util.Objects;

public record ArticleView(

		String slug,
		String title,
		String subtitle,
		String bodyHtml,
		String authorUsername,
		String authorName,
		String relativeTime,
		String publishedAtIso,
		boolean edited,
		List<String> topics) {

	public static ArticleView from(Article article, String bodyHtml) {
		return new ArticleView(
				article.getSlug(),
				article.getTitle(),
				article.getSubtitle(),
				bodyHtml,
				article.getAuthorUsername(),
				Objects.toString(article.getAuthorDisplayName(), article.getAuthorUsername()),
				RelativeTime.format(article.getPublishedAt()),
				Objects.toString(article.getPublishedAt(), ""),
				article.getUpdatedAt() != null && article.getUpdatedAt().isAfter(article.getPublishedAt().plusSeconds(60)),
				Objects.requireNonNullElse(article.getTopics(), List.of()));
	}

	/** Sem corpo renderizado: para listagens, onde o texto completo não é exibido. */
	public static ArticleView summary(Article article) {
		return from(article, "");
	}

	public static List<ArticleView> summaries(List<Article> articles) {
		return articles.stream().map(ArticleView::summary).toList();
	}

}
