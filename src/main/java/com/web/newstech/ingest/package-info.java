/**
 * Coleta de conteudo das fontes externas.
 *
 * <p>Responsabilidades: conectores de fonte ({@code SourceConnector} e implementacoes),
 * agendamento da coleta, fetch condicional (ETag / If-Modified-Since) e deduplicacao
 * por {@code contentHash}.
 *
 * <p>E o modulo da base da pilha: nao depende de nenhum outro modulo do projeto.
 * A porta {@code SourceConnector} e o que permite adicionar o X.com na fase 5
 * como classe nova, sem refatorar o pipeline.
 */
package com.web.newstech.ingest;
