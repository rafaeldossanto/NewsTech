package com.web.newstech.curation;

import com.web.newstech.ingest.RawItem;
import com.web.newstech.ingest.enums.RawItemStatus;
import com.web.newstech.ingest.model.Triage;
import com.web.newstech.ingest.repository.RawItemRepository;
import com.web.newstech.shared.config.NewsTechProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Estagio 1 do pipeline: classifica, extrai entidades e pontua relevancia.
 *
 * <p>Roda com Haiku porque e o estagio de volume - centenas de itens por dia, dos quais
 * a maioria e descartada. A decisao editorial e a escrita, que sao de baixo volume e
 * alto impacto, ficam com o Opus no estagio 2.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TriageService {

	private final TriageModel triageModel;
	private final RawItemRepository rawItemRepository;
	private final NewsTechProperties properties;

	/**
	 * Processa um lote da fila. Falha de um item nao derruba o lote: o item vai para
	 * NEEDS_REVIEW e o processamento continua.
	 *
	 * @return quantidade de itens processados
	 */
	public int triageBatch() {
		List<RawItem> queue = rawItemRepository.findByStatusOrderByPublishedAtDesc(
				RawItemStatus.COLLECTED, PageRequest.of(0, properties.claude().triageBatchSize()));

		if (queue.isEmpty()) {
			return 0;
		}

		int processed = 0;
		for (RawItem item : queue) {
			try {
				applyTriage(item);
				processed++;
			}
			catch (TriageRefusedException ex) {
				// Recusa nao e bug: e resposta legitima. O item precisa de olhar humano,
				// nao de nova tentativa.
				log.warn("Triagem recusada para {}: {}", item.getUrl(), ex.getMessage());
				marcar(item, RawItemStatus.NEEDS_REVIEW);
			}
			catch (RuntimeException ex) {
				log.error("Falha ao triar item {} ({}): {}", item.getId(), item.getUrl(), ex.getMessage());
				marcar(item, RawItemStatus.NEEDS_REVIEW);
			}
		}

		log.info("Triagem: {} itens processados de uma fila de {}", processed, queue.size());
		return processed;
	}

	private void applyTriage(RawItem item) {
		TriageOutcome outcome = triageModel.classify(item);
		TriageResult result = outcome.result();

		item.setTriage(new Triage(
				result.topics(),
				result.entities(),
				result.language(),
				result.relevanceScore(),
				result.reasoning(),
				outcome.model(),
				outcome.inputTokens(),
				outcome.outputTokens(),
				outcome.cachedInputTokens(),
				Instant.now()));

		boolean relevant = result.relevanceScore() >= properties.claude().relevanceThreshold();
		item.setStatus(relevant ? RawItemStatus.TRIAGED : RawItemStatus.DISCARDED);
		rawItemRepository.save(item);

		log.debug("Item {} triado: score {} -> {}", item.getId(), result.relevanceScore(), item.getStatus());
	}

	private void marcar(RawItem item, RawItemStatus status) {
		item.setStatus(status);
		rawItemRepository.save(item);
	}

}
