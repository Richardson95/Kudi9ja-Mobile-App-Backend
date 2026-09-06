package com.quadrilateral.kudi9ja;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * No two request or response records share a name.
 *
 * <p>This test exists because two did. {@code SignupDtos.ReviewRequest} —
 * accepting the agreements — and {@code PayInDtos.ReviewRequest} — confirming a
 * payment claim — were both called {@code ReviewRequest}.
 *
 * <p>Java did not mind: they are nested in different classes and every call site
 * was correct. The OpenAPI document minded a great deal. Schemas there are keyed
 * by simple name, so one of the two won and the endpoints taking the other were
 * published with the wrong shape — the admin confirm and reject endpoints, which
 * take a single {@code note}, were documented as taking {@code acceptedVersions}
 * and {@code accepted}.
 *
 * <p>Nothing broke. That is what makes it worth a test: the server kept working,
 * every existing client kept working, and the only casualty was anyone who
 * trusted the published contract. They would build something that cannot work
 * and find out at runtime, against a shape the document told them was right.
 *
 * <p>Reading the source rather than the generated document is deliberate — it
 * needs no Spring context, so it runs in milliseconds and fails at the moment
 * the second record is named rather than after a deploy.
 */
@DisplayName("OpenAPI schema names")
class NoCollidingSchemaNamesTest {

    private static final Path DTO_DIR =
            Path.of("src/main/java/com/quadrilateral/kudi9ja/web/dto");

    /** {@code public record Name(} — the shape springdoc turns into a schema. */
    private static final Pattern RECORD =
            Pattern.compile("public\\s+record\\s+([A-Z][A-Za-z0-9_]*)\\s*\\(");

    @Test
    @DisplayName("no two DTO records share a simple name")
    void noDuplicates() throws IOException {
        Map<String, List<String>> byName = new LinkedHashMap<>();

        try (Stream<Path> files = Files.list(DTO_DIR)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher matcher = RECORD.matcher(source);
                while (matcher.find()) {
                    byName.computeIfAbsent(matcher.group(1), k -> new ArrayList<>())
                            .add(file.getFileName().toString());
                }
            }
        }

        List<String> collisions = byName.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(e -> e.getKey() + " in " + String.join(" and ", e.getValue()))
                .toList();

        assertThat(collisions)
                .as("Two records with one name collide in the OpenAPI document: only "
                        + "one is published, and every endpoint taking the other is "
                        + "documented with the wrong shape. Give one of them a name "
                        + "that says which it is.")
                .isEmpty();
    }

    /**
     * A guard on the guard.
     *
     * <p>If the pattern ever stops matching — a refactor, a formatting change —
     * the test above would find nothing and pass, which is the worst way for a
     * check to fail.
     */
    @Test
    @DisplayName("the scan actually finds the records")
    void theScanWorks() throws IOException {
        int found = 0;
        try (Stream<Path> files = Files.list(DTO_DIR)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher matcher = RECORD.matcher(
                        Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    found++;
                }
            }
        }

        assertThat(found)
                .as("the pattern has stopped matching, which would make the "
                        + "collision test pass by examining nothing")
                .isGreaterThan(50);
    }
}
