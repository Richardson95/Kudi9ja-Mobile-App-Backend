package com.quadrilateral.kudi9ja.domain.legal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quadrilateral.kudi9ja.config.Kudi9jaProperties;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads version 1.0 of each document on an empty database.
 *
 * <p>The files under {@code resources/legal} are the documents the app shipped,
 * exported from the client rather than retyped, so the wording a customer
 * accepts here is character-for-character the wording they read there. A
 * transcription would risk a difference between what was shown and what is held
 * as evidence of it, which is the one difference that must not exist.
 *
 * <p>Seeding never overwrites. Once a version exists it is left alone.
 */
@Component
public class LegalSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegalSeeder.class);

    private final LegalDocumentRepository documents;
    private final LegalAcceptanceRepository acceptances;
    private final ObjectMapper objectMapper;
    private final Kudi9jaProperties properties;

    public LegalSeeder(
            LegalDocumentRepository documents,
            LegalAcceptanceRepository acceptances,
            ObjectMapper objectMapper,
            Kudi9jaProperties properties) {
        this.documents = documents;
        this.acceptances = acceptances;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.bootstrap().seedLegalDocuments()) {
            return;
        }
        for (LegalDocumentKind kind : LegalDocumentKind.values()) {
            seed(kind, "1.0");
        }
    }

    private void seed(LegalDocumentKind kind, String version) {
        // A published document is frozen the moment somebody agrees to it.
        //
        // Before that it is a draft that happens to live in a database, and
        // editing the file should update what is served — otherwise a typo, a
        // wrong address or a phone number that does not answer is stuck in
        // production until somebody remembers to bump a version, which is
        // exactly how a document ends up publishing something untrue.
        //
        // After that it is a record of what a person agreed to, and rewriting
        // it would make the acceptance a lie. So the count of acceptances is
        // what decides, not a flag anyone can forget to set: nobody has agreed
        // to it, refresh it; somebody has, leave it alone and publish a new
        // version instead.
        LegalDocument existing = documents.findByKindAndVersion(kind, version).orElse(null);
        if (existing != null) {
            long accepted = acceptances.countByDocumentId(existing.getId());
            if (accepted > 0) {
                return;
            }
            log.info("{} version {} has not been accepted by anyone; refreshing it from the "
                    + "shipped file", kind.defaultTitle(), version);
        }

        String path = "legal/" + kind.id() + "-" + version + ".json";
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            log.warn("No seed file at {}; {} will not be served until it is published",
                    path, kind.defaultTitle());
            return;
        }

        try (InputStream in = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));

            LegalDocument document = existing == null ? new LegalDocument() : existing;
            if (existing == null) {
                document.setId(UUID.randomUUID());
            }
            document.setKind(kind);
            document.setVersion(root.path("version").asText(version));
            document.setTitle(root.path("title").asText(kind.defaultTitle()));
            document.setShortTitle(root.path("shortTitle").asText(kind.defaultTitle()));
            document.setSummary(root.path("summary").asText(""));
            document.setReadMinutes(root.path("readMinutes").asInt(10));
            document.setBodyJson(objectMapper.writeValueAsString(root.path("sections")));
            document.setEffectiveFrom(parseEffective(root.path("effective").asText(null)));
            document.setPublishedAt(Instant.now());
            document.setPublishedBy("System (shipped with the app)");
            document.setChangeSummary(existing == null
                    ? "First published version."
                    : "Refreshed from the shipped file before anyone had accepted it.");

            documents.save(document);
            log.info("{} {} version {} ({} sections)",
                    existing == null ? "Seeded" : "Refreshed",
                    kind.defaultTitle(), document.getVersion(), root.path("sections").size());
        } catch (Exception e) {
            throw new IllegalStateException("Could not seed the " + kind.defaultTitle() + " from " + path, e);
        }
    }

    /**
     * The exporter writes a local date-time with no zone. It is a Lagos date:
     * the document takes effect on a day in Nigeria, not at a UTC instant.
     */
    private static Instant parseEffective(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return LocalDateTime.parse(value)
                    .atZone(com.quadrilateral.kudi9ja.common.util.Dates.LAGOS)
                    .toInstant();
        }
    }
}
