package com.web.newstech.ingest.connector;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import com.web.newstech.ingest.ConnectorType;
import com.web.newstech.ingest.Source;
import com.web.newstech.shared.config.NewsTechProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Coleta de feeds RSS e Atom.
 *
 * <p>Sobre robots.txt: aqui so se consome o feed que a propria fonte publica para ser
 * consumido, com User-Agent identificavel e cabecalhos condicionais para nao gerar
 * trafego a toa. Nao ha crawl de pagina nem leitura do corpo do artigo - se um dia
 * for preciso buscar og:image ou texto da pagina, ai sim entra checagem de robots.txt.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RssConnector implements SourceConnector {

	private static final int HTTP_OK = 200;
	private static final int HTTP_NOT_MODIFIED = 304;

	private final NewsTechProperties properties;

	private HttpClient httpClient;

	@Override
	public ConnectorType type() {
		return ConnectorType.RSS;
	}

	@Override
	public FetchResult fetch(Source source) {
		HttpResponse<byte[]> response = request(source);

		if (response.statusCode() == HTTP_NOT_MODIFIED) {
			log.debug("Fonte '{}' respondeu 304, nada novo", source.getName());
			return FetchResult.unchanged();
		}
		if (response.statusCode() != HTTP_OK) {
			throw new FetchException("Fonte '%s' respondeu HTTP %d".formatted(source.getName(), response.statusCode()));
		}

		List<FetchedItem> items = parse(source, response.body());
		return FetchResult.of(items, header(response, "ETag"), header(response, "Last-Modified"));
	}

	private HttpResponse<byte[]> request(Source source) {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(source.getFeedUrl()))
				.header("User-Agent", properties.ingest().userAgent())
				.header("Accept", "application/rss+xml, application/atom+xml, application/xml;q=0.9, */*;q=0.8")
				.timeout(Duration.ofSeconds(properties.ingest().fetchTimeoutSeconds()))
				.GET();

		// Cabecalhos condicionais: na maioria dos ciclos a resposta e 304 e nao ha corpo para baixar.
		if (Objects.nonNull(source.getEtag())) {
			request.header("If-None-Match", source.getEtag());
		}
		if (Objects.nonNull(source.getLastModified())) {
			request.header("If-Modified-Since", source.getLastModified());
		}

		try {
			return client().send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new FetchException("Coleta de '%s' interrompida".formatted(source.getName()), ex);
		}
		catch (Exception ex) {
			throw new FetchException("Falha de rede ao coletar '%s'".formatted(source.getName()), ex);
		}
	}

	private List<FetchedItem> parse(Source source, byte[] body) {
		try (InputStream stream = new ByteArrayInputStream(body)) {
			SyndFeed feed = new SyndFeedInput().build(new XmlReader(stream));
			return feed.getEntries().stream()
					.limit(properties.ingest().maxItemsPerFetch())
					.map(entry -> toFetchedItem(entry))
					.filter(item -> Objects.nonNull(item.url()) && Objects.nonNull(item.title()))
					.toList();
		}
		catch (Exception ex) {
			throw new FetchException("Feed de '%s' nao pode ser lido".formatted(source.getName()), ex);
		}
	}

	private FetchedItem toFetchedItem(SyndEntry entry) {
		String url = entry.getLink();
		// Nem todo feed traz guid; a url e o identificador estavel de fallback.
		String externalId = Objects.nonNull(entry.getUri()) && !entry.getUri().isBlank() ? entry.getUri() : url;

		return new FetchedItem(
				externalId,
				sanitize(entry.getTitle()),
				url,
				resolvePublishedAt(entry),
				sanitize(rawSummary(entry)));
	}

	/**
	 * Remove todo o HTML do resumo.
	 *
	 * <p>Nao e cosmetico: feed traz markup arbitrario de terceiros, e jogar isso num
	 * template sem limpar e XSS no proprio portal.
	 */
	private String sanitize(String raw) {
		if (Objects.isNull(raw) || raw.isBlank()) {
			return null;
		}
		return Jsoup.parse(raw).text().trim();
	}

	private String rawSummary(SyndEntry entry) {
		if (Objects.nonNull(entry.getDescription())) {
			return entry.getDescription().getValue();
		}
		return entry.getContents().stream()
				.findFirst()
				.map(SyndContent::getValue)
				.orElse(null);
	}

	/** Atom usa updated, RSS usa pubDate; alguns feeds nao trazem nenhum dos dois. */
	private Instant resolvePublishedAt(SyndEntry entry) {
		return Optional.ofNullable(entry.getPublishedDate())
				.or(() -> Optional.ofNullable(entry.getUpdatedDate()))
				.map(Date::toInstant)
				.orElse(null);
	}

	private String header(HttpResponse<byte[]> response, String name) {
		return response.headers().firstValue(name).orElse(null);
	}

	private synchronized HttpClient client() {
		if (Objects.isNull(httpClient)) {
			httpClient = HttpClient.newBuilder()
					.connectTimeout(Duration.ofSeconds(properties.ingest().fetchTimeoutSeconds()))
					.followRedirects(HttpClient.Redirect.NORMAL)
					.build();
		}
		return httpClient;
	}

}
