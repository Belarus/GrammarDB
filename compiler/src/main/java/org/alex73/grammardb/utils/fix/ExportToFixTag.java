package org.alex73.grammardb.utils.fix;

import org.alex73.grammardb.GrammarDB2;
import org.alex73.grammardb.SetUtils;
import org.alex73.grammardb.structures.Form;
import org.alex73.grammardb.structures.Paradigm;
import org.alex73.grammardb.structures.Variant;
import org.alex73.grammardb.tags.BelarusianTags;
import org.alex73.grammardb.tags.TagLetter;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Экспартуе формы, у тэгах якіх ёсць літара 'X', у файл fixtag.tsv.
 * Паказвае статыстыку: у колькіх групах колькі значэнняў 'X'.
 */
public class ExportToFixTag {

    public static void main(String[] args) throws Exception {
        GrammarDB2 db = GrammarDB2.initializeFromDir("data");
        BelarusianTags bt = new BelarusianTags();

        List<String[]> rows = new ArrayList<>();
        Map<String, Integer> groupXCount = new LinkedHashMap<>();

        for (Paradigm p : db.getAllParadigms()) {
            for (Variant v : p.getVariant()) {
                for (Form f : v.getForm()) {
                    String value = f.getValue();
                    if (value.isEmpty()) {
                        continue;
                    }
                    String tag = SetUtils.tag(p, v, f);
                    if (!tag.contains("X")) {
                        continue;
                    }
                    // Знаходзім назву першай групы, у якой 'X'
                    String firstXGroup = null;
                    TagLetter current = bt.getRoot();
                    for (char c : tag.toCharArray()) {
                        TagLetter.OneLetterInfo info = current.getLetterInfo(c);
                        if (info == null) {
                            break;
                        }
                        if (c == 'X' && firstXGroup == null) {
                            firstXGroup = info.groupName;
                        }
                        current = info.nextLetters;
                    }
                    String pdgVariantId = p.getPdgId() + v.getId();
                    String variantLemma = v.getLemma();
                    rows.add(new String[] {firstXGroup != null ? firstXGroup : "", tag, pdgVariantId, variantLemma, value});

                    // Падлік 'X' па групах
                    current = bt.getRoot();
                    for (char c : tag.toCharArray()) {
                        TagLetter.OneLetterInfo info = current.getLetterInfo(c);
                        if (info == null) {
                            break;
                        }
                        if (c == 'X') {
                            groupXCount.merge(info.groupName, 1, Integer::sum);
                        }
                        current = info.nextLetters;
                    }
                }
            }
        }

        // Сартаванне і запіс TSV
        rows.sort(Comparator
                .comparing((String[] r) -> r[2])   // pdgId+variantId
                .thenComparing(r -> r[1])            // tag
                .thenComparing(r -> r[4]));          // форма

        try (BufferedWriter bw = Files.newBufferedWriter(Path.of("fixtag.tsv"))) {
            bw.write("група_X\ttag\tpdgId+variantId\tvariant_lemma\tформа");
            bw.newLine();
            for (String[] row : rows) {
                bw.write(String.join("\t", row));
                bw.newLine();
            }
        }

        // Статыстыка
        System.out.println("Статыстыка 'X' па групах:");
        groupXCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> System.out.println(e.getKey() + ": " + e.getValue()));
    }
}
