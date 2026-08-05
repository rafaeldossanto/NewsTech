package com.web.newstech.ingest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Item bruto coletado de uma fonte. E insumo do pipeline, nao conteudo publicavel:
 * expira por TTL em {@code fetchedAt} e nunca e exibido no portal.
 *
 * <p>O {@code summary} guarda apenas o resumo que a propria fonte publica no feed,
 * ja sanitizado. O texto integral do artigo nao e coletado nem persistido.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "rawItems")
public class RawItem {

	@Id
	private String id;

	@NotBlank
	private String sourceId;

	/** Identificador do item na fonte (guid do RSS, id da API). Unico junto com sourceId. */
	@NotBlank
	private String externalId;

	@NotBlank
	private String title;

	@NotBlank
	private String url;

	private Instant publishedAt;

	/** Resumo publicado pela propria fonte, com o HTML ja removido. */
	private String summary;

	/**
	 * Hash do titulo normalizado + url canonica. Indice unico global: o mesmo item
	 * republicado ou vindo por dois caminhos entra uma vez so.
	 */
	@NotBlank
	private String contentHash;

	@NotNull
	@Builder.Default
	private RawItemStatus status = RawItemStatus.COLLECTED;

	/** Base do TTL. Nao confundir com publishedAt, que e a data declarada pela fonte. */
	@NotNull
	private Instant fetchedAt;

	/** Preenchido pelo estagio 1. Nulo enquanto o item estiver em COLLECTED. */
	private Triage triage;

	/** Id da story que este item ajudou a compor, quando ja publicado. */
	private String storyId;

}
