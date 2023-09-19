package org.alex73.grammardb;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.alex73.grammardb.structures.Form;
import org.alex73.grammardb.structures.FormType;
import org.alex73.grammardb.structures.Paradigm;
import org.alex73.grammardb.structures.RegulationType;
import org.alex73.grammardb.structures.Variant;

/**
 * Фільтруе толькі тыя варыянты і формы, якія варта паказваць карыстальніку і
 * экспартаваць у праверку правапісу.
 */
public class FormsReadyFilter {
    public enum MODE {
        // spell checker - A2008 with official dictionaries
        SPELL,
        // for show - A2008 with any dictionaries
        SHOW
    };

    public static List<Form> getAcceptedForms(MODE mode, Paradigm p, Variant v) {
        if (v.getForm().isEmpty()) {
            return null;
        }
        String tag = SetUtils.tag(p, v);
        switch (mode) {
        case SPELL:
            if (tag.startsWith("K") || tag.startsWith("F") || v.getLemma().contains(" ")) {
                return null;
            }
            break;
        case SHOW:
            if (tag.startsWith("K")) {
                return null;
            }
            break;
        }
        Set<String> slv = SetUtils.getSlouniki(v.getSlouniki()).keySet();
        if (mode == MODE.SPELL) {
            slv.remove("piskunou2012");// правапіс - без піскунова, база - з піскуновым
        }
        boolean use = !slv.isEmpty();
        if (!SetUtils.getSlouniki(v.getForm().get(0).getSlouniki()).isEmpty()) {
            use = true;
        }
        if (tag.startsWith("NP")) {
            use = true;
        }
        RegulationType reg = v.getRegulation() != null ? v.getRegulation() : p.getRegulation();
        boolean forceUse = false;
        if (reg != null) {
            switch (reg) {
            case ADD:
                forceUse = true;
                break;
            case MISTAKE:
            case FANTASY:
            case UNDESIRABLE:
            case LIMITED:
            case OBSCENISM:
            case INVECTIVE:
                use = false;
                break;
            case RARE:
            case RARE_BRANCH:
                switch (mode) {
                case SPELL:
                    use = false;
                    break;
                case SHOW:
                    forceUse = true;
                    break;
                }
                break;
            }
        }
        if (!use && !forceUse) {
            return null;
        }
        Stream<Form> result;
        if (forceUse) {
            result = v.getForm().stream();
        } else if (SetUtils.hasPravapis(v, "A2008")) {
            result = v.getForm().stream();
        } else if (v.getPravapis() == null) {
            result = v.getForm().stream().filter(f -> SetUtils.hasPravapis(f, "A2008"));
        } else {
            return null;
        }
        List<Form> r = result.filter(standardForms).filter(f -> !f.getValue().isEmpty()).collect(Collectors.toList());
        return r.isEmpty() ? null : r;
    }

    private static Predicate<Form> standardForms = (f) -> (f.getType() == null || f.getType() == FormType.NUMERAL);
}
