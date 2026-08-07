package com.web.newstech.web;

import com.web.newstech.authoring.Article;
import com.web.newstech.authoring.ArticleService;
import com.web.newstech.authoring.MarkdownRenderer;
import com.web.newstech.authoring.PublishingException;
import com.web.newstech.authoring.User;
import com.web.newstech.authoring.enums.ArticleStatus;
import com.web.newstech.authoring.repository.ArticleRepository;
import com.web.newstech.authoring.repository.UserRepository;
import com.web.newstech.content.Topic;
import com.web.newstech.content.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class ArticleController {

	private static final int LIMITE_LISTA = 30;

	private final ArticleRepository articleRepository;
	private final ArticleService articleService;
	private final MarkdownRenderer markdownRenderer;
	private final UserRepository userRepository;
	private final TopicRepository topicRepository;

	@ModelAttribute("navTopics")
	public List<Topic> navTopics() {
		return topicRepository.findByActiveTrueOrderByDisplayOrderAsc();
	}

	@GetMapping("/artigos")
	public String list(Model model) {
		// Lista tudo que foi publicado, inclusive o que esta em quarentena: e o lugar
		// onde uma conta nova consegue ser lida antes de alcancar a home.
		model.addAttribute("articles", ArticleView.summaries(
				articleRepository.findByStatusOrderByPublishedAtDesc(
						ArticleStatus.PUBLISHED, PageRequest.of(0, LIMITE_LISTA))));
		model.addAttribute("pageTitle", "Artigos");
		return "article/lista";
	}

	@GetMapping("/artigo/{slug}")
	public String read(@PathVariable String slug, Model model) {
		Article article = publicado(slug);

		ArticleView view = ArticleView.from(article, markdownRenderer.render(article.getBodyMarkdown()));
		model.addAttribute("article", view);
		model.addAttribute("pageTitle", view.title());
		model.addAttribute("pageDescription", view.subtitle());
		return "article/leitura";
	}

	@GetMapping("/autor/{username}")
	public String authorHub(@PathVariable String username, Model model) {
		User author = userRepository.findByUsername(username)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Autor não encontrado"));

		model.addAttribute("author", author);
		model.addAttribute("articles", ArticleView.summaries(
				articleRepository.findByAuthorUsernameAndStatusOrderByPublishedAtDesc(
						author.getUsername(), ArticleStatus.PUBLISHED, PageRequest.of(0, LIMITE_LISTA))));
		model.addAttribute("pageTitle", author.nameForDisplay());
		return "article/autor";
	}

	@GetMapping("/escrever")
	public String writeForm(Model model) {
		model.addAttribute("pageTitle", "Escrever");
		model.addAttribute("topics", navTopics());
		return "article/escrever";
	}

	@PostMapping("/escrever")
	public String publish(@AuthenticationPrincipal UserDetails principal, @RequestParam String title,
			@RequestParam(required = false) String subtitle, @RequestParam String body,
			@RequestParam(required = false) List<String> topics, Model model) {

		User author = autenticado(principal);
		try {
			Article article = articleService.publish(author, title, subtitle, body, topics);
			return "redirect:/artigo/" + article.getSlug();
		}
		catch (PublishingException ex) {
			return devolverFormulario(model, ex.getMessage(), title, subtitle, body, null);
		}
	}

	@GetMapping("/escrever/{slug}")
	public String editForm(@AuthenticationPrincipal UserDetails principal, @PathVariable String slug, Model model) {
		Article article = doAutor(principal, slug);

		model.addAttribute("pageTitle", "Editar");
		model.addAttribute("topics", navTopics());
		model.addAttribute("editing", article.getSlug());
		model.addAttribute("title", article.getTitle());
		model.addAttribute("subtitle", article.getSubtitle());
		model.addAttribute("body", article.getBodyMarkdown());
		model.addAttribute("selectedTopics", article.getTopics());
		return "article/escrever";
	}

	@PostMapping("/escrever/{slug}")
	public String update(@AuthenticationPrincipal UserDetails principal, @PathVariable String slug,
			@RequestParam String title, @RequestParam(required = false) String subtitle,
			@RequestParam String body, @RequestParam(required = false) List<String> topics, Model model) {

		Article article = doAutor(principal, slug);
		try {
			articleService.update(article, title, subtitle, body, topics);
			return "redirect:/artigo/" + article.getSlug();
		}
		catch (PublishingException ex) {
			return devolverFormulario(model, ex.getMessage(), title, subtitle, body, slug);
		}
	}

	private String devolverFormulario(Model model, String erro, String title, String subtitle, String body,
			String editing) {
		model.addAttribute("error", erro);
		model.addAttribute("title", title);
		model.addAttribute("subtitle", subtitle);
		model.addAttribute("body", body);
		model.addAttribute("editing", editing);
		model.addAttribute("pageTitle", editing == null ? "Escrever" : "Editar");
		model.addAttribute("topics", navTopics());
		return "article/escrever";
	}

	private Article publicado(String slug) {
		Article article = articleRepository.findBySlug(slug)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artigo não encontrado"));
		if (!article.isPublished()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Artigo não está publicado");
		}
		return article;
	}

	private Article doAutor(UserDetails principal, String slug) {
		User author = autenticado(principal);
		Article article = articleRepository.findBySlug(slug)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artigo não encontrado"));

		// 403 e nao 404: quem esta autenticado ja sabe que o artigo existe, entao esconder
		// so confundiria. O que importa e deixar claro que editar o alheio nao rola.
		if (!article.getAuthorId().equals(author.getId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Este artigo é de outro autor");
		}
		return article;
	}

	private User autenticado(UserDetails principal) {
		if (principal == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "É preciso entrar para escrever");
		}
		return userRepository.findByUsername(principal.getUsername())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Conta não encontrada"));
	}

}
