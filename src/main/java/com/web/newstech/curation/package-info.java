/**
 * Pipeline de IA que transforma item bruto em peca publicavel.
 *
 * <p>Dois estagios, deliberadamente duas chamadas e nao tres:
 * <ol>
 *   <li>triagem com Haiku 4.5 - classifica, extrai entidades e pontua relevancia;</li>
 *   <li>curadoria com Opus 5 - recebe o cluster e devolve, numa unica saida estruturada,
 *       a decisao editorial <em>e</em> o resumo final.</li>
 * </ol>
 *
 * <p>A clusterizacao entre os dois estagios e heuristica (Jaccard sobre titulo normalizado
 * + intersecao de entidades, janela de 48h) e nao custa chamada de API.
 *
 * <p>Depende de {@code ingest}.
 */
package com.web.newstech.curation;
