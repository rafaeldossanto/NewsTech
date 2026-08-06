package com.web.newstech.content;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record StorySource(

		@NotBlank String sourceName,

		String sourceUrl,

		@NotBlank String articleUrl,

		Instant publishedAt
) {

}
