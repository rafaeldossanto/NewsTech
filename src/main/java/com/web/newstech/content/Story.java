package com.web.newstech.content;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stories")
public class Story {

	@Id
	private String id;

	@NotBlank
	private String headline;

	@NotBlank
	private String summary;

	private String angle;

	@NotNull
	private Importance importance;

	@NotBlank
	private String slug;

	@NotNull
	private Instant publishedAt;

	@Builder.Default
	private List<String> topics = List.of();

	@Builder.Default
	private List<String> entities = List.of();

	@Valid
	@NotEmpty(message = "toda story precisa de ao menos uma fonte")
	private List<StorySource> sources;

	@Builder.Default
	private List<String> rawItemIds = List.of();

	private String model;

	private long inputTokens;

	private long outputTokens;

	private long cachedInputTokens;

}
