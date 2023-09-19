package org.alex73.grammardb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.alex73.grammardb.structures.Fan;
import org.alex73.grammardb.structures.Paradigm;

/**
 * Хутка шукае парадыгмы, ў якіх ёсць форма, падобная на патрэбнае слова. Задача
 * не ў тым, каб знайсці дакладны спіс парадыгмаў, а каб абмежаваць далейшы
 * пошук некалькімі дзясяткамі парадыгмаў, а не перабіраць усе 260 тысяч.
 * 
 * Для гэтага ствараецца хэш-табліца, якая запаўняецца на старце, і потым пошук
 * ідзе толькі па гэтай хэш-табліцы.
 */
public class GrammarFinder {
    private static final int HASHTABLE_SIZE = 256 * 1024;
    private static final Paradigm[] EMPTY = new Paradigm[0];
    private final Paradigm[][] table;
    private final Map<String, String> morph = new HashMap<>();
    private final Map<String, String> fan = new HashMap<>();
    protected final char[] LETTERS_HASH = new char[0x2020];

    public GrammarFinder(GrammarDB2 gr) {
        for (char c = 0; c < LETTERS_HASH.length; c++) {
            if (Character.isLetterOrDigit(c)) {
                LETTERS_HASH[c] = Character.toLowerCase(c);
            }
        }
        // дадаткова канвертуем мяккія у цвёрдыя
        char[] map = new char[] { 'ґ', 'г', 'ў', 'у', 'й', 'і', 'ё', 'о', 'е', 'э', 'я', 'а', 'ю', 'у', 'ь', '\0' };
        for (int i = 0; i < map.length; i += 2) {
            LETTERS_HASH[map[i]] = map[i + 1];
            LETTERS_HASH[Character.toUpperCase(map[i])] = map[i + 1];
        }

        long be = System.currentTimeMillis();
        final List<List<Paradigm>> prepare = new ArrayList<>(HASHTABLE_SIZE);
        for (int i = 0; i < HASHTABLE_SIZE; i++) {
            prepare.add(new ArrayList<>());
        }
        gr.getAllParadigms().parallelStream().forEach(p -> {
            p.getVariant().forEach(v -> {
                putToPrepare(v.getLemma(), prepare, p);
                v.getForm().forEach(f -> {
                    if (f.getValue() != null && !f.getValue().isEmpty()) {
                        putToPrepare(f.getValue(), prepare, p);
                    }
                });
                v.getMorph().forEach(m -> {
                    putToMorph(m);
                });
                v.getFan().forEach(f -> {
                    putToFan(f);
                });
            });
        });
        table = prepareToFinal(prepare);
        long af = System.currentTimeMillis();
        System.out.println("GrammarFinder prepare time: " + (af - be) + "ms");
    }

    private void putToPrepare(String w, List<List<Paradigm>> prepare, Paradigm p) {
        int hash = hash(w);
        int indexByHash = Math.abs(hash) % HASHTABLE_SIZE;
        List<Paradigm> list = prepare.get(indexByHash);
        synchronized (list) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i) == p) {
                    return;
                }
            }
            list.add(p);
        }
    }

    private void putToMorph(String m) {
        String key = m.replace("-", "").replace('ґ', 'г').toLowerCase();
        synchronized (morph) {
            String prev = morph.put(key, m);
            if (prev != null && !prev.equals(m)) {
                throw new RuntimeException("Different morph for " + key + ": " + m + " / " + prev);
            }
        }
    }

    private void putToFan(Fan f) {
        String key = f.getS().replace("+", "").toLowerCase();
        synchronized (fan) {
            String prev = fan.put(key, f.getValue());
            if (prev != null && !prev.equals(f.getValue())) {
                System.err.println("Different fan for " + key + ": " + f.getValue() + " / " + prev);
            }
        }
    }

    private Paradigm[][] prepareToFinal(List<List<Paradigm>> prepare) {
        Paradigm[][] result = new Paradigm[prepare.size()][];
        int maxLen = 0;
        for (int i = 0; i < result.length; i++) {
            List<Paradigm> list = prepare.get(i);
            if (!list.isEmpty()) {
                result[i] = list.toArray(new Paradigm[list.size()]);
                maxLen = Math.max(maxLen, result[i].length);
            }
        }
        System.out.println("GrammarFinder max table tail: " + maxLen);
        return result;
    }

    /**
     * Find paradigms by lemma or form (lower case).
     */
    public Paradigm[] getParadigms(String word) {
        int hash = hash(word);
        int indexByHash = Math.abs(hash) % HASHTABLE_SIZE;
        Paradigm[] result = table[indexByHash];
        return result != null ? result : EMPTY;
    }

    public Stream<Paradigm[]> getSimilarGroups() {
        return Arrays.stream(table).filter(r -> r != null);
    }

    public String getMorph(String word) {
        word = StressUtils.unstress(word).replace('ґ', 'г').toLowerCase();
        return morph.get(word);
    }

    public String getFan(String word) {
        word = StressUtils.unstress(word).toLowerCase();
        return fan.get(word);
    }

    private int hash(String word) {
        if (word == null) {
            return 0;
        }
        int result = 0;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            c = c < LETTERS_HASH.length ? LETTERS_HASH[c] : 0;
            if (c > 0) {
                result = 31 * result + c;
            }
        }
        return result;
    }
}
