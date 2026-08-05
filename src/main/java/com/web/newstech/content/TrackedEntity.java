package com.web.newstech.content;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * Empresa ou pessoa acompanhada pelo portal. Alimenta os hubs ({@code /empresa/anthropic}),
 * que sao o diferencial do nicho: quem quer seguir uma empresa tem pagina propria.
 *
 * <p>Chamada de {@code TrackedEntity} e nao {@code Entity} para nao colidir com o
 * vocabulario de persistencia.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "entities")
public class TrackedEntity {

	@Id
	private String id;

	/** Usado na url e como valor em {@code stories.entities}. */
	@NotBlank
	private String slug;

	@NotBlank
	private String name;

	private EntityType type;

	/**
	 * Formas alternativas de citar a mesma entidade: "Anthropic", "@AnthropicAI",
	 * "Moonshot AI" e "Kimi". Indexado - e por aqui que a triagem resolve o texto
	 * livre do modelo para um slug canonico.
	 */
	@Builder.Default
	private List<String> aliases = List.of();

	private String description;

	public enum EntityType {
		COMPANY,
		PERSON,
		PRODUCT
	}

}
