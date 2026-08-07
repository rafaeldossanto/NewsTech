package com.web.newstech.curation.exceptions;

public class TriageException extends RuntimeException {

	public TriageException(String message) {
		super(message);
	}

	public TriageException(String message, Throwable cause) {
		super(message, cause);
	}

}
