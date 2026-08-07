package com.web.newstech.curation;

import com.web.newstech.ingest.RawItem;

import java.util.function.Function;

/**
 * Duble da porta de triagem. Permite exercitar o pipeline inteiro sem gastar API.
 */
public class StubTriageModel implements TriageModel {

	private Function<RawItem, TriageResult> resposta;
	private RuntimeException erro;

	public void responder(Function<RawItem, TriageResult> resposta) {
		this.resposta = resposta;
		this.erro = null;
	}

	public void lancar(RuntimeException erro) {
		this.erro = erro;
		this.resposta = null;
	}

	@Override
	public TriageOutcome classify(RawItem item) {
		if (erro != null) {
			throw erro;
		}
		if (resposta == null) {
			throw new IllegalStateException("O teste nao definiu resposta nem erro para o duble");
		}
		return new TriageOutcome(resposta.apply(item), "stub-haiku", 300, 60, 200);
	}

}
