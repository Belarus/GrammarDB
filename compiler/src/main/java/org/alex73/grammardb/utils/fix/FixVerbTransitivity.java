package org.alex73.grammardb.utils.fix;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.alex73.grammardb.GrammarDB2;
import org.alex73.grammardb.GrammarDBSaver;
import org.alex73.grammardb.StressUtils;
import org.alex73.grammardb.structures.Paradigm;
import org.alex73.grammardb.structures.Variant;

/**
 * Выстаўляе значэнне пераходнасці (2-я літара тэга дзеяслова) паводле файла Verbs_Х_done.txt.
 */
public class FixVerbTransitivity {

    public static void main(String[] args) throws Exception {
        Map<String, Character> transitivityByLemma = new HashMap<>();
        for (String rawLine : Files.readAllLines(Path.of("Verbs_Х_done.txt"), StandardCharsets.UTF_8)) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split(" - ", 2);
            if (parts.length != 2) {
                throw new RuntimeException("Няправільны радок: " + line);
            }
            String lemma = parts[0].trim().replace('\'', GrammarDB2.pravilny_apostraf);
            char transitivity = switch (parts[1].trim()) {
                case "Пераходны" -> 'T';
                case "Непераходны" -> 'I';
                case "Пераходны/непераходны" -> 'D';
                default -> throw new RuntimeException("Невядомае значэнне пераходнасці: " + line);
            };
            transitivityByLemma.put(lemma, transitivity);
        }

        GrammarDB2 db = GrammarDB2.initializeFromDir("data");
        int updated = 0;

        for (Paradigm p : db.getAllParadigms()) {
            String pTag = p.getTag();
            boolean isVerbParadigm = pTag != null && pTag.startsWith("V");
            boolean isVerbVariants = (pTag == null || pTag.isEmpty())
                    && p.getVariant().stream().anyMatch(v -> v.getTag() != null && v.getTag().startsWith("V"));
            if (!isVerbParadigm && !isVerbVariants) {
                continue;
            }

            String lemma = StressUtils.unstress(p.getLemma());
            Character transitivity = transitivityByLemma.get(lemma);
            if (transitivity == null) {
                continue;
            }

            if (pTag != null && pTag.startsWith("V") && pTag.length() >= 2) {
                p.setTag(replaceTransitivity(pTag, transitivity));
                updated++;
            } else {
                for (Variant v : p.getVariant()) {
                    String vTag = v.getTag();
                    if (vTag != null && vTag.startsWith("V") && vTag.length() >= 2) {
                        v.setTag(replaceTransitivity(vTag, transitivity));
                        updated++;
                    }
                }
            }
        }

        System.out.println("Абноўлена тэгаў: " + updated);
        GrammarDBSaver.sortAndStore(db, "data2");
    }

    private static String replaceTransitivity(String tag, char transitivity) {
        return tag.charAt(0) + String.valueOf(transitivity) + tag.substring(2);
    }
}
