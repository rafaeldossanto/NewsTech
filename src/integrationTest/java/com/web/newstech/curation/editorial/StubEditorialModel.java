package com.web.newstech.curation.editorial;

import com.web.newstech.curation.cluster.ItemCluster;

import java.util.function.Function;

/**
 * Dublê da porta {@link EditorialModel} para os testes.
 *
 * <p>É o que permite exercitar o estágio editorial inteiro sem gastar API: o teste
 * grava a decisão que o modelo devolveria, ou o erro que ele lançaria, e verifica o
 * que a orquestração faz com isso.
 *
 * <p>Classe própria, e não interna a um teste, para poder ser reusada pelos testes de
 * pipeline ponta a ponta sem duplicar o dublê.
 */
public class StubEditorialModel implements EditorialModel {

	private Function<ItemCluster, EditorialDecision> resposta;
	private RuntimeException erro;

	/** Define a decisão que a próxima chamada devolve. */
	public void responder(Function<ItemCluster, EditorialDecision> resposta) {
		this.resposta = resposta;
		this.erro = null;
	}

	/** Define o erro que a próxima chamada lança — recusa, truncamento, falha de rede. */
	public void lancar(RuntimeException erro) {
		this.erro = erro;
		this.resposta = null;
	}

	@Override
	public EditorialOutcome decide(ItemCluster cluster) {
		if (erro != null) {
			throw erro;
		}
		if (resposta == null) {
			throw new IllegalStateException("O teste não definiu resposta nem erro para o dublê");
		}
		return new EditorialOutcome(resposta.apply(cluster), "stub", 1000, 200, 800);
	}

}
