package com.web.newstech.authoring;

import com.mongodb.MongoWriteException;
import com.web.newstech.TestcontainersConfiguration;
import com.web.newstech.authoring.enums.Role;
import com.web.newstech.authoring.exceptions.RegistrationException;
import com.web.newstech.authoring.repository.UserRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class UserRegistrationIT {

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private MongoTemplate mongoTemplate;

	@BeforeEach
	void limpar() {
		userRepository.findByUsername("jornalista").ifPresent(userRepository::delete);
		userRepository.findByEmailIgnoreCase("jornalista@exemplo.test").ifPresent(userRepository::delete);
	}

	@Test
	@DisplayName("cadastro cria conta de autor com senha codificada")
	void cadastra() {
		User user = userService.register("Jornalista@Exemplo.test", "Jornalista", "senha-bem-longa");

		assertThat(user.getUsername()).as("username normalizado para minúsculas").isEqualTo("jornalista");
		assertThat(user.getEmail()).isEqualTo("jornalista@exemplo.test");
		assertThat(user.getRoles()).containsExactly(Role.AUTHOR);
		assertThat(user.getPublishedCount()).isZero();

		assertThat(user.getPasswordHash())
				.as("senha nunca é persistida em texto puro")
				.isNotEqualTo("senha-bem-longa");
		assertThat(passwordEncoder.matches("senha-bem-longa", user.getPasswordHash())).isTrue();
	}

	@Test
	@DisplayName("username e e-mail repetidos são recusados")
	void recusaDuplicado() {
		userService.register("jornalista@exemplo.test", "jornalista", "senha-bem-longa");

		assertThatThrownBy(() -> userService.register("outro@exemplo.test", "jornalista", "senha-bem-longa"))
				.isInstanceOf(RegistrationException.class)
				.hasMessageContaining("nome de usuário");

		assertThatThrownBy(() -> userService.register("jornalista@exemplo.test", "outro-nome", "senha-bem-longa"))
				.isInstanceOf(RegistrationException.class)
				.hasMessageContaining("e-mail");
	}

	@Test
	@DisplayName("a unicidade é garantida pelo índice, não só pela checagem em código")
	void unicidadeNoBanco() {
		userService.register("jornalista@exemplo.test", "jornalista", "senha-bem-longa");

		// Duas inscrições simultâneas passam pela checagem de existência antes de qualquer
		// uma gravar; quem decide de fato é o índice único.
		Document duplicado = new Document()
				.append("username", "jornalista")
				.append("email", "outro@exemplo.test")
				.append("passwordHash", "irrelevante");

		assertThatThrownBy(() -> mongoTemplate.getDb().getCollection("users").insertOne(duplicado))
				.isInstanceOf(MongoWriteException.class)
				.hasMessageContaining("duplicate key");
	}

	@Test
	@DisplayName("entrada inválida é recusada com mensagem que diz o que corrigir")
	void validacao() {
		assertThatThrownBy(() -> userService.register("sem-arroba", "jornalista", "senha-bem-longa"))
				.hasMessageContaining("e-mail válido");

		assertThatThrownBy(() -> userService.register("a@b.test", "NOME_INVALIDO!", "senha-bem-longa"))
				.hasMessageContaining("nome de usuário deve ter");

		assertThatThrownBy(() -> userService.register("a@b.test", "jornalista", "curta"))
				.hasMessageContaining("pelo menos 8");
	}

	@Test
	@DisplayName("o admin continua existindo como conta no banco")
	void adminBootstrap() {
		assertThat(userRepository.findByUsername("admin"))
				.get()
				.satisfies(admin -> {
					assertThat(admin.hasRole(Role.ADMIN)).isTrue();
					assertThat(admin.isActive()).isTrue();
				});
	}

}
