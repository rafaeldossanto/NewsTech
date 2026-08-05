package com.web.newstech.ingest.connector;

import com.web.newstech.ingest.ConnectorType;
import com.web.newstech.ingest.Source;

/**
 * Porta de coleta. Cada tipo de fonte tem uma implementacao registrada como bean;
 * o {@code IngestService} resolve qual usar pelo {@link ConnectorType} da fonte.
 *
 * <p>E o ponto de extensao que faz o X.com ser uma classe nova na fase 5, e nao
 * uma refatoracao do pipeline.
 */
public interface SourceConnector {

	ConnectorType type();

	/**
	 * Coleta os itens disponiveis. Deve enviar os cabecalhos condicionais a partir de
	 * {@code source.getEtag()} / {@code source.getLastModified()} e devolver os novos
	 * valores no {@link FetchResult}.
	 *
	 * @throws FetchException para qualquer falha de rede ou parsing - o chamador trata
	 *                        aplicando backoff na fonte
	 */
	FetchResult fetch(Source source);

}
