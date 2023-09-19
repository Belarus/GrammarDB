package org.alex73.grammardb;

import java.util.ArrayList;
import java.util.List;


/**
 * Some utilities methods for stress processing.
 * 
 * Stress syll index started from 0.
 */
public class StressUtils {

    public static char STRESS_CHAR = GrammarDB2.pravilny_nacisk;
    public static String STRESS_CHARS = "+\u0301\u00b4";

    public static final String HALOSNYJA = "ёуеыаоэяіюЁУЕЫАОЭЯІЮ";
    public static final String USUALLY_STRESSED = "ёоЁО";

    public static String unstress(String stressedWord) {
        if (!hasStress(stressedWord)) {
            return stressedWord;
        }
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < stressedWord.length(); i++) {
            char c = stressedWord.charAt(i);
            if (STRESS_CHARS.indexOf(c) < 0) {
                s.append(c);
            }
        }
        return s.toString();
    }

    public static boolean hasStress(String word) {
        for (int i = 0; i < word.length(); i++) {
            if (STRESS_CHARS.indexOf(word.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find stress syll by ё, о using possible value. If possible < 0 - find first.
     */
    public static int getUsuallyStressedSyll(String word, int possible) {
        int r = 0;
        if (possible >= 0) {
            for (int i = 0; i < word.length(); i++) {
                char c = word.charAt(i);
                if (possible == r && USUALLY_STRESSED.indexOf(c) >= 0) {
                    return r;
                }
                if (HALOSNYJA.indexOf(c) >= 0) {
                    r++;
                }
            }
        }
        int count = 0;
        int result = -1;
        r = 0;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (USUALLY_STRESSED.indexOf(c) >= 0) {
                result = r;
                count++;
            }
            if (HALOSNYJA.indexOf(c) >= 0) {
                r++;
            }
            if (c == '-') {
                return -1;
            }
        }
        return count == 1 ? result : -1;
    }

    public static String setUsuallyStress(String word) {
        if (hasStress(word)) {
            return word;
        }
        if (syllCount(word) == 1) {
            return setStressFromStart(word, 0);
        }
        int u = getUsuallyStressedSyll(word, -1);
        if (u >= 0) {
            return setStressFromStart(word, u);
        }
        return word;
    }

    public static boolean isAssignable(String destination, String withStress) {
        if (destination.equals(unstress(destination))) {
            return destination.equals(unstress(withStress));
        } else {
            return destination.equals(withStress);
        }
    }

    public static int getStressFromStart(String word) {
        int r = 0;
        for (int i = 0; i < word.length() - 1; i++) {
            char c = word.charAt(i);
            char c1 = word.charAt(i + 1);
            if (STRESS_CHARS.indexOf(c1) >= 0) {
                return r;
            }
            boolean halosnaja = HALOSNYJA.indexOf(c) >= 0;
            if (halosnaja) {
                r++;
            }
        }
        return -1;
    }

    public static List<Integer> getAllStressesFromStart(String word) {
        List<Integer> result = new ArrayList<>();
        int r = 0;
        for (int i = 0; i < word.length() - 1; i++) {
            char c = word.charAt(i);
            char c1 = word.charAt(i + 1);
            if (STRESS_CHARS.indexOf(c1) >= 0) {
                result.add(r);
            }
            boolean halosnaja = HALOSNYJA.indexOf(c) >= 0;
            if (halosnaja) {
                r++;
            }
        }
        return result;
    }

    public static int getStressFromEnd(String word) {
        int r = 0;
        for (int i = word.length() - 1; i >= 0; i--) {
            char c = word.charAt(i);
            if (STRESS_CHARS.indexOf(c) >= 0) {
                return r;
            }
            boolean halosnaja = HALOSNYJA.indexOf(c) >= 0;
            if (halosnaja) {
                r++;
            }
        }
        return -1;
    }

    public static List<Integer> getAllStressesFromEnd(String word) {
        List<Integer> result = new ArrayList<>();
        int r = 0;
        for (int i = word.length() - 1; i >= 0; i--) {
            char c = word.charAt(i);
            if (STRESS_CHARS.indexOf(c) >= 0) {
                result.add(r);
            }
            boolean halosnaja = HALOSNYJA.indexOf(c) >= 0;
            if (halosnaja) {
                r++;
            }
        }
        return result;
    }

    public static String setStressFromStart(String word, int pos) {
        if (pos < 0) {
            return word;
        }
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            boolean halosnaja = HALOSNYJA.indexOf(c) >= 0;
            if (halosnaja) {
                if (pos == 0) {
                    return word.substring(0, i + 1) + STRESS_CHAR + word.substring(i + 1);
                } else {
                    pos--;
                }
            }
        }
        return word;
    }

    public static String setStressFromEnd(String word, int pos) {
        if (pos < 0) {
            return word;
        }
        for (int i = word.length() - 1; i >= 0; i--) {
            char c = word.charAt(i);
            boolean halosnaja = HALOSNYJA.indexOf(c) >= 0;
            if (halosnaja) {
                if (pos == 0) {
                    return word.substring(0, i + 1) + STRESS_CHAR + word.substring(i + 1);
                } else {
                    pos--;
                }
            }
        }
        return word;
    }

    public static void checkStress(String word) throws Exception {
        for (String w : word.split("[\\-, \\.]")) {
            int pos = -1;
            int mainStresses = 0;
            while ((pos = w.indexOf(STRESS_CHAR, pos + 1)) >= 0) {
                if (HALOSNYJA.indexOf(w.charAt(pos - 1)) < 0) {
                    throw new Exception("Націск не на галосную");
                }
                mainStresses++;
            }
            if (mainStresses > 1) {
                throw new Exception("Зашмат асноўных націскаў у " + word);
            }
        }
    }

    public static int syllCount(String word) {
        int r = 0;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            boolean halosnaja = HALOSNYJA.indexOf(c) >= 0;
            if (halosnaja) {
                r++;
            }
        }
        return r;
    }

    public static char syllHal(String word, int pos) {
        int r = 0;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            boolean halosnaja = HALOSNYJA.indexOf(c) >= 0;
            if (halosnaja) {
                if (pos == r) {
                    return c;
                }
                r++;
            }
        }
        return 0;
    }

    public static String setSyllHal(String word, int pos, char cr) {
        int r = 0;
        StringBuilder s = new StringBuilder(word);
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            boolean halosnaja = HALOSNYJA.indexOf(c) >= 0;
            if (halosnaja) {
                if (pos == r) {
                    s.setCharAt(i, cr);
                    return s.toString();
                }
                r++;
            }
        }
        throw new RuntimeException("No syll #" + pos + " in the " + word);
    }

    public static String combineAccute(String word) {
        return word.replace(STRESS_CHAR, '\u0301');
    }
}
