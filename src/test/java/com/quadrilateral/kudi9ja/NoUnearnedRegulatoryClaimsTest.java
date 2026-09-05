package com.quadrilateral.kudi9ja;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The application must not claim a licence it does not hold.
 *
 * <p>Kudi9ja asserts no CBN licence, no regulatory compliance and no NDIC
 * cover. The three legal documents shipped with the product <b>deny</b> these
 * things rather than claiming them — the Terms say in as many words that the
 * wallet is not a bank account and is not NDIC-insured — and the onboarding
 * footer names the company and its RC number in place of any such claim.
 *
 * <p>That is a deliberate position, and it is the sort of position that erodes
 * one well-meant marketing string at a time. So this test fails the build if a
 * claim reappears anywhere it should not.
 *
 * <p>Taking deposits and paying 17% is deposit-taking, which needs a CBN
 * licence; digital lending needs a state money-lender's licence and FCCPC
 * approval. If those are obtained, changing this test is the deliberate
 * decision that records it — which is exactly why the check is here rather
 * than in a style guide nobody reads.
 */
@DisplayName("The application claims no licence it does not hold")
class NoUnearnedRegulatoryClaimsTest {

    /**
     * Phrases that assert a standing the company does not have.
     *
     * <p>Deliberately narrow. "Licensed" on its own is an ordinary English
     * word; "licensed lender" is a regulatory claim.
     */
    private static final List<String> FORBIDDEN_CLAIMS = List.of(
            "licensed lender",
            "licenced lender",
            "licensed by the cbn",
            "licensed by cbn",
            "cbn licensed",
            "cbn-licensed",
            "cbn compliant",
            "cbn-compliant",
            "ndic insured",
            "ndic-insured",
            "insured by the ndic",
            "government backed",
            "government-backed",
            "fully regulated",
            "regulated by the cbn");

    /**
     * Where the denials legitimately live.
     *
     * <p>The legal documents have to be able to say "this is not NDIC-insured",
     * and this test file has to be able to name what it is looking for. Both
     * would otherwise fail the check they exist to enforce.
     */
    private static final List<String> EXEMPT_PATHS = List.of(
            "src/main/resources/legal/",
            "NoUnearnedRegulatoryClaimsTest.java");

    @Test
    @DisplayName("no source file asserts a licence, CBN compliance or NDIC cover")
    void noUnearnedClaimsInSource() throws IOException {
        List<String> offences = new ArrayList<>();

        for (Path root : List.of(Path.of("src/main/java"), Path.of("src/main/resources"))) {
            if (!Files.exists(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(Files::isRegularFile)
                        .filter(NoUnearnedRegulatoryClaimsTest::isText)
                        .filter(path -> EXEMPT_PATHS.stream()
                                .noneMatch(exempt -> normalise(path).contains(exempt)))
                        .forEach(path -> offences.addAll(claimsIn(path)));
            }
        }

        assertThat(offences)
                .as("""
                        A regulatory claim appeared in the source.

                        Kudi9ja holds no CBN licence, is not NDIC-insured, and the legal \
                        documents deny both. If that standing has actually been obtained, \
                        change the documents first and then this test — in that order.""")
                .isEmpty();
    }

    /**
     * The Terms deny deposit insurance in as many words.
     *
     * <p>Checked because the absence of a claim and a statement that the thing
     * is not true are different, and only the second is any use to a customer
     * deciding whether to put money in. If that sentence ever goes missing, the
     * position stops being stated and becomes merely unstated.
     *
     * <p>The document writes "Nigeria Deposit Insurance Corporation" out in
     * full rather than as an acronym, which is the right way round: a customer
     * reading it should not have to know what NDIC stands for.
     */
    @Test
    @DisplayName("the Terms still say what the wallet is not")
    void theDenialIsStillThere() throws IOException {
        Path terms = Path.of("src/main/resources/legal/terms-1.0.json");
        assertThat(terms).exists();

        String body = Files.readString(terms, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);

        assertThat(body)
                .as("the Terms must keep saying the wallet is not a bank account")
                .contains("not a bank account");
        assertThat(body)
                .as("the Terms must keep denying deposit insurance")
                .contains("not insured by the nigeria deposit insurance corporation");
        assertThat(body)
                .as("the Terms must keep saying no account numbers are issued")
                .contains("do not issue account numbers");
    }

    private static List<String> claimsIn(Path path) {
        List<String> found = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String lower = lines.get(i).toLowerCase(Locale.ROOT);
                for (String claim : FORBIDDEN_CLAIMS) {
                    int at = lower.indexOf(claim);
                    if (at >= 0 && !isDenied(lower, at)) {
                        found.add(path + ":" + (i + 1) + " — \"" + claim + "\"");
                    }
                }
            }
        } catch (IOException e) {
            // A file that cannot be read as UTF-8 text is not one carrying a
            // marketing claim.
            return List.of();
        }
        return found;
    }

    /**
     * Whether the phrase is being denied rather than asserted.
     *
     * <p>"Not NDIC-insured" and "NDIC-insured" contain the same words and mean
     * opposite things, and the sentence this product needs to be able to write
     * is the first one. So a negation immediately in front of the phrase makes
     * it a denial, which is exactly what is wanted.
     *
     * <p>Only the words directly before it are considered. A negation earlier
     * in a long line usually belongs to a different clause, and reading it as
     * covering this one would let a real claim through by sitting it after an
     * unrelated "no".
     */
    private static boolean isDenied(String line, int claimStart) {
        String before = line.substring(Math.max(0, claimStart - 24), claimStart);
        return Stream.of("not ", "never ", "no ", "neither ", "nor ", "n't ", "denies ", "without ")
                .anyMatch(before::endsWith);
    }

    private static boolean isText(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".java")
                || name.endsWith(".json")
                || name.endsWith(".yml")
                || name.endsWith(".yaml")
                || name.endsWith(".properties")
                || name.endsWith(".html")
                || name.endsWith(".sql")
                || name.endsWith(".txt");
    }

    private static String normalise(Path path) {
        return path.toString().replace('\\', '/');
    }
}
