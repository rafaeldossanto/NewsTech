package com.web.newstech.content;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "topics")
public class Topic {

	@Id
	private String id;

	@NotBlank
	private String slug;

	@NotBlank
	private String name;

	private String description;

	@Builder.Default
	private int displayOrder = 0;

	@Builder.Default
	private boolean active = true;

}
