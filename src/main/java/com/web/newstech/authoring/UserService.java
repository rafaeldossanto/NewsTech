package com.web.newstech.authoring;

import com.web.newstech.authoring.enums.Role;
import com.web.newstech.authoring.exceptions.RegistrationException;
import com.web.newstech.authoring.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UserService {

	private static final Pattern USERNAME = Pattern.compile("^[a-z0-9][a-z0-9-]{2,19}$");

	private static final int MIN_PASSWORD = 8;

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public User register(String email, String username, String password) {
		String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
		String normalizedUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);

		validate(normalizedEmail, normalizedUsername, password);

		if (userRepository.existsByUsername(normalizedUsername)) {
			throw new RegistrationException("Esse nome de usuário já está em uso.");
		}
		if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
			throw new RegistrationException("Já existe uma conta com esse e-mail.");
		}

		try {
			return userRepository.save(User.builder()
					.email(normalizedEmail)
					.username(normalizedUsername)
					.passwordHash(passwordEncoder.encode(password))
					.roles(Set.of(Role.AUTHOR))
					.createdAt(Instant.now())
					.active(true)
					.build());
		}
		catch (DuplicateKeyException ex) {
			// Duas inscricoes simultaneas com o mesmo username passam pelas checagens acima;
			// o indice unico e quem realmente decide.
			throw new RegistrationException("Esse nome de usuário já está em uso.");
		}
	}

	private void validate(String email, String username, String password) {
		if (!email.contains("@") || email.length() < 5) {
			throw new RegistrationException("Informe um e-mail válido.");
		}
		if (!USERNAME.matcher(username).matches()) {
			throw new RegistrationException(
					"O nome de usuário deve ter de 3 a 20 caracteres, usando letras minúsculas, números e hífen.");
		}
		if (password == null || password.length() < MIN_PASSWORD) {
			throw new RegistrationException("A senha precisa de pelo menos %d caracteres.".formatted(MIN_PASSWORD));
		}
	}

	public void registerPublication(User author) {
		author.setPublishedCount(author.getPublishedCount() + 1);
		userRepository.save(author);
	}

}
