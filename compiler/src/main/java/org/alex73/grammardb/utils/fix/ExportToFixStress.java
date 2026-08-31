package org.alex73.grammardb.utils.fix;

import org.alex73.grammardb.GrammarDB2;
import org.alex73.grammardb.SetUtils;
import org.alex73.grammardb.StressUtils;
import org.alex73.grammardb.structures.Form;
import org.alex73.grammardb.structures.Paradigm;
import org.alex73.grammardb.structures.Variant;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Экспартуе формы без націску ў файл nostress.tsv.
 */
public class ExportToFixStress {

    public static void main(String[] args) throws Exception {
        GrammarDB2 db = GrammarDB2.initializeFromDir("data");
        List<String[]> rows = new ArrayList<>();
        for (Paradigm p : db.getAllParadigms()) {
            for (Variant v : p.getVariant()) {
                for (Form f : v.getForm()) {
                    String value = f.getValue();
                    if (value.isEmpty()) {
                        continue;
                    }
                    if (!StressUtils.hasStress(value) && StressUtils.syllCount(value) > 1) {
                        String tag = SetUtils.tag(p, v, f);
                        String pdgVariantId = p.getPdgId() + v.getId();
                        String variantLemma = v.getLemma();
                        rows.add(new String[] {tag, pdgVariantId, variantLemma, value});
                    }
                }
            }
        }
        rows.sort(Comparator
                .comparing((String[] r) -> r[1])   // pdgId+variantId
                .thenComparing(r -> r[0])            // tag
                .thenComparing(r -> r[3]));          // форма

        try (BufferedWriter bw = Files.newBufferedWriter(Path.of("nostress.tsv"))) {
            bw.write("tag\tpdgId+variantId\tvariant_lemma\tформа");
            bw.newLine();
            for (String[] row : rows) {
                bw.write(String.join("\t", row));
                bw.newLine();
            }
        }
    }
}
