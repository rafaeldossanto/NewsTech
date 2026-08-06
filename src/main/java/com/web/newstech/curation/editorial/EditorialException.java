package com.web.newstech.curation.editorial;

/** Falha ao produzir a decisão editorial de um cluster. Não interrompe o ciclo. */
public class EditorialException extends RuntimeException {

	public EditorialException(String message) {
		super(message);
	}

	public EditorialException(String message, Throwable cause) {
		super(message, cause);
	}

}
