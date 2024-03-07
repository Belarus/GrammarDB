package org.alex73.grammardb;

import org.alex73.grammardb.structures.Form;
import org.alex73.grammardb.structures.Paradigm;
import org.alex73.grammardb.structures.Variant;

public class WordMorphology implements Comparable<WordMorphology> {
    public final Paradigm p;
    public final Variant v;
    public final Form f;

    public WordMorphology(Paradigm p, Variant v, Form f) {
        this.p = p;
        this.v = v;
        this.f = f;
    }

    public boolean isMorphologyDefined() {
        if (v.getPrystauki() == null) {
            return false;
        }
        String prefix = v.getPrystauki().replace("/", "").replace("|", "");
        return f.getValue().startsWith(prefix);
    }

    public String getFanetykaApplied() {
        String w = f.getValue();
        // дадаем пазначэнне прыставак і корняў
        if (v.getPrystauki() != null) {
            String prefix = v.getPrystauki().replace("/", "").replace("|", "");
            if (f.getValue().startsWith(prefix)) {
                w = v.getPrystauki() + f.getValue().substring(prefix.length());
            }
        }
        String zf = v.getZmienyFanietyki();
        if (zf != null) {
            int p = zf.indexOf(':');
            w = w.replace(zf.substring(0, p), zf.substring(p + 1));
        }
        return w;
    }

    @Override
    public int compareTo(WordMorphology o) {
        return f.getValue().compareTo(o.f.getValue());
    }

    @Override
    public int hashCode() {
        return f.getValue().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof WordMorphology) {
            WordMorphology o = (WordMorphology) obj;
            return p == o.p && v == o.v && f == o.f;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return f.getValue() + " #" + p.getPdgId() + v.getId();
    }
}
