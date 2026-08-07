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
import com.web.newstech.shared.config.NewsTechProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudeTriageModel implements TriageModel {

	private final ObjectProvider<AnthropicClient> clientProvider;
	private final NewsTechProperties properties;

	@Override
	public TriageOutcome classify(RawItem item) {
		StructuredMessage<TriageResult> message = call(item);

		// Antes de tocar em content(): numa recusa a lista nao traz o bloco esperado.
		if (message.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
			throw new TriageRefusedException("Modelo recusou a triagem de " + item.getUrl());
		}
		if (message.stopReason().filter(StopReason.MAX_TOKENS::equals).isPresent()) {
			throw new TriageException("Resposta truncada - aumentar newstech.claude.triage-max-tokens");
		}

		TriageResult result = message.content().stream()
				.filter(StructuredContentBlock::isText)
				.map(StructuredContentBlock::asText)
				.map(StructuredTextBlock::text)
				.findFirst()
				.orElseThrow(() -> new TriageException("Resposta sem bloco de texto estruturado"));

		Usage usage = message.usage();
		return new TriageOutcome(
				result,
				properties.claude().triageModel(),
				usage.inputTokens(),
				usage.outputTokens(),
				usage.cacheReadInputTokens().orElse(0L));
	}

	private StructuredMessage<TriageResult> call(RawItem item) {
		StructuredMessageCreateParams<TriageResult> params = MessageCreateParams.builder()
				.model(properties.claude().triageModel())
				.maxTokens(properties.claude().triageMaxTokens())
				.systemOfTextBlockParams(List.of(TextBlockParam.builder()
						.text(TriagePrompt.SYSTEM)
						.cacheControl(CacheControlEphemeral.builder()
								.ttl(CacheControlEphemeral.Ttl.TTL_1H)
								.build())
						.build()))
				.addUserMessage(TriagePrompt.userMessage(item))
				.outputConfig(TriageResult.class)
				.build();

		try {
			return clientProvider.getObject().messages().create(params);
		}
		catch (RuntimeException ex) {
			throw new TriageException("Falha ao chamar o modelo de triagem", ex);
		}
	}

}
