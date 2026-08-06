package com.web.newstech.authoring;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

@Component
public class MarkdownRenderer {

	private final Parser parser = Parser.builder().build();

	// escapeHtml: HTML escrito dentro do Markdown sai como texto, nao interpretado.
	private final HtmlRenderer renderer = HtmlRenderer.builder().escapeHtml(true).build();

	/**
	 * Segunda camada. Duas porque este e o unico ponto do sistema onde texto de gente
	 * nao confiavel vira HTML na pagina - se a primeira falhar por bug ou mudanca de
	 * versao, a segunda ainda barra script, iframe e atributo de evento.
	 */
	private final Safelist safelist = Safelist.basic()
			.addTags("h2", "h3", "h4", "hr")
			.addAttributes("a", "href", "title")
			.addProtocols("a", "href", "http", "https", "mailto");

	// Renderiza na leitura em vez de guardar HTML: se esta regra mudar, todo o conteudo
	// ja publicado passa a ser protegido pela regra nova.
	public String render(String markdown) {
		if (markdown == null || markdown.isBlank()) {
			return "";
		}
		return Jsoup.clean(renderer.render(parser.parse(markdown)), safelist);
	}

}
