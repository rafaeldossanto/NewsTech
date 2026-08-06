package com.web.newstech.curation;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredContentBlock;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredTextBlock;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Usage;
import com.web.newstech.ingest.RawItem;
import com.web.newstech.ingest.repository.RawItemRepository;
import com.web.newstech.ingest.enums.RawItemStatus;
import com.web.newstech.ingest.model.Triage;
import com.web.newstech.shared.config.NewsTechProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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

	private final ObjectProvider<AnthropicClient> clientProvider;
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
			catch (RuntimeException ex) {
				log.error("Falha ao triar item {} ({}): {}", item.getId(), item.getUrl(), ex.getMessage());
				item.setStatus(RawItemStatus.NEEDS_REVIEW);
				rawItemRepository.save(item);
			}
		}

		log.info("Triagem: {} itens processados de uma fila de {}", processed, queue.size());
		return processed;
	}

	private void applyTriage(RawItem item) {
		StructuredMessage<TriageResult> message = call(item);

		// Precisa vir antes de tocar em content(): numa recusa a lista nao traz o bloco
		// de texto esperado, e indexar direto quebraria com um erro que nao explica nada.
		if (message.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
			log.warn("Modelo recusou a triagem do item {} ({})", item.getId(), item.getUrl());
			item.setStatus(RawItemStatus.NEEDS_REVIEW);
			rawItemRepository.save(item);
			return;
		}
		if (message.stopReason().filter(StopReason.MAX_TOKENS::equals).isPresent()) {
			throw new IllegalStateException(
					"Resposta truncada por max_tokens - aumentar newstech.claude.triage-max-tokens");
		}

		TriageResult result = extractResult(message);
		Usage usage = message.usage();

		item.setTriage(new Triage(
				result.topics(),
				result.entities(),
				result.language(),
				result.relevanceScore(),
				result.reasoning(),
				properties.claude().triageModel(),
				usage.inputTokens(),
				usage.outputTokens(),
				usage.cacheReadInputTokens().orElse(0L),
				Instant.now()));

		boolean relevant = result.relevanceScore() >= properties.claude().relevanceThreshold();
		item.setStatus(relevant ? RawItemStatus.TRIAGED : RawItemStatus.DISCARDED);
		rawItemRepository.save(item);

		log.debug("Item {} triado: score {} -> {}", item.getId(), result.relevanceScore(), item.getStatus());
	}

	private StructuredMessage<TriageResult> call(RawItem item) {
		StructuredMessageCreateParams<TriageResult> params = MessageCreateParams.builder()
				.model(properties.claude().triageModel())
				.maxTokens(properties.claude().triageMaxTokens())
				.systemOfTextBlockParams(List.of(TextBlockParam.builder()
						.text(TriagePrompt.SYSTEM)
						// Prefixo identico em toda chamada: TTL de 1h porque os ciclos
						// sao espacados e um TTL de 5min expiraria entre eles.
						.cacheControl(CacheControlEphemeral.builder()
								.ttl(CacheControlEphemeral.Ttl.TTL_1H)
								.build())
						.build()))
				.addUserMessage(TriagePrompt.userMessage(item))
				.outputConfig(TriageResult.class)
				.build();

		return clientProvider.getObject().messages().create(params);
	}

	private TriageResult extractResult(StructuredMessage<TriageResult> message) {
		return message.content().stream()
				.filter(StructuredContentBlock::isText)
				.map(StructuredContentBlock::asText)
				.map(StructuredTextBlock::text)
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("Resposta sem bloco de texto estruturado"));
	}

}
