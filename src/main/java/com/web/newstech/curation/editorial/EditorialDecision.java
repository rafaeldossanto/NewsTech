package com.web.newstech.curation.editorial;

import com.web.newstech.content.Importance;

import java.util.List;

/**
 * Saída estruturada do estágio 2: a decisão editorial <em>e</em> o texto publicável,
 * numa chamada só.
 *
 * <p>Fundir as duas coisas foi decisão de projeto. Uma chamada separada só para escrever
 * teria de reenviar todo o contexto do cluster — mais caro que o punhado de tokens de
 * saída que o resumo custa aqui — e abriria espaço para um segundo modelo parafrasear
 * errado um dado que o primeiro já tinha entendido.
 *
 * <p>O schema enviado ao modelo é derivado deste record, então mudar um campo aqui muda
 * o contrato com o modelo.
 */
public record EditorialDecision(

		/** Manchete da peça. Texto novo, não o título da fonte. */
		String headline,

		/** Duas a três frases. Nunca o texto integral de terceiros. */
		String summary,

		/** Uma frase de contexto sobre por que isso importa. Pode vir vazio. */
		String angle,

		/** Onde a peça aparece no portal. */
		Importance importance,

		/** Slugs da taxonomia, escolhidos do conjunto fechado que o prompt informa. */
		List<String> topics,

		/** Slugs de entidades acompanhadas, também do conjunto fechado. */
		List<String> entities,

		/**
		 * Posições (base 1) dos itens do cluster que realmente compõem esta peça.
		 *
		 * <p>Existe porque o agrupamento é heurístico e pode juntar demais. Sem esta
		 * saída, um item pescado por engano viraria fonte de uma notícia com a qual não
		 * tem relação — e o crédito, que é a base do modelo do portal, ficaria errado.
		 */
		List<Integer> itemsUsed,

		/** Preenchido quando o modelo considera que o cluster não rende peça publicável. */
		String rejectionReason) {

	public boolean rejected() {
		return rejectionReason != null && !rejectionReason.isBlank();
	}

}
