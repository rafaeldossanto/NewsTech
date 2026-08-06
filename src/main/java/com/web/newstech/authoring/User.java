package com.web.newstech.authoring;

import com.web.newstech.authoring.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {

	@Id
	private String id;

	@Email
	@NotBlank
	private String email;

	@NotBlank
	private String username;

	@NotBlank
	private String passwordHash;

	private String displayName;

	@Builder.Default
	private Set<Role> roles = Set.of(Role.AUTHOR);

	private Instant createdAt;

	@Builder.Default
	private boolean active = true;

	// Base da quarentena de conta nova: conta com poucos artigos publicados nao alcanca a home.
	@Builder.Default
	private int publishedCount = 0;

	public boolean hasRole(Role role) {
		return roles.contains(role);
	}

	public String nameForDisplay() {
		return isNullOrBlank(displayName) ? username : displayName;
	}

	private static boolean isNullOrBlank(String value) {
		return value == null || value.isBlank();
	}

}
