package com.web.newstech.curation.editorial;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara o estágio editorial periodicamente.
 *
 * <p><strong>Desligado por padrão</strong>, como a triagem. É o mais caro dos dois: cada
 * ciclo pode gastar uma chamada de Opus por cluster. Ativar exige
 * {@code newstech.claude.auto-editorial=true} explícito.
 *
 * <p>Intervalo maior que o da triagem de propósito. Não adianta procurar cluster novo
 * mais rápido do que a triagem consegue alimentar a fila — só gastaria chamada para
 * encontrar os mesmos itens.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "newstech.claude.auto-editorial", havingValue = "true")
public class EditorialScheduler {

	private final EditorialService editorialService;

	@Scheduled(fixedDelayString = "${newstech.claude.editorial-interval}", initialDelayString = "PT5M")
	public void publish() {
		try {
			editorialService.publishPending();
		}
		catch (RuntimeException ex) {
			// Falha por cluster já é tratada no serviço; chegar aqui é falha do ciclo.
			// Engolir é proposital: exceção propagada mataria o agendamento.
			log.error("Ciclo editorial falhou por inteiro", ex);
		}
	}

}
