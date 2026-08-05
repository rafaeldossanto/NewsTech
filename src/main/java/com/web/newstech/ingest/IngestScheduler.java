package com.web.newstech.ingest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara a coleta periodicamente.
 *
 * <p>{@code fixedDelay} e nao {@code fixedRate}: o intervalo conta a partir do fim do
 * ciclo anterior, entao um ciclo lento nunca acumula execucoes sobrepostas.
 *
 * <p>Ao rodar em mais de uma instancia isto vira coleta duplicada. Os indices unicos
 * de {@code rawItems} evitam dado duplicado, mas nao o trafego desnecessario nas fontes -
 * na fase 4, com mais de uma instancia, entra trava distribuida.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestScheduler {

	private final IngestService ingestService;

	@Scheduled(fixedDelayString = "${newstech.ingest.interval}", initialDelayString = "PT30S")
	public void collect() {
		try {
			ingestService.collectAll();
		}
		catch (RuntimeException ex) {
			// O ciclo ja trata falha por fonte; chegar aqui e falha do proprio ciclo.
			// Engolir e proposital: excecao propagada mataria o agendamento.
			log.error("Ciclo de coleta falhou por inteiro", ex);
		}
	}

}
