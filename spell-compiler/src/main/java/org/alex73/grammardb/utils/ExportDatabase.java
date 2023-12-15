package org.alex73.grammardb.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

import org.alex73.grammardb.FormsReadyFilter;
import org.alex73.grammardb.GrammarDB2;
import org.alex73.grammardb.GrammarDBSaver;
import org.alex73.grammardb.structures.Form;
import org.alex73.grammardb.structures.Paradigm;
import org.alex73.grammardb.structures.Variant;

/**
 * Экспартуе звесткі для праверкі правапісу.
 */
public class ExportDatabase {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("ExportDatabase <input_data_dir> <output_dir> ");
            System.exit(1);
        }
        Path inDir = Paths.get(args[0]);
        Path outDir = Paths.get(args[1]);
        deleteDirectory(outDir);
        Files.createDirectories(outDir.resolve("spellchecker/"));

        GrammarDB2 db = GrammarDB2.initializeFromDir(inDir.toAbsolutePath().toString());

        System.out.println("Запісваем кэш...");
        db.makeCache(outDir.resolve("cache/").toAbsolutePath().toString());

        System.out.println("Фільтраванне базы - што не паказваем...");
        db.getAllParadigms().parallelStream().forEach(p -> {
            for (Variant v : p.getVariant()) {
                if (v.getForm().isEmpty()) {
                    continue;
                }
                List<Form> fs = FormsReadyFilter.getAcceptedForms(FormsReadyFilter.MODE.SHOW, p, v);
                if (fs != null && !fs.isEmpty()) {
                    v.getForm().clear();
                }
            }
        });
        removeEmpty(db.getAllParadigms());
        GrammarDBSaver.sortAndStore(db, outDir.resolve("noshow/").toAbsolutePath().toString());

        System.out.println("Фільтраванне базы для паказу...");
        db = GrammarDB2.initializeFromDir(inDir.toAbsolutePath().toString());
        db.getAllParadigms().parallelStream().forEach(p -> {
            for (Variant v : p.getVariant()) {
                List<Form> fs = FormsReadyFilter.getAcceptedForms(FormsReadyFilter.MODE.SHOW, p, v);
                v.getForm().clear();
                if (fs == null) {
                    continue;
                }
                v.getForm().addAll(fs);
            }
        });
        removeEmpty(db.getAllParadigms());
        GrammarDBSaver.sortAndStore(db, outDir.resolve("export/").toAbsolutePath().toString());
    }

    static void deleteDirectory(Path d) throws IOException {
        if (Files.exists(d)) {
            Files.walk(d).sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
        }
    }

    static void removeEmpty(List<Paradigm> paradigms) {
        for (int i = 0; i < paradigms.size(); i++) {
            Paradigm p = paradigms.get(i);
            for (int j = 0; j < p.getVariant().size(); j++) {
                if (p.getVariant().get(j).getForm().isEmpty()) {
                    p.getVariant().remove(j);
                    j--;
                }
            }
            if (p.getVariant().isEmpty()) {
                paradigms.remove(i);
                i--;
            }
        }
    }
}
