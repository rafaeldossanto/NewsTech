package com.web.newstech.curation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara a triagem periodicamente.
 *
 * <p><strong>Desligado por padrao.</strong> Este e o unico agendador do projeto que
 * gasta dinheiro: ligado, ele chama a API a cada ciclo, sozinho, para sempre. Ativar
 * exige {@code newstech.claude.auto-triage=true} explicito - e so depois de o limite
 * de gasto estar configurado no console da Anthropic.
 *
 * <p>Com ele desligado, a triagem ainda pode ser disparada sob demanda pelo admin,
 * que e como se calibra o prompt sem deixar nada rodando solto.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "newstech.claude.auto-triage", havingValue = "true")
public class TriageScheduler {

	private final TriageService triageService;

	@Scheduled(fixedDelayString = "${newstech.claude.triage-interval:PT15M}", initialDelayString = "PT2M")
	public void triage() {
		try {
			triageService.triageBatch();
		}
		catch (RuntimeException ex) {
			// Excecao propagada mataria o agendamento; falha de item ja e tratada no servico.
			log.error("Ciclo de triagem falhou por inteiro", ex);
		}
	}

}
