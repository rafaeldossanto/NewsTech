package com.web.newstech.curation.cluster;

import com.web.newstech.ingest.RawItem;

import java.time.Instant;
import java.util.List;

/**
 * Itens que o agrupamento considerou serem o mesmo fato.
 *
 * <p>É efêmero de propósito: vive durante o ciclo do pipeline e não vira coleção.
 * O que sobra dele é a story com {@code rawItemIds} preenchido.
 *
 * @param items em ordem de publicação, do mais antigo para o mais recente
 */
public record ItemCluster(List<RawItem> items) {

	/**
	 * Quem publicou primeiro. Não é necessariamente a fonte que vai virar o link
	 * principal da story - esse desempate usa o peso de confiabilidade e cabe ao
	 * estágio editorial, que tem acesso às fontes.
	 */
	public RawItem earliest() {
		return items.getFirst();
	}

	public boolean isSingleton() {
		return items.size() == 1;
	}

	public int size() {
		return items.size();
	}

	public Instant mostRecentPublication() {
		return items.getLast().getPublishedAt();
	}

	public List<String> ids() {
		return items.stream().map(RawItem::getId).toList();
	}

}
