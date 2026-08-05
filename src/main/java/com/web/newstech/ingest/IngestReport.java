package com.web.newstech.ingest;

/**
 * Resultado consolidado de um ciclo de coleta. Serve ao log e, na fase 1, ao admin.
 *
 * @param notModified fontes que responderam 304. Numero alto aqui e sinal de saude:
 *                    significa que os cabecalhos condicionais estao funcionando e o
 *                    portal nao esta baixando o mesmo feed toda vez.
 */
public record IngestReport(int sourcesProcessed, int itemsCollected, int duplicatesSkipped, int notModified,
						   int failures) {

	public static IngestReport empty() {
		return new IngestReport(0, 0, 0, 0, 0);
	}

}
