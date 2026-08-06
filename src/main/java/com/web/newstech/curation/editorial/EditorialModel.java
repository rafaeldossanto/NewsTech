package com.web.newstech.curation.editorial;

import com.web.newstech.curation.cluster.ItemCluster;

/**
 * Porta do estágio 2.
 *
 * <p>Existe para separar a orquestração — ler cluster, gravar story, atualizar itens —
 * da chamada ao modelo. É o que permite testar o pipeline inteiro com decisões gravadas,
 * sem gastar API a cada execução do CI, e o que deixa trocar de modelo sem tocar em
 * mais nada.
 */
public interface EditorialModel {

	/**
	 * @throws ModelRefusedException quando o modelo se recusa a responder — caso plausível
	 *                               aqui, já que notícia de cibersegurança descreve ataque
	 * @throws EditorialException    para falhas de comunicação ou resposta inutilizável
	 */
	EditorialOutcome decide(ItemCluster cluster);

}
