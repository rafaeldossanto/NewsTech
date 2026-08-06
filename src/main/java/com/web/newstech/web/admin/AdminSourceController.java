package com.web.newstech.web.admin;

import com.web.newstech.ingest.enums.ConnectorType;
import com.web.newstech.ingest.IngestService;
import com.web.newstech.ingest.repository.RawItemRepository;
import com.web.newstech.ingest.enums.RawItemStatus;
import com.web.newstech.ingest.Source;
import com.web.newstech.ingest.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.util.List;

/**
 * Operacao das fontes: ver estado, ligar e desligar, cadastrar e forcar coleta.
 *
 * <p>Existe para operar o pipeline enquanto o portal publico ainda nao existe - e
 * continua util depois, porque coletar sob demanda e a forma de testar uma fonte
 * nova sem esperar o proximo ciclo do agendador.
 */
@Slf4j
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminSourceController {

	private final SourceRepository sourceRepository;
	private final RawItemRepository rawItemRepository;
	private final IngestService ingestService;

	@GetMapping
	public String home() {
		return "redirect:/admin/sources";
	}

	@GetMapping("/sources")
	public String list(Model model) {
		List<Source> sources = sourceRepository.findAll().stream()
				// Inativas por ultimo; dentro de cada grupo, as de maior confiabilidade primeiro.
				.sorted(Comparator.comparing(Source::isActive).reversed()
						.thenComparing(Comparator.comparingInt(Source::getTrustWeight).reversed())
						.thenComparing(Source::getName))
				.toList();

		model.addAttribute("sources", sources);
		model.addAttribute("connectorTypes", ConnectorType.values());
		model.addAttribute("stats", new QueueStats(
				rawItemRepository.countByStatus(RawItemStatus.COLLECTED),
				rawItemRepository.countByStatus(RawItemStatus.TRIAGED),
				rawItemRepository.countByStatus(RawItemStatus.DISCARDED),
				rawItemRepository.countByStatus(RawItemStatus.PUBLISHED),
				rawItemRepository.countByStatus(RawItemStatus.NEEDS_REVIEW)));
		return "admin/sources";
	}

	@PostMapping("/sources")
	public String create(@RequestParam String name, @RequestParam String feedUrl,
			@RequestParam ConnectorType connectorType, @RequestParam(defaultValue = "50") int trustWeight,
			RedirectAttributes redirect) {

		if (sourceRepository.findByFeedUrl(feedUrl).isPresent()) {
			redirect.addFlashAttribute("error", "Ja existe uma fonte com esse feed.");
			return "redirect:/admin/sources";
		}

		sourceRepository.save(Source.builder()
				.name(name)
				.feedUrl(feedUrl)
				.connectorType(connectorType)
				.trustWeight(trustWeight)
				.active(true)
				.build());

		redirect.addFlashAttribute("message", "Fonte '%s' cadastrada.".formatted(name));
		return "redirect:/admin/sources";
	}

	@PostMapping("/sources/{id}/toggle")
	public String toggle(@PathVariable String id, RedirectAttributes redirect) {
		Source source = sourceRepository.findById(id).orElseThrow();
		source.setActive(!source.isActive());
		if (source.isActive()) {
			// Reativar tambem limpa o backoff: senao a fonte volta ligada mas so seria
			// consultada horas depois, e o operador nao entenderia por que nada acontece.
			source.setConsecutiveFailures(0);
			source.setNextAttemptAt(null);
		}
		sourceRepository.save(source);

		redirect.addFlashAttribute("message",
				"Fonte '%s' %s.".formatted(source.getName(), source.isActive() ? "ativada" : "desativada"));
		return "redirect:/admin/sources";
	}

	@PostMapping("/sources/{id}/collect")
	public String collectNow(@PathVariable String id, RedirectAttributes redirect) {
		Source source = sourceRepository.findById(id).orElseThrow();
		try {
			IngestService.SourceOutcome outcome = ingestService.collect(source);
			redirect.addFlashAttribute("message", outcome.notModified()
					? "'%s': sem novidades (304).".formatted(source.getName())
					: "'%s': %d novos, %d duplicados.".formatted(
							source.getName(), outcome.collected(), outcome.duplicates()));
		}
		catch (RuntimeException ex) {
			// Aqui o erro precisa aparecer na tela: o operador clicou justamente para descobrir
			// se a fonte funciona. Engolir seria o oposto do proposito do botao.
			log.warn("Coleta manual de '{}' falhou", source.getName(), ex);
			redirect.addFlashAttribute("error",
					"'%s' falhou: %s".formatted(source.getName(), ex.getMessage()));
		}
		return "redirect:/admin/sources";
	}

	public record QueueStats(long collected, long triaged, long discarded, long published, long needsReview) {
	}

}
