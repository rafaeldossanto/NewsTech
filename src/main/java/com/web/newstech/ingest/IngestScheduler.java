package com.web.newstech.ingest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
			log.error("Ciclo de coleta falhou por inteiro", ex);
		}
	}

}
