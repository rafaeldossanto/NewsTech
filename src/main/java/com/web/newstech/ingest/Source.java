package com.web.newstech.ingest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
 * Uma fonte monitorada: blog oficial, veiculo de imprensa, feed de releases.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sources")
public class Source {

	@Id
	private String id;

	@NotBlank
	private String name;

	/** Chave natural da fonte - indice unico em {@code uk_feedUrl}. */
	@NotBlank
	private String feedUrl;

	@NotNull
	private ConnectorType connectorType;

	/**
	 * Confiabilidade de 0 a 100. Usado como desempate editorial: quando duas fontes
	 * contam o mesmo fato, a de maior peso vira o link principal da story.
	 */
	@Min(0)
	@Max(100)
	@Builder.Default
	private int trustWeight = 50;

	@Builder.Default
	private boolean active = true;

	/** Cabecalhos da ultima resposta, reenviados como If-None-Match / If-Modified-Since. */
	private String etag;

	private String lastModified;

	private Instant lastFetchedAt;

	/**
	 * Falhas seguidas. Zera a cada coleta bem sucedida e alimenta o backoff exponencial -
	 * fonte fora do ar para de ser consultada a cada 10 minutos.
	 */
	@Builder.Default
	private int consecutiveFailures = 0;

	/** Enquanto for futuro, o agendador pula esta fonte. */
	private Instant nextAttemptAt;

	/**
	 * Observacao operacional livre. Serve principalmente para registrar por que uma
	 * fonte esta desativada - sem isso, seis meses depois ninguem lembra se o feed
	 * estava quebrado ou se foi decisao editorial.
	 */
	private String note;

	public boolean isDueAt(Instant moment) {
		return active && (nextAttemptAt == null || !nextAttemptAt.isAfter(moment));
	}

}
