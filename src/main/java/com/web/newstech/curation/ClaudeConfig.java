package com.web.newstech.curation;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Cliente da Claude API.
 *
 * <p>{@code fromEnv()} le {@code ANTHROPIC_API_KEY} do ambiente - a chave nunca passa
 * por arquivo de configuracao.
 *
 * <p>O bean e {@code @Lazy} de proposito: sem isso, a aplicacao inteira deixaria de
 * subir em qualquer maquina sem a variavel definida, inclusive para rodar so a coleta
 * de feeds ou o teste de integracao, que nao tocam na API. Quem consome resolve o bean
 * via {@code ObjectProvider} no momento do uso, entao a falta da chave estoura na
 * primeira triagem - onde o erro e util - e nao na subida.
 */
@Configuration
public class ClaudeConfig {

	@Bean
	@Lazy
	public AnthropicClient anthropicClient() {
		return AnthropicOkHttpClient.fromEnv();
	}

}
