package org.alex73.grammardb.utils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.alex73.grammardb.FormsReadyFilter;
import org.alex73.grammardb.GrammarDB2;
import org.alex73.grammardb.GrammarDBSaver;
import org.alex73.grammardb.SetUtils;
import org.alex73.grammardb.StressUtils;
import org.alex73.grammardb.structures.Form;
import org.alex73.grammardb.structures.FormOptions;
import org.alex73.grammardb.structures.Paradigm;
import org.alex73.grammardb.structures.Variant;
import org.alex73.grammardb.tags.BelarusianTags;

public class ExportUniMorph {
    static BelarusianTags tags = new BelarusianTags();

    public static void main(String[] args) throws Exception {
        GrammarDB2 db = GrammarDB2.initializeFromDir(Path.of("../data").toAbsolutePath().toString());

        int pCount = 0;
        int fCount = 0;
        Files.createDirectories(Path.of("unimorph"));
        BufferedWriter writer = new BufferedWriter(new FileWriter("unimorph/bel.txt"));
        BufferedWriter writerVoc = new BufferedWriter(new FileWriter("unimorph/bel_voc.txt"));
        Collections.sort(db.getAllParadigms(), GrammarDBSaver.COMPARATOR);

        for (Paradigm p : db.getAllParadigms()) {
            for (Variant v : p.getVariant()) {
                List<Form> forms = FormsReadyFilter.getAcceptedForms(FormsReadyFilter.MODE.SPELL, p, v);
                if (forms == null) {
                    continue;
                }
                writer.write("\n");
                writerVoc.write("\n");
                boolean hasForms = false;
                for (Form form : forms) {
                    String tag = SetUtils.tag(p, v, form);
                    String pos = getUniMorphPOS(tag);
                    if (pos == null) {
                        continue;
                    }
                    List<BiFunction<String, Form, String>> groups;
                    switch (pos) {
                    case "N":
                    case "PROPN":
                        groups = List.of(FEATURE_CASE, FEATURE_NUMBER, FEATURE_GENDER, FEATURE_ANIMACY);
                        break;
                    case "ADJ":
                        groups = List.of(FEATURE_CASE, FEATURE_NUMBER, FEATURE_GENDER, FEATURE_COMPARISON, FEATURE_ANIMACY_OPTIONS);
                        break;
                    case "PRO":
                        groups = List.of(FEATURE_CASE, FEATURE_NUMBER, FEATURE_GENDER, FEATURE_ANIMACY_OPTIONS, FEATURE_PERSON);
                        break;
                    case "NUM":
                        groups = List.of(FEATURE_CASE, FEATURE_NUMBER, FEATURE_GENDER, FEATURE_ANIMACY_OPTIONS);
                        break;
                    case "CONJ":
                    case "ADP":
                    case "PART":
                    case "INTJ":
                    case "":
                        groups = List.of();
                        break;
                    case "V":
                        groups = List.of(FEATURE_CASE, FEATURE_NUMBER, FEATURE_GENDER, FEATURE_TENSE, FEATURE_ASPECT, FEATURE_VOICE, FEATURE_PERSON);
                        break;
                    case "V.PTCP":
                        groups = List.of(FEATURE_CASE, FEATURE_NUMBER, FEATURE_GENDER, FEATURE_ANIMACY_OPTIONS);
                        groups = List.of(FEATURE_CASE, FEATURE_NUMBER, FEATURE_GENDER, FEATURE_TENSE, FEATURE_ASPECT, FEATURE_VOICE, FEATURE_COMPARISON,
                                FEATURE_ANIMACY, FEATURE_ANIMACY_OPTIONS, FEATURE_PERSON);
                        break;
                    case "V.CVB":
                        groups = List.of(FEATURE_ASPECT);
                        groups = List.of(FEATURE_TENSE, FEATURE_ASPECT);
                        break;
                    case "ADV":
                        groups = List.of(FEATURE_COMPARISON);
                        break;
                    case "DET":
                        groups = List.of(FEATURE_CASE, FEATURE_NUMBER, FEATURE_GENDER, FEATURE_COMPARISON, FEATURE_ANIMACY, FEATURE_ANIMACY_OPTIONS,
                                FEATURE_PERSON);
                        break;
                    default:
                        throw new RuntimeException("Wrong UniMorph POS: " + pos);
                    }
                    groups = List.of(FEATURE_CASE, FEATURE_NUMBER, FEATURE_GENDER, FEATURE_TENSE, FEATURE_ASPECT, FEATURE_VOICE, FEATURE_COMPARISON,
                            FEATURE_ANIMACY, FEATURE_ANIMACY_OPTIONS, FEATURE_PERSON);
                    String features = groups.stream().map(f -> f.apply(tag, form)).filter(s -> s != null).collect(Collectors.joining(";"))
                            .replace("ANIM;ANIM", "ANIM").replace("INAN;INAN", "INAN");
                    String line = v.getLemma() + "\t" + form.getValue() + "\t" + pos + (features.isEmpty() ? "" : (";" + features)) + "\n";
                    writer.write(StressUtils.unstress(line));
                    writerVoc.write(line);
                    fCount++;
                    hasForms = true;
                }
                if (hasForms) {
                    pCount++;
                }
            }
        }
        System.out.println("Парадыгм: " + pCount + ", форм: " + fCount);

        writer.close();
        writerVoc.close();
    }

