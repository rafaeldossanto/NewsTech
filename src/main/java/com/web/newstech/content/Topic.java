package com.web.newstech.content;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Taxonomia editorial. Vira rota do portal ({@code /ia}, {@code /linguagens})
 * e faz parte do prompt de triagem - o modelo classifica dentro deste conjunto fechado.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "topics")
public class Topic {

	@Id
	private String id;

	/** Usado na url e como valor em {@code stories.topics}. */
	@NotBlank
	private String slug;

	@NotBlank
	private String name;

	/** Descricao curta - entra no prompt de triagem para o modelo saber o que cabe aqui. */
	private String description;

	/** Ordem de exibicao na navegacao. */
	@Builder.Default
	private int displayOrder = 0;

	@Builder.Default
	private boolean active = true;

}
