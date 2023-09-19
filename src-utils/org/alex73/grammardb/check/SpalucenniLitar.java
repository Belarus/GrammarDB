package org.alex73.grammardb.check;

import java.text.Collator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.alex73.grammardb.GrammarDB2;
import org.alex73.grammardb.structures.Form;
import org.alex73.grammardb.structures.Variant;

/**
 * Спраўжанне колькасці спалучэнняў літар.
 * 
 * Іншыя знакі: ' + - . , _
 * 
 * Не ўжываюцца вялікія літары: Ў Й Ы Ь
 * 
 * Словы не пачынаюцца на: ы ь ў ' + - , _
 * 
 * Словы не канчаюцца на: ' - , _
 * 
 * Дзіўнае:
 * 
 * Слова "й"
 * 
 * Кропкі у злучніках(C) і частках слоў(F) - TODO KORPUS-50 перанесці ў асобныя тыпы
 * 
 * Прагалы ў прыслоў'ях(R), часціцах(E), злучніках(C), частках слоў(F), займенніках(S), выклічніках(Y), прыназоўніках(I) - TODO KORPUS-50 перанесці ў асобныя тыпы
 * 
 * Коскі ў злучніках(C)
 * 
 * Неспалучальныя літары: ў пасля зычных, [ржшд]е рі ря рю рё, бь пь мь на канцы
 * 
 * Апостраф толькі перад: е ё ю я і
 * 
 * TODO [зычныя]й  выдаліць +, не улічваць з . _ ,
 */
public class SpalucenniLitar {
    static final Collator BE = Collator.getInstance(new Locale("be"));

    enum MODE {
        BEGIN, MIDDLE, END
    };

    static final String CHARS = "ёйцукенгшўзх'фывапролджэячсмітьбю";

    static MODE mode = MODE.END;

    static final Set<String> predefined = new HashSet<>();
    static final Map<String, Integer> info = new TreeMap<>();

    public static void main(String[] args) throws Exception {
        for (char c : CHARS.toCharArray()) {
            for (char d : CHARS.toCharArray()) {
                String v = c + "" + d;
                if (v.matches("[ржшд][еіяюё]") || v.matches("[йцкнгшўзх'фвпрлджчсмтьб]ў"))
                    continue;
                switch (mode) {
                case BEGIN:
                    if ("ыьў'+-, ".indexOf(c) >= 0)
                        continue;
                    break;
                case MIDDLE:
                    break;
                case END:
                    if (v.matches("[бпм]ь"))
                        continue;
                    break;
                }

                predefined.add(v);
            }
        }
        predefined.forEach(c -> info.put(c, 0));

        GrammarDB2 db = GrammarDB2.initializeFromDir("/data/gits/GrammarDB");
        db.getAllParadigms().parallelStream().forEach(p -> {
            for (Variant v : p.getVariant()) {
                for (Form f : v.getForm()) {
                    if (f.getValue().length() >= 2) {
                        switch (mode) {
                        case BEGIN:
                            inc(f.getValue().substring(0, 2));
                            break;
                        case MIDDLE:
                            // TODO прыбраць націскі
                            for (int i = 1; i < f.getValue().length(); i++) {
                                inc(f.getValue().substring(i - 1, i + 1));
                            }
                            break;
                        case END:
                            inc(f.getValue().substring(f.getValue().length() - 2));
                            break;
                        }
                    }
                }
            }
        });

        info.entrySet().stream().map(en -> new Pair(en.getKey(), en.getValue())).sorted().forEach(System.out::println);
    }

    static void process(String w) {
        for (char c : w.toCharArray()) {
            inc(Character.toString(c));
        }
    }

    static void inc(String v) {
        synchronized (info) {
            Integer count = info.get(v);
            count = count == null ? 1 : count.intValue() + 1;
            info.put(v, count);
        }
    }

    static class Pair implements Comparable<Pair> {
        final String v;
        final int count;

        Pair(String v, int count) {
            this.v = v;
            this.count = count;
        }

        @Override
        public int compareTo(Pair o) {
            int c = Integer.compare(o.count, count);
            if (c == 0) {
                c = BE.compare(v, o.v);
            }
            return c;
        }

        @Override
        public String toString() {
            boolean n = !predefined.contains(v);
            return v + (n ? "(new)" : "") + "=" + count;
        }
    }
}
