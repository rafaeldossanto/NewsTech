package com.web.newstech.curation.triage;

import java.util.List;

/**
 * Saida estruturada do estagio 1.
 *
 * <p>O SDK deriva o JSON Schema deste record e devolve o objeto ja tipado - por isso
 * o prompt nao pede "responda em JSON" e nao ha parsing manual em lugar nenhum.
 *
 * <p>Como o schema vem daqui, mudar um campo deste record muda o contrato com o modelo.
 */
public record TriageResult(

		/** Slugs de topicos da taxonomia. O prompt restringe ao conjunto fechado. */
		List<String> topics,

		/** Empresas, produtos e pessoas citados, em texto livre - resolvidos depois para slugs. */
		List<String> entities,

		/** Codigo ISO de duas letras: "pt", "en". */
		String language,

		/** 0 a 100. Abaixo do corte configurado o item e descartado. */
		int relevanceScore,

		/** Uma frase justificando a nota. Existe para calibrar o corte lendo casos reais. */
		String reasoning) {

}
