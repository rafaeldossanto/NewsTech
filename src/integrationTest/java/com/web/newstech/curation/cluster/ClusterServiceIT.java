package com.web.newstech.curation.cluster;

import com.web.newstech.TestcontainersConfiguration;
import com.web.newstech.ingest.RawItem;
import com.web.newstech.ingest.RawItemRepository;
import com.web.newstech.ingest.RawItemStatus;
import com.web.newstech.ingest.Triage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O agrupamento saindo do repositório, com a janela temporal aplicada.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ClusterServiceIT {

	@Autowired
	private ClusterService clusterService;

	@Autowired
	private RawItemRepository rawItemRepository;

	@BeforeEach
	void limpar() {
		rawItemRepository.deleteAll();
	}

	@Test
	@DisplayName("junta a cobertura do mesmo fato e deixa o resto sozinho")
	void agrupaOMesmoFato() {
		Instant agora = Instant.now();

		salvar("Anthropic's AI used fake identities, malware in rogue attack on GitHub project",
				List.of("Anthropic", "OpenAI", "GitHub"), agora.minus(Duration.ofHours(3)));
		salvar("AI models shock UK testers by using fake identities to try to trick developers",
				List.of("OpenAI", "Anthropic"), agora.minus(Duration.ofHours(2)));
		salvar("Texas halts data center connections to power grid amid overwhelming demand",
				List.of(), agora.minus(Duration.ofHours(5)));
		salvar("Rust 1.97.1", List.of(), agora.minus(Duration.ofHours(8)));

		List<ItemCluster> clusters = clusterService.pendingClusters();

		assertThat(clusters).hasSize(3);
		assertThat(clusters).filteredOn(cluster -> !cluster.isSingleton())
				.singleElement()
				.satisfies(cluster -> {
					assertThat(cluster.size()).isEqualTo(2);
					assertThat(cluster.earliest().getTitle())
							.as("o cluster preserva quem publicou primeiro")
							.startsWith("Anthropic's AI");
				});
	}

	@Test
	@DisplayName("fora da janela temporal não agrupa, mesmo com título idêntico")
	void respeitaAJanela() {
		Instant agora = Instant.now();

		// Mesma cobertura, mas com uma semana de distância: é assunto recorrente,
		// não o mesmo fato. A janela é o que separa os dois casos.
		salvar("Nikita Bier steps down as X's head of product", List.of("Nikita Bier", "X"),
				agora.minus(Duration.ofHours(2)));
		salvar("Nikita Bier steps down as X's head of product", List.of("Nikita Bier", "X"),
				agora.minus(Duration.ofDays(7)));

		List<ItemCluster> clusters = clusterService.pendingClusters();

		assertThat(clusters)
				.as("o item antigo nem entra na consulta, que já filtra pela janela")
				.hasSize(1);
	}

	@Test
	@DisplayName("só considera itens já triados")
	void ignoraNaoTriados() {
		Instant agora = Instant.now();
		salvar("Anthropic's AI used fake identities, malware in rogue attack on GitHub project",
				List.of("Anthropic", "OpenAI"), agora.minus(Duration.ofHours(1)));

		RawItem naoTriado = novoItem("AI models shock UK testers by using fake identities",
				List.of("Anthropic", "OpenAI"), agora);
		naoTriado.setStatus(RawItemStatus.COLLECTED);
		rawItemRepository.save(naoTriado);

		assertThat(clusterService.pendingClusters())
				.as("item ainda não triado não tem entidades confiáveis para comparar")
				.hasSize(1);
	}

	@Test
	@DisplayName("três itens em cadeia acabam no mesmo grupo")
	void uniaoTransitiva() {
		Instant agora = Instant.now();

		// B casa com A e com C; A e C podem não casar diretamente, mas contam o mesmo fato.
		salvar("Gemini Robotics ER 2: powering robotics with video understanding and task orchestration",
				List.of("Google DeepMind", "Gemini"), agora.minus(Duration.ofHours(4)));
		salvar("Gemini Robotics 2 brings whole body intelligence to robots",
				List.of("Google DeepMind", "Gemini"), agora.minus(Duration.ofHours(3)));
		salvar("DeepMind lança nova geração de robótica com Gemini",
				List.of("Google DeepMind", "Gemini"), agora.minus(Duration.ofHours(2)));

		List<ItemCluster> clusters = clusterService.pendingClusters();

		assertThat(clusters).singleElement().satisfies(cluster -> assertThat(cluster.size()).isEqualTo(3));
	}

	private void salvar(String titulo, List<String> entidades, Instant publicadoEm) {
		rawItemRepository.save(novoItem(titulo, entidades, publicadoEm));
	}

	private RawItem novoItem(String titulo, List<String> entidades, Instant publicadoEm) {
		return RawItem.builder()
				.sourceId(UUID.randomUUID().toString())
				.externalId(UUID.randomUUID().toString())
				.title(titulo)
				.url("https://exemplo.test/" + UUID.randomUUID())
				.contentHash(UUID.randomUUID().toString())
				.publishedAt(publicadoEm)
				.fetchedAt(Instant.now())
				.status(RawItemStatus.TRIAGED)
				.triage(new Triage(List.of("ia"), entidades, "en", 80, "teste", "manual", 0, 0, 0, Instant.now()))
				.build();
	}

}
