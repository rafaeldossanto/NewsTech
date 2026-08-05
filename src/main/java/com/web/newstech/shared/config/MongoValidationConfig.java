package com.web.newstech.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.mapping.event.ValidatingEntityCallback;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Liga o Bean Validation nas escritas do MongoDB.
 *
 * <p>Sem este bean, anotacoes como {@code @NotEmpty} em {@code Story.sources} seriam
 * puramente decorativas: o Spring Data nao valida documento na gravacao por padrao.
 *
 * <p>E a camada de aplicacao do invariante de atribuicao. A camada de banco e o
 * validador {@code $jsonSchema} aplicado pelo {@link MongoBootstrap} - as duas existem
 * porque protegem de coisas diferentes: esta da erro cedo e legivel, aquela vale
 * mesmo para escrita que nao passa por aqui.
 */
@Configuration
public class MongoValidationConfig {

	@Bean
	public ValidatingEntityCallback validatingEntityCallback(LocalValidatorFactoryBean factory) {
		return new ValidatingEntityCallback(factory);
	}

}
