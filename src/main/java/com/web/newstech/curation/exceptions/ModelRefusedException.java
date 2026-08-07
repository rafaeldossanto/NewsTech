package com.web.newstech.curation.exceptions;

/**
 * O modelo recusou-se a responder.
 *
 * <p>Não é erro de código nem de rede: é uma resposta legítima da API, com
 * {@code stop_reason} igual a {@code refusal}. Num portal que cobre segurança da
 * informação isso vai acontecer — matéria sobre vulnerabilidade descreve ataque.
 *
 * <p>Tem tipo próprio para o pipeline poder distinguir "não deu para publicar isto"
 * de "algo quebrou", e mandar o item para revisão humana em vez de tentar de novo.
 */
public class ModelRefusedException extends RuntimeException {

	public ModelRefusedException(String message) {
		super(message);
	}

}
