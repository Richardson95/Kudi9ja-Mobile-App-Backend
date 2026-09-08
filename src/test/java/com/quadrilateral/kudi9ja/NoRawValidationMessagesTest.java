package com.quadrilateral.kudi9ja;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every refusal is written for the customer who reads it.
 *
 * <p>This test exists because one was not. A customer part-way through opening
 * an account typed a short address and was shown <i>"size must be between 5 and
 * 400"</i> — Bean Validation's default, describing a constraint rather than
 * telling anybody what to do about it. Nineteen constraints were in that state,
 * across sign-up, loans, savings, circles and the admin panel.
 *
 * <p>The failure is quiet in exactly the way that matters: the endpoint works,
 * the validation is correct, every test passes, and the only person who ever
 * sees the problem is somebody who has already given up on the form.
 */
@DisplayName("Validation messages")
class NoRawValidationMessagesTest {

    private static final Path DTO_DIR =
            Path.of("src/main/java/com/quadrilateral/kudi9ja/web/dto");

    /** The constraints that produce a message a customer can end up reading. */
    private static final Pattern CONSTRAINT = Pattern.compile(
            "@(NotBlank|NotNull|NotEmpty|Size|Pattern|Min|Max|Positive|PositiveOrZero"
                    + "|DecimalMin|DecimalMax|Email|Past|Future)\\(([^)]*)\\)");

    @Test
    @DisplayName("no constraint falls back to the framework's own wording")
    void everyConstraintSpeaksToTheCustomer() throws IOException {
        List<String> bare = new ArrayList<>();

        try (Stream<Path> files = Files.list(DTO_DIR)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher matcher = CONSTRAINT.matcher(source);
                while (matcher.find()) {
                    if (matcher.group(2).contains("message")) {
                        continue;
                    }
                    int line = (int) source.substring(0, matcher.start()).chars()
                            .filter(c -> c == '\n').count() + 1;
                    bare.add(file.getFileName() + ":" + line + " @" + matcher.group(1));
                }
            }
        }

        assertThat(bare)
                .as("These fall back to Bean Validation's default wording, which "
                        + "describes the rule rather than telling the customer what "
                        + "to do — \"size must be between 5 and 400\" in front of "
                        + "somebody halfway through opening an account. Give each a "
                        + "message written for the person who will read it.")
                .isEmpty();
    }

    /**
     * A guard on the guard: if the pattern stops matching, the test above finds
     * nothing and passes, which is the worst way for a check to fail.
     */
    @Test
    @DisplayName("the scan actually finds constraints")
    void theScanWorks() throws IOException {
        int found = 0;
        try (Stream<Path> files = Files.list(DTO_DIR)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher matcher = CONSTRAINT.matcher(
                        Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    found++;
                }
            }
        }
        assertThat(found)
                .as("the pattern has stopped matching, so the check above is "
                        + "examining nothing")
                .isGreaterThan(50);
    }
}
