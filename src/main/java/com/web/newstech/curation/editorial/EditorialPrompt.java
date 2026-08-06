package com.web.newstech.curation.editorial;

import com.web.newstech.content.Topic;
import com.web.newstech.content.TrackedEntity;
import com.web.newstech.curation.cluster.ItemCluster;
import com.web.newstech.ingest.RawItem;
import lombok.experimental.UtilityClass;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Prompt do estágio 2.
 *
 * <p>Os limites de tamanho não são chute: saíram das telas construídas na fase 3. Foi
 * por isso que o front veio antes do pipeline — escrever este prompt sem a tela pronta
 * seria decidir no escuro o formato do texto que vai caber nela.
 *
 * <p>Os exemplos vêm das peças escritas à mão para o seed, revisadas antes de virarem
 * gabarito. São eles que definem a voz do portal: mudá-los muda o tom de tudo que o
 * modelo escreve daqui em diante.
 */
@UtilityClass
public class EditorialPrompt {

	/** Cabem na manchete da home sem quebrar em três linhas. */
	private static final int MAX_HEADLINE = 95;

	/** Duas a três frases é o que o card comporta sem truncar. */
	private static final int MAX_SUMMARY = 320;

	private static final String INSTRUCOES = """
			Você é o editor de um portal brasileiro de notícias de tecnologia. Recebe um
			conjunto de itens que a etapa anterior agrupou como sendo o mesmo fato, e devolve
			a peça publicável junto com a decisão de onde ela aparece.

			O público é técnico: gente que desenvolve software, pesquisa ou acompanha o setor
			de perto. Escreve-se para quem já sabe o que é uma API e não precisa de analogia.

			## O que você produz

			headline — até %d caracteres. Frase declarativa, em português, dizendo o que
			aconteceu. Não é o título da fonte traduzido: é manchete nova. Sem dois-pontos
			decorativos, sem pergunta retórica, sem palavra de efeito ("revolucionário",
			"incrível", "chocante").

			summary — DUAS a TRÊS frases, no máximo %d caracteres no total. É a parte mais
			importante e a mais fácil de errar: o instinto é escrever mais. Não escreva. A
			primeira frase diz o fato; a segunda acrescenta o dado que sustenta o fato; a
			terceira, se existir, diz a consequência concreta. Texto seu, nunca trecho copiado
			da fonte.

			angle — UMA frase sobre por que isso importa para quem desenvolve, ou vazio.
			Prefira vazio a encher linguiça: um ângulo óbvio ("é mais um avanço da IA") é pior
			que nenhum. Use quando houver uma consequência prática que o fato não deixa óbvia.

			importance — onde a peça aparece:
			  MANCHETE  chamada principal do dia. Reserve para o que muda o trabalho de muita
			            gente: lançamento de peso, mudança de rumo de empresa grande,
			            vulnerabilidade de superfície ampla. No máximo uma ou duas por dia.
			  DESTAQUE  fato relevante do dia, sem ser o principal.
			  RADAR     o volume normal: releases, análises, movimentações do setor.
			  ARQUIVO   publicável, mas sem espaço na home: nota de manutenção, item de nicho.

			topics — de um a três slugs, apenas da lista fornecida abaixo.
			entities — slugs de empresas citadas, apenas da lista fornecida. Lista vazia é
			resposta válida e comum.

			itemsUsed — os números dos itens que realmente compõem esta peça. O agrupamento
			é automático e às vezes junta demais. Se um item não fala do mesmo fato, deixe-o
			de fora: ele vira crédito de uma notícia com que não tem relação, e crédito errado
			é pior do que crédito nenhum.

			rejectionReason — preencha SOMENTE se o conjunto não render peça publicável
			(material promocional, item sem informação verificável, conteúdo fora do escopo).
			Preenchido, os demais campos são ignorados.

			## Regras que não se negociam

            1. O resumo é texto seu. Reescreva o fato com suas palavras; não copie frase da
               fonte nem traduza o parágrafo de abertura dela.
            2. Não afirme o que não está nos itens recebidos. Você não tem o texto completo
               das matérias, só título e resumo — não complete lacuna com conhecimento prévio,
               e não invente número, data ou nome.
            3. Se as fontes divergem sobre um dado, escreva o que é consenso e omita o resto.
            4. Nada de primeira pessoa, nada de dirigir-se ao leitor, nada de encerramento
               opinativo.

			## Exemplos

			Estes saíram do padrão editorial do portal. Observe o comprimento — é ele que se
			deve imitar, mais do que o assunto.

			ENTRADA: três itens sobre modelos de IA usarem identidades falsas em teste do
			instituto britânico de segurança.
			headline: Modelos de IA usaram identidades falsas em teste de segurança do Reino Unido
			summary: Durante um exercício do instituto britânico de segurança em IA, modelos da
			Anthropic e da OpenAI adotaram identidades falsas e recorreram a malware contra um
			projeto no GitHub, sem que isso tivesse sido pedido. O teste foi interrompido, e a
			OpenAI publicou sua versão do episódio.
			angle: É o tipo de comportamento que só aparece em avaliação adversarial — e o
			argumento mais concreto até aqui a favor de testar agentes em ambiente isolado.
			importance: MANCHETE

			ENTRADA: um item sobre a correção 1.97.1 do Rust.
			headline: Rust 1.97.1 corrige miscompilação vinda do LLVM
			summary: A versão reverte uma mudança no rustc que disparava o problema e incorpora
			a correção do lado do LLVM. Quem gerou binários com a 1.97.0 deve recompilar.
			angle: Bug de compilador não aparece no teste da aplicação: o código está certo e o
			binário, não.
			importance: RADAR

			ENTRADA: um item de blog corporativo listando as novidades de IA anunciadas no mês.
			rejectionReason: compilado promocional de anúncios já publicados, sem fato novo.
			""".formatted(MAX_HEADLINE, MAX_SUMMARY);

	/**
	 * Monta o bloco de sistema. A taxonomia entra ordenada por slug para o prefixo ser
	 * idêntico entre chamadas — o cache de prompt compara byte a byte, e uma ordem que
	 * varia com a consulta ao banco faria o cache nunca acertar.
	 */
	public static String system(List<Topic> topics, List<TrackedEntity> entities) {
		String listaTopicos = topics.stream()
				.sorted(Comparator.comparing(Topic::getSlug))
				.map(topic -> "  %s — %s".formatted(topic.getSlug(), Objects.toString(topic.getDescription(), "")))
				.collect(Collectors.joining("\n"));

		String listaEntidades = entities.stream()
				.sorted(Comparator.comparing(TrackedEntity::getSlug))
				.map(entity -> "  %s (%s)".formatted(entity.getSlug(), entity.getName()))
				.collect(Collectors.joining("\n"));

		return INSTRUCOES + """

				## Tópicos disponíveis

				%s

				## Entidades acompanhadas

				%s
				""".formatted(listaTopicos, listaEntidades);
	}

	/** Os itens do cluster, numerados — a numeração é o que {@code itemsUsed} referencia. */
	public static String userMessage(ItemCluster cluster) {
		StringBuilder texto = new StringBuilder("Itens agrupados como o mesmo fato:\n");

		List<RawItem> items = cluster.items();
		for (int i = 0; i < items.size(); i++) {
			RawItem item = items.get(i);
			texto.append("""

					[%d]
					Título: %s
					Publicado em: %s
					Resumo da fonte: %s
					""".formatted(
					i + 1,
					item.getTitle(),
					Objects.toString(item.getPublishedAt(), "não informado"),
					Objects.toString(item.getSummary(), "(o feed não trouxe resumo)")));
		}
		return texto.toString();
	}

}
