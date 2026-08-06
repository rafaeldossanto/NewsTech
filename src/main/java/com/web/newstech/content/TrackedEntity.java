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

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "entities")
public class TrackedEntity {

	@Id
	private String id;

	@NotBlank
	private String slug;

	@NotBlank
	private String name;

	private EntityType type;

	@Builder.Default
	private List<String> aliases = List.of();

	private String description;

}
