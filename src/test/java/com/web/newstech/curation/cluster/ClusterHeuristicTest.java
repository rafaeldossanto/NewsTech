package com.web.newstech.curation.cluster;

import com.web.newstech.ingest.RawItem;
import com.web.newstech.ingest.model.Triage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Os casos vêm do primeiro lote real coletado pelo pipeline, com as entidades da
 * triagem manual. Testar a heurística contra títulos inventados não prova nada: o que
 * quebra o agrupamento é justamente como veículos diferentes escrevem o mesmo fato.
 */
class ClusterHeuristicTest {

	private static final double LIMIAR = 0.30;

	@Nested
	@DisplayName("clusters que apareceram de verdade no lote")
	class ClustersReais {

		@Test
		@DisplayName("duas coberturas do episódio dos testes do Reino Unido casam")
		void testesDoReinoUnido() {
			RawItem ars = item("Anthropic's AI used fake identities, malware in rogue attack on GitHub project",
					List.of("Anthropic", "OpenAI", "GitHub"));
			RawItem guardian = item("AI models shock UK testers by using fake identities to try to trick developers",
					List.of("OpenAI", "Anthropic"));

			assertThat(SimilarityScorer.score(ars, guardian))
					.as("títulos compartilham só 'ai', 'fake' e 'identities' — quem liga é a entidade")
					.isGreaterThanOrEqualTo(LIMIAR);
		}

		@Test
		@DisplayName("LIMITE CONHECIDO: o comunicado oficial sobre o mesmo episódio fica de fora")
		void comunicadoOficialNaoEhCapturado() {
			RawItem ars = item("Anthropic's AI used fake identities, malware in rogue attack on GitHub project",
					List.of("Anthropic", "OpenAI", "GitHub"));
			RawItem comunicado = item("Third-party cyber evaluations involving OpenAI models", List.of("OpenAI"));

			// Na curadoria manual os três itens eram o mesmo fato — mas isso se soube LENDO
			// o conteúdo. O título do comunicado é genérico e não compartilha palavra alguma
			// com a cobertura; a única ligação é a entidade, e entidade sozinha não basta
			// (senão duas notícias quaisquer da mesma empresa virariam um fato só).
			//
			// Este teste existe para registrar o limite, não para aprová-lo: se um dia a
			// heurística melhorar e passar a capturar, ele falha e alguém revisa de propósito.
			assertThat(SimilarityScorer.score(ars, comunicado))
					.as("ligação semântica que só o estágio 2 consegue fazer, se receber os candidatos")
					.isLessThan(LIMIAR);
		}

		@Test
		@DisplayName("duas coberturas da saída do Nikita Bier casam")
		void saidaDoExecutivo() {
			RawItem techcrunch = item("Nikita Bier steps down as X's head of product", List.of("Nikita Bier", "X"));
			RawItem verge = item("X product chief Nikita Bier is leaving after one year", List.of("X", "Nikita Bier"));

			assertThat(SimilarityScorer.score(techcrunch, verge)).isGreaterThanOrEqualTo(LIMIAR);
		}

		@Test
		@DisplayName("dois anúncios do mesmo lançamento na mesma fonte casam")
		void mesmoLancamentoNaMesmaFonte() {
			RawItem er2 = item("Gemini Robotics ER 2: powering robotics with video understanding, task orchestration,"
					+ " and multi-robot collaboration", List.of("Google DeepMind", "Gemini"));
			RawItem robotics2 = item("Gemini Robotics 2 brings whole body intelligence to robots",
					List.of("Google DeepMind", "Gemini"));

			assertThat(SimilarityScorer.score(er2, robotics2)).isGreaterThanOrEqualTo(LIMIAR);
		}

	}

	@Nested
	@DisplayName("o que não pode ser agrupado")
	class FalsosPositivos {

		@Test
		@DisplayName("releases seguidos da mesma ferramenta são fatos diferentes")
		void releasesConsecutivos() {
			RawItem novo = item("2026-08-05, Version 26.7.0 (Current)", List.of());
			RawItem antigo = item("2026-08-03, Version 26.6.0 (Current)", List.of());

			// Sem a regra de versão estes dois teriam título praticamente idêntico.
			assertThat(SimilarityScorer.score(novo, antigo))
					.as("o número é a única coisa que distingue um release do seguinte")
					.isLessThan(LIMIAR);
		}

		@Test
		@DisplayName("mesma empresa em dois assuntos diferentes não vira um fato só")
		void mesmaEmpresaAssuntosDiferentes() {
			RawItem avaliacoes = item("Third-party cyber evaluations involving OpenAI models", List.of("OpenAI"));
			RawItem disputa = item("Apple is getting this wrong", List.of("OpenAI", "Apple"));

			assertThat(SimilarityScorer.score(avaliacoes, disputa)).isLessThan(LIMIAR);
		}

		@Test
		@DisplayName("dois itens sem entidade nenhuma não se atraem")
		void semEntidades() {
			RawItem a = item("Techie lured out of retirement to support software only he remembered", List.of());
			RawItem b = item("Airport sign fails to boot, officers already on the scene", List.of());

			assertThat(SimilarityScorer.score(a, b)).isLessThan(LIMIAR);
		}

	}

	@Nested
	@DisplayName("tokenização")
	class Tokenizacao {

		@Test
		@DisplayName("descarta palavras vazias dos dois idiomas e mantém siglas curtas")
		void tokens() {
			assertThat(TitleTokenizer.tokens("The new AI models are in the UK"))
					.contains("ai", "uk", "models")
					.doesNotContain("the", "are", "in", "new");
		}

		@Test
		@DisplayName("extrai versão antes de a normalização quebrar os pontos")
		void versoes() {
			assertThat(TitleTokenizer.versions("Version 26.7.0 (Current)")).containsExactly("26.7.0");
			assertThat(TitleTokenizer.versions("Rust 1.94.0")).containsExactly("1.94.0");
			assertThat(TitleTokenizer.versions("jdk-27+33")).contains("27+33");
			assertThat(TitleTokenizer.versions("Sem numero nenhum")).isEmpty();
		}

	}

	private RawItem item(String title, List<String> entities) {
		return RawItem.builder()
				.title(title)
				.publishedAt(Instant.now())
				.triage(new Triage(List.of(), entities, "en", 80, "", "manual", 0, 0, 0, Instant.now()))
				.build();
	}

}
