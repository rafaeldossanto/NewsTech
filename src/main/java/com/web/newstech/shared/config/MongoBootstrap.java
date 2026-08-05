package com.web.newstech.shared.config;

import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ValidationAction;
import com.mongodb.client.model.ValidationLevel;
import com.mongodb.client.model.ValidationOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Cria indices e validadores de colecao na subida da aplicacao.
 *
 * <p>Feito na mao, e nao por {@code auto-index-creation} + anotacao, porque aqui os
 * indices precisam de coisas que a anotacao nao expressa bem: TTL ajustavel por
 * configuracao, indice de texto composto e o validador {@code $jsonSchema}.
 *
 * <p>Usa o driver do MongoDB diretamente em vez da API do Spring Data: e uma API mais
 * estavel entre versoes e deixa explicito o documento que esta sendo enviado ao servidor.
 *
 * <p>Todas as operacoes sao idempotentes.
 *
 * <p>Roda em {@code afterPropertiesSet} e nao como {@code ApplicationRunner} de proposito:
 * runner nao e executado por {@code @SpringBootTest}, e o teste de integracao precisa
 * exercitar este bootstrap. Como efeito colateral desejado, a aplicacao falha na subida
 * se o banco estiver inacessivel, em vez de subir e quebrar na primeira query.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MongoBootstrap implements InitializingBean {

	public static final String SOURCES = "sources";
	public static final String RAW_ITEMS = "rawItems";
	public static final String STORIES = "stories";
	public static final String TOPICS = "topics";
	public static final String ENTITIES = "entities";

	/** Codigo do erro IndexOptionsConflict do MongoDB. */
	private static final int INDEX_OPTIONS_CONFLICT = 85;

	private static final String RAW_ITEM_TTL_INDEX = "ttl_fetchedAt";

	private final MongoTemplate mongoTemplate;
	private final NewsTechProperties properties;

	@Override
	public void afterPropertiesSet() {
		MongoDatabase database = mongoTemplate.getDb();

		applyStoryValidator(database);
		createSourceIndexes(database);
		createRawItemIndexes(database);
		createStoryIndexes(database);
		createTaxonomyIndexes(database);

		log.info("MongoBootstrap concluido: indices e validadores aplicados em '{}'", database.getName());
	}

	/**
	 * Garante no banco o invariante que sustenta o modelo editorial: nenhuma story
	 * existe sem pelo menos uma fonte, e toda fonte tem nome e link.
	 *
	 * <p>A validacao vive aqui, e nao so no {@code @NotEmpty} do record, porque precisa
	 * valer tambem para escrita que nao passa pela aplicacao - script de migracao,
	 * correcao manual no Compass, importacao em massa.
	 */
	private void applyStoryValidator(MongoDatabase database) {
		Document validator = new Document("$jsonSchema", storySchema());

		if (mongoTemplate.collectionExists(STORIES)) {
			database.runCommand(new Document("collMod", STORIES)
					.append("validator", validator)
					.append("validationLevel", ValidationLevel.STRICT.getValue())
					.append("validationAction", ValidationAction.ERROR.getValue()));
			log.debug("Validador da colecao '{}' atualizado", STORIES);
			return;
		}

		database.createCollection(STORIES, new CreateCollectionOptions()
				.validationOptions(new ValidationOptions()
						.validator(validator)
						.validationLevel(ValidationLevel.STRICT)
						.validationAction(ValidationAction.ERROR)));
		log.debug("Colecao '{}' criada com validador", STORIES);
	}

	private Document storySchema() {
		Document sourceEntry = new Document()
				.append("bsonType", "object")
				.append("required", List.of("sourceName", "articleUrl"))
				.append("properties", new Document()
						.append("sourceName", nonEmptyString("nome da fonte, exibido como credito"))
						.append("sourceUrl", new Document("bsonType", "string"))
						.append("articleUrl", nonEmptyString("link canonico para o artigo original"))
						.append("publishedAt", new Document("bsonType", List.of("date", "null"))));

		return new Document()
				.append("bsonType", "object")
				.append("required", List.of("headline", "summary", "slug", "publishedAt", "sources"))
				.append("properties", new Document()
						.append("headline", nonEmptyString("manchete da peca"))
						.append("summary", nonEmptyString("resumo proprio - nunca o texto integral da fonte"))
						.append("slug", nonEmptyString("identificador da url"))
						.append("publishedAt", new Document("bsonType", "date"))
						.append("sources", new Document()
								.append("bsonType", "array")
								.append("minItems", 1)
								.append("description", "toda story publicada precisa de ao menos uma fonte rastreavel")
								.append("items", sourceEntry)));
	}

	private Document nonEmptyString(String description) {
		return new Document()
				.append("bsonType", "string")
				.append("minLength", 1)
				.append("description", description);
	}

	private void createSourceIndexes(MongoDatabase database) {
		createIndex(database, SOURCES, Indexes.ascending("feedUrl"),
				new IndexOptions().name("uk_feedUrl").unique(true));
		createIndex(database, SOURCES, Indexes.ascending("active"),
				new IndexOptions().name("idx_active"));
	}

	private void createRawItemIndexes(MongoDatabase database) {
		// Dedupe global: o mesmo fato publicado em duas fontes com titulo e url iguais entra uma vez so.
		createIndex(database, RAW_ITEMS, Indexes.ascending("contentHash"),
				new IndexOptions().name("uk_contentHash").unique(true));

		// Dedupe por fonte: reprocessar o mesmo feed nao duplica nada.
		createIndex(database, RAW_ITEMS, Indexes.ascending("sourceId", "externalId"),
				new IndexOptions().name("uk_source_externalId").unique(true));

		// Fila do pipeline: "o que ainda nao foi triado, mais recente primeiro".
		createIndex(database, RAW_ITEMS, Indexes.compoundIndex(
						Indexes.ascending("status"), Indexes.descending("publishedAt")),
				new IndexOptions().name("idx_status_publishedAt"));

		createRawItemTtlIndex(database);
	}

	/**
	 * TTL da materia-prima. Tratado a parte porque e o unico indice cujas opcoes mudam
	 * por configuracao: alterar {@code raw-item-retention-days} faria o createIndex
	 * falhar com IndexOptionsConflict, entao o conflito e resolvido com collMod.
	 */
	private void createRawItemTtlIndex(MongoDatabase database) {
		long retentionSeconds = Duration.ofDays(properties.mongo().rawItemRetentionDays()).toSeconds();
		Bson key = Indexes.ascending("fetchedAt");
		IndexOptions options = new IndexOptions()
				.name(RAW_ITEM_TTL_INDEX)
				.expireAfter(retentionSeconds, TimeUnit.SECONDS);

		try {
			database.getCollection(RAW_ITEMS).createIndex(key, options);
		}
		catch (MongoCommandException ex) {
			if (ex.getErrorCode() != INDEX_OPTIONS_CONFLICT) {
				throw ex;
			}
			database.runCommand(new Document("collMod", RAW_ITEMS)
					.append("index", new Document("name", RAW_ITEM_TTL_INDEX)
							.append("expireAfterSeconds", retentionSeconds)));
			log.info("Retencao de '{}' ajustada para {} dias", RAW_ITEMS, properties.mongo().rawItemRetentionDays());
		}
	}

	private void createStoryIndexes(MongoDatabase database) {
		createIndex(database, STORIES, Indexes.ascending("slug"),
				new IndexOptions().name("uk_slug").unique(true));

		// Home: as mais recentes primeiro.
		createIndex(database, STORIES, Indexes.descending("publishedAt"),
				new IndexOptions().name("idx_publishedAt"));

		// Paginas de topico e hubs de entidade. Multikey: os dois campos sao arrays.
		createIndex(database, STORIES, Indexes.compoundIndex(
						Indexes.ascending("topics"), Indexes.descending("publishedAt")),
				new IndexOptions().name("idx_topics_publishedAt"));
		createIndex(database, STORIES, Indexes.compoundIndex(
						Indexes.ascending("entities"), Indexes.descending("publishedAt")),
				new IndexOptions().name("idx_entities_publishedAt"));

		// Busca. Uma colecao so pode ter um indice de texto, entao ja nasce com os dois campos.
		createIndex(database, STORIES, Indexes.compoundIndex(
						Indexes.text("headline"), Indexes.text("summary")),
				new IndexOptions().name("txt_headline_summary"));
	}

	private void createTaxonomyIndexes(MongoDatabase database) {
		createIndex(database, TOPICS, Indexes.ascending("slug"),
				new IndexOptions().name("uk_slug").unique(true));
		createIndex(database, ENTITIES, Indexes.ascending("slug"),
				new IndexOptions().name("uk_slug").unique(true));
		// Resolve "Anthropic", "anthropic" e "@AnthropicAI" para a mesma entidade.
		createIndex(database, ENTITIES, Indexes.ascending("aliases"),
				new IndexOptions().name("idx_aliases"));
	}

	private void createIndex(MongoDatabase database, String collection, Bson key, IndexOptions options) {
		database.getCollection(collection).createIndex(key, options);
	}

}
