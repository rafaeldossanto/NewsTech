package com.web.newstech.curation;

public class TriageException extends RuntimeException {

	public TriageException(String message) {
		super(message);
	}

	public TriageException(String message, Throwable cause) {
		super(message, cause);
	}

}