    static BiFunction<String, Form, String> FEATURE_ANIMACY = (tag, form) -> {
        char pos = tags.getValueOfGroup(tag, "Адушаўлёнасць");
        switch (pos) {
        case 'A':
            return "ANIM";
        case 'I':
            return "INAN";
        case '\0':
            return null;
        default:
            throw new RuntimeException("Адушаўлёнасць: " + pos);
        }
    };
    static BiFunction<String, Form, String> FEATURE_ANIMACY_OPTIONS = (tag, form) -> {
        if (form.getOptions() == FormOptions.ANIM) {
            return "ANIM";
        } else if (form.getOptions() == FormOptions.INANIM) {
            return "INAN";
        }
        return null; // TODO калі няма - і тое, і тое
    };
    static BiFunction<String, Form, String> FEATURE_ASPECT = (tag, form) -> {
        char pos = tags.getValueOfGroup(tag, "Трыванне");
        switch (pos) {
        case 'P':
            return "PFV";
        case 'M':
            return "IPFV";
        case '\0':
            return null;
        default:
            throw new RuntimeException("Трыванне: " + pos);
        }
    };
    static BiFunction<String, Form, String> FEATURE_CASE = (tag, form) -> {
        char pos = tags.getValueOfGroup(tag, "Склон");
        switch (pos) {
        case 'N':
            return "NOM";
        case 'G':
            return "GEN";
        case 'D':
            return "DAT";
        case 'A':
            return "ACC";
        case 'I':
            return "INS";
        case 'L':
            return "ESS";
        case 'V':
            return "VOC";
        case '\0':
        case 'H':
            return null;
        default:
            throw new RuntimeException("Склон: " + pos);
        }
    };
    static BiFunction<String, Form, String> FEATURE_COMPARISON = (tag, form) -> {
        char pos = tags.getValueOfGroup(tag, "Ступень параўнання");
        switch (pos) {
        case 'C':
            return "CMPR";
        case 'S':
            return "SPRL";
        case 'P': // станоўчая - не пазначаецца
        case '\0':
            return null;
        default:
            throw new RuntimeException("Ступень параўнання: " + pos);
        }
    };
    static BiFunction<String, Form, String> FEATURE_GENDER = (tag, form) -> {
        char pos = tags.getValueOfGroup(tag, "Род");
        switch (pos) {
        case 'M':
            return "MASC";
        case 'F':
            return "FEM";
        case 'N':
            return "NEUT";
        case '\0':
        case '0':
        case 'P':
        case 'X':
            return null;
        default:
            throw new RuntimeException("Род: " + pos);
        }
    };
    static BiFunction<String, Form, String> FEATURE_NUMBER = (tag, form) -> {
        char pos = tags.getValueOfGroup(tag, "Лік");
        switch (pos) {
        case 'S':
            return "SG";
        case 'P':
            return "PL";
        case '\0':
            return null;
        default:
            throw new RuntimeException("Лік: " + pos);
        }
    };
    static BiFunction<String, Form, String> FEATURE_PERSON = (tag, form) -> {
        char pos = tags.getValueOfGroup(tag, "Асоба");
        switch (pos) {
        case '1':
            return "1";
        case '2':
            return "2";
        case '3':
            return "3";
        case '0':
        case '\0':
            return null;
        default:
            throw new RuntimeException("Асоба: " + pos);
        }
    };
    static BiFunction<String, Form, String> FEATURE_TENSE = (tag, form) -> {
        char pos = tags.getValueOfGroup(tag, "Час");
        switch (pos) {
        case 'R':
            return "PRS";
        case 'P':
            return "PST";
        case 'F':
            return "FUT";
        case '\0':
            return null;
        default:
            throw new RuntimeException("Час: " + pos);
        }
    };
    static BiFunction<String, Form, String> FEATURE_VOICE = (tag, form) -> {
        char pos = tags.getValueOfGroup(tag, "Стан");
        switch (pos) {
        case 'A':
            return "ACT";
        case 'P':
            return "PASS";
        case '\0':
            return null;
        default:
            throw new RuntimeException("Стан: " + pos);
        }
    };

    static String getUniMorphPOS(String tag) {
        char pos = tags.getValueOfGroup(tag, "Часціна мовы");
        switch (pos) {
        case 'N':
            char ul = tags.getValueOfGroup(tag, "Уласнасць");
            return ul == 'P' ? "PROPN" : "N";
        case 'A':
            char ty = tags.getValueOfGroup(tag, "Тып");
            return ty == 'P' ? "DET" : "ADJ";
        case 'M':
            char zn = tags.getValueOfGroup(tag, "Значэнне");
            return zn == 'O' ? "ADJ" : "NUM";
        case 'S':
            char ra = tags.getValueOfGroup(tag, "Разрад");
            return ra == 'S' ? "DET" : "PRO";
        case 'V':
            char dzp = tags.getValueOfGroup(tag, "Дзеепрыслоўе");
            return dzp == 'G' ? "V.CVB" : "V";
        case 'P':
            return "V.PTCP";
        case 'R':
            return "ADV";
        case 'C':
            return "CONJ";
        case 'I':
            return "ADP";
        case 'E':
            return "PART";
        case 'Y':
            return "INTJ";
        case 'W':
        case 'Z':
            return null; // няма ў UniMorph
        default:
            throw new RuntimeException("Часціна мовы: " + pos);
        }
    }
}
