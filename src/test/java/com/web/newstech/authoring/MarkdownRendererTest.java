package com.web.newstech.authoring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownRendererTest {

	private final MarkdownRenderer renderer = new MarkdownRenderer();

	@Nested
	@DisplayName("segurança")
	class Seguranca {

		@Test
		@DisplayName("script no meio do texto vira texto, não executa")
		void script() {
			String html = renderer.render("Texto normal.\n\n<script>alert(1)</script>\n\nMais texto.");

			assertThat(html).doesNotContain("<script").doesNotContain("alert(1)</script>");
			assertThat(html).contains("Texto normal");
		}

		@Test
		@DisplayName("tag com atributo de evento não vira elemento ativo")
		void atributoDeEvento() {
			String html = renderer.render("<img src=x onerror=\"alert(1)\">");

			// O que importa não é a string "onerror" sumir — ela pode aparecer como texto.
			// O que não pode existir é uma tag <img> de verdade carregando o atributo.
			assertThat(html).doesNotContain("<img");
			assertThat(html).contains("&lt;img").as("aparece escapado, como texto visível");
		}

		@Test
		@DisplayName("link javascript: não sobrevive")
		void protocoloJavascript() {
			String html = renderer.render("[clique](javascript:alert(1))");

			assertThat(html).doesNotContain("javascript:");
		}

		@Test
		@DisplayName("iframe é descartado")
		void iframe() {
			String html = renderer.render("<iframe src=\"https://exemplo.test\"></iframe>");

			assertThat(html).doesNotContain("<iframe");
		}

		@Test
		@DisplayName("HTML escrito de propósito aparece como texto legível")
		void htmlVisivelComoTexto() {
			String html = renderer.render("Use a tag <b>negrito</b> assim.");

			// escapeHtml transforma em entidade; o leitor vê o código, não o efeito.
			assertThat(html).contains("&lt;b&gt;");
		}

	}

	@Nested
	@DisplayName("formatação")
	class Formatacao {

		@Test
		@DisplayName("títulos, listas, link e ênfase são renderizados")
		void formatacaoBasica() {
			String html = renderer.render("""
					## Subtítulo

					Texto com **negrito** e *itálico*.

					- primeiro
					- segundo

					[link](https://exemplo.test)
					""");

			assertThat(html)
					.contains("<h2>Subtítulo</h2>")
					.contains("<strong>negrito</strong>")
					.contains("<em>itálico</em>")
					.contains("<li>primeiro</li>")
					.contains("href=\"https://exemplo.test\"");
		}

		@Test
		@DisplayName("texto vazio ou nulo não quebra o template")
		void vazio() {
			assertThat(renderer.render(null)).isEmpty();
			assertThat(renderer.render("   ")).isEmpty();
		}

	}

}
