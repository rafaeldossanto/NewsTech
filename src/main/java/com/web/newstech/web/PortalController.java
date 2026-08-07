package com.web.newstech.web;

import com.web.newstech.authoring.Article;
import com.web.newstech.authoring.enums.ArticleStatus;
import com.web.newstech.authoring.repository.ArticleRepository;
import com.web.newstech.content.Importance;
import com.web.newstech.content.Story;
import com.web.newstech.content.repository.StoryRepository;
import com.web.newstech.content.Topic;
import com.web.newstech.content.repository.TopicRepository;
import com.web.newstech.content.TrackedEntity;
import com.web.newstech.content.repository.TrackedEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class PortalController {

	private static final int LIMITE_DESTAQUES = 4;
	private static final int LIMITE_RADAR = 25;
	private static final int PAGINA = 12;
	private static final int LIMITE_LISTA = 40;
	private static final int HORAS_DO_RADAR = 48;

	private final StoryRepository storyRepository;
	private final TopicRepository topicRepository;
	private final TrackedEntityRepository entityRepository;
	private final ArticleRepository articleRepository;

	@ModelAttribute("navTopics")
	public List<Topic> navTopics() {
		return topicRepository.findByActiveTrueOrderByDisplayOrderAsc();
	}

	@GetMapping("/")
	public String home(Model model) {
		List<Story> manchetes = storyRepository.findByImportanceOrderByPublishedAtDesc(
				Importance.MANCHETE, PageRequest.of(0, 1));

		List<Story> destaques = storyRepository.findByImportanceOrderByPublishedAtDesc(
				Importance.DESTAQUE, PageRequest.of(0, LIMITE_DESTAQUES));

		Instant desde = Instant.now().minus(HORAS_DO_RADAR, ChronoUnit.HOURS);

		List<Story> radarStories = storyRepository.findByPublishedAtAfterOrderByPublishedAtDesc(
						desde, PageRequest.of(0, LIMITE_RADAR)).stream()
				.filter(story -> manchetes.stream().noneMatch(m -> m.getSlug().equals(story.getSlug())))
				.toList();

		// Artigos entram no radar, nunca em manchete ou destaque - essas posicoes seguem
		// sendo da curadoria. E so entra quem ja saiu da quarentena de conta nova.
		List<Article> radarArticles = articleRepository
				.findByStatusAndHomeEligibleTrueAndPublishedAtAfterOrderByPublishedAtDesc(
						ArticleStatus.PUBLISHED, desde, PageRequest.of(0, LIMITE_RADAR));

		model.addAttribute("lead", manchetes.isEmpty() ? null : StoryView.from(manchetes.getFirst()));
		model.addAttribute("highlights", StoryView.from(destaques));
		model.addAttribute("radar", FeedItem.merge(radarStories, radarArticles, LIMITE_RADAR));
		return "home";
	}

	@GetMapping("/n/{slug}")
	public String story(@PathVariable String slug, Model model) {
		Story story = storyRepository.findBySlug(slug)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Peça não encontrada"));

		StoryView view = StoryView.from(story);
		model.addAttribute("story", view);
		model.addAttribute("pageTitle", view.headline());
		model.addAttribute("pageDescription", view.summary());
		model.addAttribute("related", StoryView.from(relacionadas(story)));
		model.addAttribute("topicChips", topicChips(view.topics()));
		model.addAttribute("entityChips", entityChips(view.entities()));
		return "story";
	}

	private List<Chip> topicChips(List<String> slugs) {
		return slugs.stream()
				.flatMap(slug -> topicRepository.findBySlug(slug).stream())
				.map(topic -> new Chip(topic.getSlug(), topic.getName(), "/" + topic.getSlug()))
				.toList();
	}

	private List<Chip> entityChips(List<String> slugs) {
		return slugs.stream()
				.flatMap(slug -> entityRepository.findBySlug(slug).stream())
				.map(entity -> new Chip(entity.getSlug(), entity.getName(), "/empresa/" + entity.getSlug()))
				.toList();
	}

	public record Chip(String slug, String label, String href) {
	}

	@GetMapping("/empresa/{slug}")
	public String entityHub(@PathVariable String slug, Model model) {
		TrackedEntity entity = entityRepository.findBySlug(slug)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não acompanhada"));

		model.addAttribute("entity", entity);
		// Hub de empresa e so de curadoria: entidades sao extraidas pela triagem, nao
		// declaradas por quem escreve artigo.
		model.addAttribute("entries", FeedItem.merge(
				storyRepository.findByEntitiesContainingOrderByPublishedAtDesc(slug, PageRequest.of(0, LIMITE_LISTA)),
				List.of(), LIMITE_LISTA));
		model.addAttribute("pageTitle", entity.getName());
		return "entity";
	}

	@GetMapping("/{slug}")
	public String topic(@PathVariable String slug, Model model) {
		Topic topic = topicRepository.findBySlug(slug)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tópico não encontrado"));

		List<Story> stories =
				storyRepository.findByTopicsContainingOrderByPublishedAtDesc(slug, PageRequest.of(0, PAGINA));

		List<Article> articles = articleRepository
				.findByStatusAndHomeEligibleTrueAndTopicsContainingOrderByPublishedAtDesc(
						ArticleStatus.PUBLISHED, slug, PageRequest.of(0, PAGINA));

		model.addAttribute("topic", topic);
		model.addAttribute("activeTopic", slug);
		model.addAttribute("entries", FeedItem.merge(stories, articles, PAGINA));
		// A paginacao seguinte traz so pecas curadas: elas sao o volume, e misturar duas
		// colecoes com offsets independentes daria item repetido ou pulado.
		model.addAttribute("hasMore", stories.size() == PAGINA);
		model.addAttribute("pageTitle", topic.getName());
		return "topic";
	}

	@GetMapping("/{slug}/pagina/{page}")
	public String topicPage(@PathVariable String slug, @PathVariable int page, Model model) {
		if (topicRepository.findBySlug(slug).isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tópico não encontrado");
		}

		List<Story> stories = storyRepository.findByTopicsContainingOrderByPublishedAtDesc(
				slug, PageRequest.of(Math.max(page, 0), PAGINA));

		model.addAttribute("entries", FeedItem.merge(stories, List.of(), PAGINA));
		return "fragments/story-list :: page";
	}

	private List<Story> relacionadas(Story story) {
		if (story.getTopics().isEmpty()) {
			return List.of();
		}
		return storyRepository.findByTopicsContainingOrderByPublishedAtDesc(
						story.getTopics().getFirst(), PageRequest.of(0, 5)).stream()
				.filter(outra -> !outra.getSlug().equals(story.getSlug()))
				.limit(3)
				.toList();
	}

}
