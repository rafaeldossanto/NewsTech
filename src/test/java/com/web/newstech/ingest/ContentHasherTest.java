package com.web.newstech.ingest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentHasherTest {

	@Test
	@DisplayName("mesmo artigo com acentuacao e pontuacao diferentes gera o mesmo hash")
	void deduplicaVariacaoDeTitulo() {
		String comAcento = ContentHasher.hash("OpenAI lança o GPT-6: agora com raciocínio",
				"https://exemplo.com/gpt6");
		String semAcento = ContentHasher.hash("OpenAI lanca o GPT-6 — agora com raciocinio",
				"https://exemplo.com/gpt6");

		assertThat(comAcento).isEqualTo(semAcento);
	}

	@Test
	@DisplayName("parametros de tracking nao mudam o hash")
	void ignoraParametrosDeTracking() {
		String limpa = ContentHasher.hash("Anthropic anuncia novo modelo", "https://exemplo.com/post");
		String comUtm = ContentHasher.hash("Anthropic anuncia novo modelo",
				"https://exemplo.com/post?utm_source=twitter&utm_campaign=launch");

		assertThat(limpa).isEqualTo(comUtm);
	}

	@Test
	@DisplayName("parametro que identifica o recurso continua contando")
	void preservaParametroSignificativo() {
		String semId = ContentHasher.hash("Release notes", "https://exemplo.com/notes");
		String comId = ContentHasher.hash("Release notes", "https://exemplo.com/notes?v=21");

		assertThat(semId).isNotEqualTo(comId);
	}

	@Test
	@DisplayName("barra final e fragmento nao mudam o hash")
	void normalizaBarraFinalEFragmento() {
		String base = ContentHasher.hash("Kimi K2 chega ao mercado", "https://exemplo.com/kimi");
		String comBarra = ContentHasher.hash("Kimi K2 chega ao mercado", "https://exemplo.com/kimi/");
		String comFragmento = ContentHasher.hash("Kimi K2 chega ao mercado", "https://exemplo.com/kimi#intro");

		assertThat(base).isEqualTo(comBarra).isEqualTo(comFragmento);
	}

	@Test
	@DisplayName("artigos diferentes na mesma fonte geram hashes diferentes")
	void naoColideEntreArtigos() {
		String primeiro = ContentHasher.hash("Java 25 sai em setembro", "https://exemplo.com/java25");
		String segundo = ContentHasher.hash("Python 3.15 sai em outubro", "https://exemplo.com/py315");

		assertThat(primeiro).isNotEqualTo(segundo);
	}

	@Test
	@DisplayName("url malformada nao explode - volta como veio")
	void toleraUrlMalformada() {
		String hash = ContentHasher.hash("Titulo qualquer", "isto :: nao e uma url");

		assertThat(hash).isNotBlank().hasSize(64);
	}

	@Test
	@DisplayName("titulo nulo nao explode")
	void toleraTituloNulo() {
		assertThat(ContentHasher.hash(null, "https://exemplo.com/x")).isNotBlank();
	}

	@Test
	@DisplayName("ordem dos parametros da query nao muda o hash")
	void normalizaOrdemDaQuery() {
		String ordemA = ContentHasher.hash("Post", "https://exemplo.com/p?b=2&a=1");
		String ordemB = ContentHasher.hash("Post", "https://exemplo.com/p?a=1&b=2");

		assertThat(ordemA).isEqualTo(ordemB);
	}

}
