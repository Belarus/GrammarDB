package org.alex73.grammardb.utils.fix;

import org.alex73.grammardb.GrammarDB2;
import org.alex73.grammardb.SetUtils;
import org.alex73.grammardb.structures.Form;
import org.alex73.grammardb.structures.Paradigm;
import org.alex73.grammardb.structures.Variant;
import org.alex73.grammardb.tags.BelarusianTags;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Экспартуе формы некаторых тэгаў для праверкі, што род непатрэбны.
 */
public class ExportMnoznyLik {
    static GrammarDB2 db;
    static BelarusianTags bt = new BelarusianTags();

    public static void main(String[] args) throws Exception {
        db = GrammarDB2.initializeFromDir("data");
        export(filterTagGroup("Часціна мовы", 'V'), filterTagGroup("Род", 'X'));
        export(filterTagGroup("Часціна мовы", 'N'), filterTagGroup("Род", 'P'));
        export(filterTagGroup("Часціна мовы", 'M'), filterTagGroup("Род", 'P'));
        export(filterTagGroup("Часціна мовы", 'S'), filterTagGroup("Род", '0'));
        export(filterTagGroup("Часціна мовы", 'S'), filterTagGroup("Формы", '1'));
        export(filterTagGroup("Часціна мовы", 'A'), filterTagGroup("Род", 'P'));
        export(filterTagGroup("Часціна мовы", 'P'), filterTagGroup("Род", 'P'));
        export(filterTagGroup("Часціна мовы", 'C'), filterTagGroup("Злучальны", 'X'));
        export(filterTagGroup("Часціна мовы", 'C'), filterTagGroup("Падпарадкавальны", 'X'));
        export(filterTagGroup("Часціна мовы", 'V'), filterTagGroup("Пераходнасць", 'X'));
        export(filterTagGroup("Часціна мовы", 'R'), filterTagGroup("Спосаб утварэння", 'X'));
    }

    public static void export(GrammarPredicateGroup... filters) throws Exception {
        FixTags.GrammarPredicate filter = filterMultiple(filters);

        List<String[]> rows = new ArrayList<>();
        for (Paradigm p : db.getAllParadigms()) {
            for (Variant v : p.getVariant()) {
                for (Form f : v.getForm()) {
                    String tag = SetUtils.tag(p, v, f);
                    if (filter.test(p, v, f, tag)) {
                        rows.add(new String[]{tag, p.getPdgId() + v.getId(), v.getLemma(), f.getValue()});
                    }
                }
            }
        }

        // Сартаванне і запіс TSV
        rows.sort(Comparator.comparing((String[] r) -> r[0])   // tag
                .thenComparing(r -> r[2])  // variantLemma
                .thenComparing(r -> r[3])  // форма
                .thenComparing(r -> r[1]));// pdgId+variantId

        String fn = List.of(filters).stream().map(f -> f.group().substring(0, 3).toLowerCase() + "=" + f.value()).reduce((a, b) -> a + "_" + b).get() + ".tsv";

        try (BufferedWriter bw = Files.newBufferedWriter(Path.of(fn))) {
            bw.write("tag\tpdgId+variantId\tvariant_lemma\tформа");
            bw.newLine();
            for (String[] row : rows) {
                bw.write(String.join("\t", row));
                bw.newLine();
            }
        }
    }


    public static FixTags.GrammarPredicate filterMultiple(GrammarPredicateGroup... filters) {
        return (p, v, f, tag) -> {
            for (GrammarPredicateGroup filter : filters) {
                if (!filter.test(p, v, f, tag)) {
                    return false;
                }
            }
            return true;
        };
    }

    public static GrammarPredicateGroup filterTagGroup(String group, char value) {
        return new GrammarPredicateGroup() {
            @Override
            public boolean test(Paradigm p, Variant v, Form f, String tag) {
                return bt.getValueOfGroup(tag, group) == value;
            }

            @Override
            public String group() {
                return group;
            }

            @Override
            public char value() {
                return value;
            }
        };
    }

    public interface GrammarPredicateGroup {
        boolean test(Paradigm p, Variant v, Form f, String tag);
        String group();
        char value();
    }
}
