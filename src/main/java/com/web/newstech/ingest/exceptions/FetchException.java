package com.web.newstech.ingest.exceptions;

/**
 * Falha ao coletar de uma fonte. Nao interrompe o ciclo: o {@code IngestService}
 * registra, aplica backoff naquela fonte e segue para as demais.
 */
public class FetchException extends RuntimeException {

	public FetchException(String message) {
		super(message);
	}

	public FetchException(String message, Throwable cause) {
		super(message, cause);
	}

}
