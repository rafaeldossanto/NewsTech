package com.web.newstech.web.auth;

import com.web.newstech.authoring.RegistrationException;
import com.web.newstech.authoring.UserService;
import com.web.newstech.content.Topic;
import com.web.newstech.content.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class AuthController {

	private final UserService userService;
	private final TopicRepository topicRepository;

	@org.springframework.web.bind.annotation.ModelAttribute("navTopics")
	public List<Topic> navTopics() {
		return topicRepository.findByActiveTrueOrderByDisplayOrderAsc();
	}

	@GetMapping("/entrar")
	public String loginForm(Model model) {
		model.addAttribute("pageTitle", "Entrar");
		return "auth/entrar";
	}

	@GetMapping("/cadastro")
	public String registerForm(Model model) {
		model.addAttribute("pageTitle", "Criar conta");
		return "auth/cadastro";
	}

	@PostMapping("/cadastro")
	public String register(@RequestParam String email, @RequestParam String username,
			@RequestParam String password, RedirectAttributes redirect, Model model) {

		try {
			userService.register(email, username, password);
		}
		catch (RegistrationException ex) {
			// Devolve o formulario com o que a pessoa ja tinha digitado, menos a senha.
			model.addAttribute("error", ex.getMessage());
			model.addAttribute("email", email);
			model.addAttribute("username", username);
			model.addAttribute("pageTitle", "Criar conta");
			return "auth/cadastro";
		}

		redirect.addFlashAttribute("message", "Conta criada. Entre para começar a escrever.");
		return "redirect:/entrar";
	}

}
