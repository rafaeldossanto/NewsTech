package com.web.newstech.content;

/**
 * Decisao editorial sobre a peca, tomada pelo estagio 2 do pipeline.
 *
 * <p>Define diretamente onde a story aparece na home, entao e contrato entre
 * o prompt do modelo e o template.
 */
public enum Importance {

	/** Chamada principal da home. Deve ser rara - no maximo uma ou duas por dia. */
	MANCHETE,

	/** Faixa de destaques logo abaixo da manchete. */
	DESTAQUE,

	/** Lista cronologica compacta das ultimas 24h. O grosso do volume vive aqui. */
	RADAR,

	/** Publicado mas fora da home: acessivel por topico, hub de entidade e busca. */
	ARQUIVO

}
