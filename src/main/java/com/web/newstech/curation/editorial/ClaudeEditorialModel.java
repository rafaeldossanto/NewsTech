package com.web.newstech.curation.editorial;

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
import com.web.newstech.content.repository.TopicRepository;
import com.web.newstech.content.repository.TrackedEntityRepository;
import com.web.newstech.curation.cluster.ItemCluster;
import com.web.newstech.curation.exceptions.EditorialException;
import com.web.newstech.curation.exceptions.ModelRefusedException;
import com.web.newstech.shared.config.NewsTechProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Estágio 2 rodando no Opus 5.
 *
 * <p>É o único ponto do sistema que decide o que vai ao ar e escreve o texto que o leitor
 * lê — daí valer o modelo mais caro num volume que é baixo por natureza: dezenas de
 * chamadas por dia, não centenas.
 *
 * <p><strong>Sobre o effort:</strong> não é configurado aqui. No SDK, o parâmetro de
 * effort e o schema de saída estruturada ocupam o mesmo campo {@code outputConfig}, e
 * definir os dois em sequência faz um sobrescrever o outro. Perder o schema seria bem
 * pior do que usar o effort padrão do modelo, então o padrão fica. Se a calibração
 * mostrar que vale ajustar, o caminho é montar um {@code OutputConfig} único com
 * {@code format} e {@code effort} juntos — e conferir contra uma chamada real, porque
 * o comportamento dessa combinação não dá para deduzir da assinatura.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudeEditorialModel implements EditorialModel {

	private final ObjectProvider<AnthropicClient> clientProvider;
	private final TopicRepository topicRepository;
	private final TrackedEntityRepository entityRepository;
	private final NewsTechProperties properties;

	@Override
	public EditorialOutcome decide(ItemCluster cluster) {
		StructuredMessage<EditorialDecision> message = call(cluster);

		// Precisa vir antes de tocar em content(): numa recusa a lista não traz o bloco de
		// texto esperado, e indexar direto quebraria com um erro que não explica nada.
		if (message.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
			throw new ModelRefusedException(
					"Modelo recusou a curadoria do cluster iniciado por: " + cluster.earliest().getUrl());
		}
		if (message.stopReason().filter(StopReason.MAX_TOKENS::equals).isPresent()) {
			// Metade de uma manchete é pior que manchete nenhuma.
			throw new EditorialException(
					"Resposta truncada por max_tokens — aumentar newstech.claude.editorial-max-tokens");
		}

		EditorialDecision decision = message.content().stream()
				.filter(StructuredContentBlock::isText)
				.map(StructuredContentBlock::asText)
				.map(StructuredTextBlock::text)
				.findFirst()
				.orElseThrow(() -> new EditorialException("Resposta sem bloco de texto estruturado"));

		Usage usage = message.usage();
		return new EditorialOutcome(
				decision,
				properties.claude().editorialModel(),
				usage.inputTokens(),
				usage.outputTokens(),
				usage.cacheReadInputTokens().orElse(0L));
	}

	private StructuredMessage<EditorialDecision> call(ItemCluster cluster) {
		String system = EditorialPrompt.system(
				topicRepository.findByActiveTrueOrderByDisplayOrderAsc(),
				entityRepository.findAll());

		StructuredMessageCreateParams<EditorialDecision> params = MessageCreateParams.builder()
				.model(properties.claude().editorialModel())
				// Cobre thinking + resposta. Apertado demais, a peça sai truncada no meio.
				.maxTokens(properties.claude().editorialMaxTokens())
				.systemOfTextBlockParams(List.of(TextBlockParam.builder()
						.text(system)
						// Prefixo estável entre chamadas. TTL de 1h porque os ciclos são
						// espaçados: com 5 minutos o cache expiraria entre um e outro.
						.cacheControl(CacheControlEphemeral.builder()
								.ttl(CacheControlEphemeral.Ttl.TTL_1H)
								.build())
						.build()))
				.addUserMessage(EditorialPrompt.userMessage(cluster))
				.outputConfig(EditorialDecision.class)
				.build();

		try {
			return clientProvider.getObject().messages().create(params);
		}
		catch (RuntimeException ex) {
			throw new EditorialException("Falha ao chamar o modelo editorial", ex);
		}
	}

}
