package com.web.newstech.authoring;

import com.web.newstech.authoring.enums.ArticleStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * Artigo assinado por uma pessoa.
 *
 * <p>Colecao separada de {@code stories} por razao estrutural: o validador de stories
 * exige fonte, e um artigo original nao tem fonte externa - ele e a fonte. Encaixa-lo
 * ali obrigaria a afrouxar o validador e derrubaria a garantia de atribuicao que
 * protege toda a parte agregada do portal.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "articles")
public class Article {

	@Id
	private String id;

	@NotBlank
	private String slug;

	@NotBlank
	private String title;

	private String subtitle;

	@NotBlank
	private String bodyMarkdown;

	@NotBlank
	private String authorId;

	// Desnormalizado para listar e assinar sem consultar users a cada item.
	@NotBlank
	private String authorUsername;

	private String authorDisplayName;

	@Builder.Default
	private List<String> topics = List.of();

	@NotNull
	@Builder.Default
	private ArticleStatus status = ArticleStatus.PUBLISHED;

	@NotNull
	private Instant publishedAt;

	private Instant updatedAt;

	// Quarentena de conta nova: falso mantem o artigo em /artigos, fora da home e dos topicos.
	@Builder.Default
	private boolean homeEligible = false;

	public boolean isPublished() {
		return status == ArticleStatus.PUBLISHED;
	}

}
