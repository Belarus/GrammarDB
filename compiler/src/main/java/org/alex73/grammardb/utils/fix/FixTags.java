package org.alex73.grammardb.utils.fix;

import org.alex73.grammardb.GrammarDB2;
import org.alex73.grammardb.GrammarDBSaver;
import org.alex73.grammardb.SetUtils;
import org.alex73.grammardb.structures.Form;
import org.alex73.grammardb.structures.Paradigm;
import org.alex73.grammardb.structures.Variant;
import org.alex73.grammardb.tags.BelarusianTags;

/**
 * Выпраўленне тэгаў каб прыбраць род для множнага ліку.
 */
public class FixTags {
    static GrammarDB2 db;
    static BelarusianTags bt = new BelarusianTags();

    public static void main(String[] args) throws Exception {
        db = GrammarDB2.initializeFromDir("data");

        Applier applierRodSklonLik = (p, v, f, tag) -> {
            if (f.getTag().length() != 3) {
                throw new RuntimeException("Expected tag length=3 in " + tag);
            }
            char rod = bt.getValueOfGroup(tag, "Род");
            char sklon = bt.getValueOfGroup(tag, "Склон");
            char lik = bt.getValueOfGroup(tag, "Лік");
            if (rod == '\0' || sklon == '\0' || lik == '\0') {
                throw new RuntimeException("Missing tag group in " + tag);
            }
            if (lik == 'P') {
                if (rod != 'P') {
                    throw new RuntimeException("Expected rod=P for lik=P in " + tag);
                }
                f.setTag("" + sklon + lik);
            } else {
                f.setTag("" + sklon + lik + rod);
            }
        };

        Applier applierCasRodLik = (p, v, f, tag) -> {
            if (f.getTag().length() != 3) {
                throw new RuntimeException("Expected tag length=3 in " + tag);
            }
            char cas = bt.getValueOfGroup(tag, "Час");
            char rod = bt.getValueOfGroup(tag, "Род");
            char lik = bt.getValueOfGroup(tag, "Лік");
            if (rod == '\0' || cas == '\0' || lik == '\0') {
                throw new RuntimeException("Missing tag group in " + tag);
            }
            if (lik == 'P') {
                if (rod != 'X') {
                    throw new RuntimeException("Expected rod=X for lik=P in " + tag);
                }
                f.setTag("" + cas + lik);
            } else {
                f.setTag("" + cas + lik + rod);
            }
        };

        fix(applierRodSklonLik, filterTag("Часціна мовы", 'N'), filterTag("Скланенне", '5'));

        fix(applierRodSklonLik, filterTag("Часціна мовы", 'A'),
                (p, v, f, tag) -> bt.getValueOfGroup(tag, "Тып") != '0',
                (p, v, f, tag) -> bt.getValueOfGroup(tag, "Прыметнік у функцыі прыслоўя") != 'R');

        fix(applierRodSklonLik, filterTag("Часціна мовы", 'P'),
                (p, v, f, tag) -> bt.getValueOfGroup(tag, "Кароткая форма") != 'R');

        fix(applierCasRodLik, filterTag("Часціна мовы", 'V'), filterTag("Час", 'P'), filterTag("Дзеепрыслоўе", '\0'));

        GrammarDBSaver.sortAndStore(db, "data2");
    }

    public static void fix(Applier applier, GrammarPredicate... filters) throws Exception {
        GrammarPredicate filter = filterMultiple(filters);
        for (Paradigm p : db.getAllParadigms()) {
            for (Variant v : p.getVariant()) {
                for (Form f : v.getForm()) {
                    String tag = SetUtils.tag(p, v, f);
                    if (filter.test(p, v, f, tag)) {
                        applier.apply(p, v, f, tag);
                    }
                }
            }
        }
    }

    public static GrammarPredicate filterMultiple(GrammarPredicate... filters) {
        return (p, v, f, tag) -> {
            for (GrammarPredicate filter : filters) {
                if (!filter.test(p, v, f, tag)) {
                    return false;
                }
            }
            return true;
        };
    }

    public static GrammarPredicate filterTag(String group, char value) {
        return (p, v, f, tag) -> bt.getValueOfGroup(tag, group) == value;
    }

    public interface GrammarPredicate {
        boolean test(Paradigm p, Variant v, Form f, String tag);
    }

    public interface Applier {
        void apply(Paradigm p, Variant v, Form f, String tag);
    }
}
