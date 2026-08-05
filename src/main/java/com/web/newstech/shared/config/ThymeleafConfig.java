package com.web.newstech.shared.config;

import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * O starter do Thymeleaf nao registra o layout dialect sozinho. Sem este bean os
 * atributos {@code layout:decorate} e {@code layout:fragment} sao ignorados em
 * silencio - a pagina renderiza, mas sem o layout, o que parece erro de template.
 */
@Configuration
public class ThymeleafConfig {

	@Bean
	public LayoutDialect layoutDialect() {
		return new LayoutDialect();
	}

}
