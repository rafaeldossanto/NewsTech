package com.web.newstech.curation.triage;

import com.web.newstech.curation.exceptions.TriageException;
import com.web.newstech.curation.exceptions.TriageRefusedException;
import com.web.newstech.ingest.RawItem;

/**
 * Porta do estagio 1, simetrica ao EditorialModel do estagio 2.
 *
 * <p>Separar a chamada ao modelo da orquestracao e o que permite exercitar o pipeline
 * completo - coleta, triagem, cluster e curadoria - sem gastar API.
 */
public interface TriageModel {

	/**
	 * @throws TriageRefusedException quando o modelo se recusa a responder
	 * @throws TriageException        para falha de comunicacao ou resposta inutilizavel
	 */
	TriageOutcome classify(RawItem item);

}
