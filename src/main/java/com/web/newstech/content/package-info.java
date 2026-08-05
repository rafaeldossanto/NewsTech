/**
 * Modelo editorial publicado: story, topico, entidade e atribuicao.
 *
 * <p>A atribuicao as fontes fica <strong>embutida</strong> em {@code stories.sources[]},
 * nao referenciada. Isso substitui a chave estrangeira que existiria num desenho relacional:
 * o credito e parte do agregado, viaja com a peca e e renderizado sem join.
 *
 * <p>O invariante "nenhuma story publicada sem fonte" e garantido em duas camadas:
 * {@code @NotEmpty} no record e um validador {@code $jsonSchema} na colecao.
 *
 * <p>Depende de {@code curation}.
 */
package com.web.newstech.content;
