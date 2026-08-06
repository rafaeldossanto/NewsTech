package com.web.newstech.ingest.enums;

/**
 * Tipo de conector que sabe coletar de uma fonte.
 *
 * <p>Persistido em {@code sources.connectorType}, entao os nomes das constantes sao contrato:
 * renomear exige migracao dos documentos existentes.
 */
public enum ConnectorType {

	/** Feeds RSS e Atom. Cobre a maioria dos blogs oficiais e da imprensa de tecnologia. */
	RSS,

	/** APIs que devolvem JSON, como a do Algolia para o Hacker News. */
	JSON_API,

	/** Releases de repositorios do GitHub - novas versoes de linguagens e frameworks. */
	GITHUB_RELEASES,

	/** Posts do X.com. Fase 5: a constante ja existe para o enum nao virar migracao depois. */
	X

}
