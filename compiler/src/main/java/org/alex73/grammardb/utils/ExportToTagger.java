package org.alex73.grammardb.utils;

import org.alex73.grammardb.GrammarDB2;
import org.alex73.grammardb.SetUtils;
import org.alex73.grammardb.StressUtils;
import org.alex73.grammardb.structures.Form;
import org.alex73.grammardb.structures.Paradigm;
import org.alex73.grammardb.structures.Variant;
import org.alex73.grammardb.tags.BelarusianTags;
import org.alex73.grammardb.tags.TagLetter;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Экспартуе звесткі для tagger.
 */
public class ExportToTagger {

    public static Map<String, Object> nodeToMap(TagLetter tl) {
        Map<String, Object> node = new LinkedHashMap<>();

        Map<String, Object> children = new LinkedHashMap<>();
        for (TagLetter.OneLetterInfo li : tl.letters) {
            Map<String, Object> childInfo = new LinkedHashMap<>();
            childInfo.put("group", li.groupName);
            childInfo.put("desc", li.description);
            childInfo.putAll(nodeToMap(li.nextLetters));
            if (li.letter != '+' && li.letter != 'X') {
                children.put(String.valueOf(li.letter), childInfo);
            }
        }
        node.put("children", children);
        return node;
    }

    public static void main(String[] args) throws Exception {
        BelarusianTags bt = new BelarusianTags();
        Map<String, Object> tree = nodeToMap(bt.getRoot());
        // Захаваць у tag_tree.json праз Jackson або Gson
        new com.fasterxml.jackson.databind.ObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValue(new File("tagger/tag_tree.json"), tree);


        GrammarDB2 db = GrammarDB2.initializeFromDir("data");
        try (BufferedWriter bw = Files.newBufferedWriter(Path.of("tagger/forms.tsv"))) {
            for (Paradigm p : db.getAllParadigms()) {
                for (Variant v : p.getVariant()) {
                    String variantLemma = StressUtils.unstress(v.getLemma());
                    for (Form f : v.getForm()) {
                        if (f.getValue().isEmpty()) {
                            continue; // прапускаем пустыя формы
                        }
                        String tag = SetUtils.tag(p, v, f);
                        if (tag.contains("X") || tag.contains("+")) {
                            continue; // прапускаем тэгі з 'X' і '+'
                        }
                        bw.write(StressUtils.unstress(f.getValue()) + "\t" + variantLemma + "\t" + tag);
                        bw.newLine();
                    }
                }
            }
        }
    }
}
