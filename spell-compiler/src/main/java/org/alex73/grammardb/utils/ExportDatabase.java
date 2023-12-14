package org.alex73.grammardb.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.alex73.grammardb.FormsReadyFilter;
import org.alex73.grammardb.GrammarDB2;
import org.alex73.grammardb.GrammarDBSaver;
import org.alex73.grammardb.SetUtils;
import org.alex73.grammardb.StressUtils;
import org.alex73.grammardb.structures.Form;
import org.alex73.grammardb.structures.Paradigm;
import org.alex73.grammardb.structures.Variant;

/**
 * Экспартуе звесткі для праверкі правапісу.
 */
public class ExportDatabase {
    static final Collator BE = Collator.getInstance(new Locale("be"));
    static AtomicInteger c = new AtomicInteger();

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

        List<String> list2008 = new ArrayList<>();
        List<String> temp1 = new ArrayList<>();
        List<String> temp2 = new ArrayList<>();
        List<String> temp3 = new ArrayList<>();

        System.out.println("Збор слоў для праверкі правапісу...");
        db.getAllParadigms().forEach(p -> {
            List<String> result = new ArrayList<>();
            for (Variant v : p.getVariant()) {
                List<Form> fs1 = FormsReadyFilter.getAcceptedForms(FormsReadyFilter.MODE.SPELL, p, v);
                if (fs1 == null) {
                    continue;
                }
                if (!fs1.isEmpty()) {
                    c.incrementAndGet();
                    fs1.forEach(f -> result.add(f.getValue()));
                    result.add("");
                }
                String tag = SetUtils.tag(p, v);
                for (Form f : fs1) {
                    temp1.add(StressUtils.unstress(f.getValue()) + "\t" + StressUtils.unstress(v.getLemma()) + "\t" + tag.charAt(0));
                    temp2.add(StressUtils.unstress(f.getValue()).replace(GrammarDB2.pravilny_apostraf, '\''));
                    temp3.add(f.getValue().replace('+', '\u0301').replace(GrammarDB2.pravilny_apostraf, '\''));
                }
            }
            list2008.addAll(result);
        });

        System.out.println("Апрацоўка спісаў...");
        duplicateU(list2008, false);
        duplicateU(temp1, true);
        duplicateU(temp2, true);
        duplicateU(temp2, true);
        System.out.println("Сартаванне спісаў...");
        //List<String> list2008flc = temp1.stream().sorted(BE).distinct().collect(Collectors.toList());
        List<String> list2008uniq = temp2.stream().sorted(BE).distinct().collect(Collectors.toList());
        List<String> list2008uniqStress = temp3.stream().sorted(BE).distinct().collect(Collectors.toList());

        // Files.write(Paths.get("slovy-2008-z_naciskami_i_razdialicielami.txt"),
        // list2008);
        // Files.write(Paths.get("slovy-2008-forma+lemma+cascinamovy.txt"),
        // list2008flc);
        Files.write(outDir.resolve("spellchecker/slovy-2008.txt"), list2008uniq);
        Files.write(outDir.resolve("spellchecker/slovy-2008-stress.txt"), list2008uniqStress);

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

    static void duplicateU(List<String> words, boolean addToEnd) {
        for (int i = 0; i < words.size(); i++) {
            if (words.get(i).startsWith("у")) {
                String neww = "ў" + words.get(i).substring(1);
                if (addToEnd) {
                    words.add(neww);
                } else {
                    words.add(i + 1, neww);
                }
            }
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
