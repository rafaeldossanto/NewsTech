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

/**
 * Peca publicada. E o unico documento que o portal exibe.
 *
 * <p>O {@code summary} e texto proprio, produzido pelo estagio 2 a partir do que as
 * fontes publicam - nunca o texto integral de terceiros. E o que mantem o projeto
 * no modelo de agregador, e nao de republicador.
 *
 * <p>O invariante "nenhuma story sem fonte" e garantido em duas camadas: o
 * {@code @NotEmpty} abaixo, e o validador {@code $jsonSchema} da colecao, que vale
 * tambem para escrita que nao passa pela aplicacao.
 */
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

	/** Resumo proprio, curto. O limite real vem do layout e e imposto no prompt. */
	@NotBlank
	private String summary;

	/** Por que isso importa - uma frase de contexto, opcional. */
	private String angle;

	@NotNull
	private Importance importance;

	@NotBlank
	private String slug;

	@NotNull
	private Instant publishedAt;

	@Builder.Default
	private List<String> topics = List.of();

	/** Slugs de entidades citadas. Alimenta os hubs por empresa. */
	@Builder.Default
	private List<String> entities = List.of();

	@Valid
	@NotEmpty(message = "toda story precisa de ao menos uma fonte")
	private List<StorySource> sources;

	/** Rastreabilidade para os itens brutos que originaram esta peca. */
	@Builder.Default
	private List<String> rawItemIds = List.of();

	private String model;

	private long inputTokens;

	private long outputTokens;

	private long cachedInputTokens;

}
