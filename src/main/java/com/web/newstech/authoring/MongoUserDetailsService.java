package com.web.newstech.authoring;

import com.web.newstech.authoring.enums.Role;
import com.web.newstech.authoring.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MongoUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	@Override
	public UserDetails loadUserByUsername(String username) {
		User user = userRepository.findByUsername(username.toLowerCase(Locale.ROOT))
				.orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

		return org.springframework.security.core.userdetails.User
				.withUsername(user.getUsername())
				.password(user.getPasswordHash())
				.authorities(user.getRoles().stream().map(Role::authority).toArray(String[]::new))
				.disabled(!user.isActive())
				.build();
	}

}
