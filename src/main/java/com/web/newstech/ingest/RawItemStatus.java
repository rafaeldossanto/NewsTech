package com.web.newstech.ingest;

/**
 * Estagio do item dentro do pipeline.
 *
 * <p>Persistido, portanto contrato. O par {@code (status, publishedAt)} e indexado:
 * a fila do pipeline e sempre "o que esta em COLLECTED, mais recente primeiro".
 */
public enum RawItemStatus {

	/** Coletado do feed, ainda nao passou pela triagem. */
	COLLECTED,

	/** Triado e considerado relevante - aguarda clusterizacao e curadoria. */
	TRIAGED,

	/** Triado e descartado por baixa relevancia. Fica no banco ate o TTL para auditoria do corte. */
	DISCARDED,

	/** Ja compoe uma story publicada. */
	PUBLISHED,

	/** Falha na triagem, ou recusa do modelo. Nao volta para a fila sozinho: exige olhar humano. */
	NEEDS_REVIEW

}
