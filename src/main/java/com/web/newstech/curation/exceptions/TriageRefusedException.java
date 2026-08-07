package com.web.newstech.curation.exceptions;

/**
 * O modelo recusou-se a classificar o item. Nao e erro de codigo: e resposta legitima
 * da API, e o item vai para revisao humana em vez de nova tentativa.
 */
public class TriageRefusedException extends RuntimeException {

	public TriageRefusedException(String message) {
		super(message);
	}

}
