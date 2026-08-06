package com.web.newstech.shared.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuracao propria da aplicacao, sob o prefixo {@code newstech}.
 */
@Validated
@ConfigurationProperties(prefix = "newstech")
public record NewsTechProperties(Mongo mongo, Ingest ingest, Claude claude, Cluster cluster, Admin admin) {

	public record Cluster(

			/* Janela em que dois itens ainda podem ser o mesmo fato. Larga demais junta
			 * cobertura de assuntos recorrentes; estreita demais perde repercussão tardia. */
			@Positive int windowHours,

			/* Pontuação mínima para agrupar. Calibrar contra casos reais, não no escuro:
			 * os clusters do primeiro lote coletado pontuaram entre 0,36 e 0,60. */
			double threshold) {
	}

	public record Admin(

			@NotBlank String username,

			/* Vem em texto puro por variavel de ambiente e e codificada em memoria na subida -
			 * nunca e persistida. Em prod nao ha default: sem a variavel, a aplicacao nao sobe. */
			@NotBlank String password) {
	}

	public record Mongo(

			/*
			 * Vira o expireAfterSeconds do indice TTL em rawItems.fetchedAt.
			 * Mudar este valor e subir a aplicacao ja ajusta o indice existente.
			 */
			@Positive int rawItemRetentionDays) {
	}

	public record Ingest(

			/* Identifica o bot para os donos das fontes. Manter um contato aqui e cortesia
			 * basica: quem opera o site precisa saber quem esta consumindo e como reclamar. */
			@NotBlank String userAgent,

			@Positive int fetchTimeoutSeconds,

			/* Teto de itens por coleta. Protege contra feed que devolve o arquivo inteiro. */
			@Positive int maxItemsPerFetch,

			Duration interval,

			/* Backoff exponencial por fonte: base * 2^(falhas-1), limitado pelo teto.
			 * Fonte fora do ar para de ser consultada a cada ciclo. */
			@Positive int backoffBaseMinutes,

			@Positive int backoffMaxHours) {
	}

	public record Claude(

			@NotBlank String triageModel,

			/* Inclui thinking + resposta. Apertado demais trunca a saida no meio. */
			@Positive long triageMaxTokens,

			/* Corte de relevancia da triagem. Calibrar lendo casos reais antes de mexer. */
			@Min(0) @Max(100) int relevanceThreshold,

			/* Quantos itens o estagio 1 processa por ciclo. */
			@Positive int triageBatchSize,

			@NotBlank String editorialModel,

			/* Inclui thinking + resposta. Generoso de proposito: peca truncada no meio e
			 * pior que peca nenhuma, e o custo do teto alto e zero se nao for usado. */
			@Positive long editorialMaxTokens,

			/* Quantos clusters o estagio 2 processa por ciclo. Cada um e uma chamada paga. */
			@Positive int editorialBatchSize,

			/* Agendadores que gastam dinheiro. Default false nos dois: so ligam por decisao
			 * explicita, depois do limite de gasto configurado no console da Anthropic. */
			boolean autoTriage,

			boolean autoEditorial,

			Duration triageInterval,

			Duration editorialInterval) {
	}

}
