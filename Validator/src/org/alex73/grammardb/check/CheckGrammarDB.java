/**************************************************************************
 Korpus - Corpus Linguistics Software.

 Copyright (C) 2014 Aleś Bułojčyk (alex73mail@gmail.com)
               Home page: https://sourceforge.net/projects/korpus/

 This file is part of Korpus.

 Korpus is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Korpus is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 **************************************************************************/

package org.alex73.grammardb.check;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.alex73.corpus.paradigm.Form;
import org.alex73.corpus.paradigm.FormType;
import org.alex73.corpus.paradigm.Paradigm;
import org.alex73.corpus.paradigm.Variant;
import org.alex73.corpus.paradigm.VariantType;
import org.alex73.korpus.base.GrammarDB2;
import org.alex73.korpus.base.GrammarDBSaver;
import org.alex73.korpus.belarusian.BelarusianTags;
import org.alex73.korpus.belarusian.BelarusianWordNormalizer;
import org.alex73.korpus.utils.SetUtils;
import org.alex73.korpus.utils.StressUtils;

/**
 * Правярае граматычную базу, і запісвае вынікі ў каталёг vyniki/.
 *
 * Выключэнні правілаў:
 * exSyllCount - не правяраць на колькасць складаў
 * exStress - не правяраць націск
 * exLemma1Form: Лема ў варыянце несупадае з першай стандартнай формай
 * exFormsCount: Нестандартная колькасць формаў
 * exFormsCountGS: Нестандартная колькасць формаў GS
 * exFormsCountIS: Нестандартная колькасць формаў IS
 * exFormsCountLS: Нестандартная колькасць формаў LS
 * exFormsCountGPAP: Нестандартная колькасць формаў GP і AP
 * exFormsCountGP: Нестандартная колькасць формаў GP
 * exFormsCountIP: Нестандартная колькасць формаў IP
 * exFormsEquals: Непадобныя формы
 * exFormLSEnd: LS канчаецца на зычны
 */
public class CheckGrammarDB {

     Map<String, List<Paradigm>> paradigmsByErrors = new TreeMap<>();
     GrammarDB2 gr;

     public void check(String dir) throws Exception {
         paradigmsByErrors.clear();

            System.out.println("Чытаем файлы...");
            gr = GrammarDB2.initializeFromDir(dir);

            // set ids
            int maxPdgId = 0;
            for (Paradigm p : gr.getAllParadigms()) {
                if (maxPdgId < p.getPdgId()) {
                    maxPdgId = p.getPdgId();
                }
            }
            for (Paradigm p : gr.getAllParadigms()) {
                if (p.getPdgId() <= 0) {
                    p.setPdgId(++maxPdgId);
                }
            }

            System.out.println("Правяраем...");
            // выдаляем старыя паведамленьні пра памылкі
            removeErrors(gr);
                for (Paradigm p: new ArrayList<>(gr.getAllParadigms())) {
                    try {
                        if ("бы´ць".equals(p.getLemma())) {
                            // надта асаблівае слова
                            continue;
                        }
                        checkUniqueParadigm(p);
                        checkUniqueVariants(p);
                        checkTags(p);
                        checkLemmas(p);
                        checkBeg(p);
                        for(Variant v:p.getVariant()) {
                            check3(p, v);
                            check9(p, v);
                            checkUniqueForms(p,v);
                            checkSlounikPravapis(p,v);
                            check2(p,v);
                            check10(p,v);
                            check11(p,v);
                            String vTag=SetUtils.tag(p, v);
                            if (vTag.startsWith("V")) {
                                checkV1(p,v);
                                checkV4(p,v);
                                checkV5(p, v);
                                checkV67(p, v);
                                // пакуль выключым checkV8(p);
                            }
                            if (vTag.startsWith("N")) {
                                /* 
                                5) пашукаць Капылову абалонку навучання
                                6) аднаразова: ID замест 0
                                */
                                checkNFormsCount(p,v);
                                checkNEqualForms(p,v);
                                checkNasabovyja(p,v);
                            }
                        }
                    } catch (KnownError ex) {
                        to_pamylki(p, ex);
                    }
                }

            System.out.println("Захоўваем вынікі");

            GrammarDBSaver.sortAndStore(gr, ".");
            for (Map.Entry<String, List<Paradigm>> en : paradigmsByErrors.entrySet()) {
                GrammarDBSaver.sortList(en.getValue());
                GrammarDBSaver.store(en.getValue(), new File(en.getKey()));
            }

//        System.out.println("Max pdgId=" + maxPdgId);
//        errors = new ArrayList(new TreeSet<String>(errors));
//        if (!errors.isEmpty()) {
//            Collections.sort(errors);
//            File out = new File("errors.txt");
//            FileUtils.writeLines(out, errors, "\r\n");
//            label.setText(errors.size() + " памылак у " + out.getAbsolutePath());
//        } else {
//            label.setText("Памылак няма");
//        }
    }

    boolean needSkip(String rule, Paradigm p, Variant v) {
        return SetUtils.hasTag(v.getRules(), rule);
    }

    public void removeErrors(GrammarDB2 db) {
        db.getAllParadigms().forEach(p -> p.getE().clear());
    }

    void to_pamylki(Paradigm p, KnownError ex) {
        String fn = "_pamylki_" + ex.fileprefix + ".xml";
        List<Paradigm> pe = paradigmsByErrors.get(fn);
        if (pe == null) {
            pe = new ArrayList<Paradigm>();
            paradigmsByErrors.put(fn, pe);
        }
        if (!p.getE().contains(ex.text)) {
            p.getE().add(ex.text);
        }
        if (!pe.contains(p)) {
            pe.add(p);
        }
        gr.getAllParadigms().remove(p);
    }

    public void validateXMLs(String dir) throws Exception {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = factory.newSchema(
                new StreamSource(CheckGrammarDB.class.getResourceAsStream("/xsd/Paradigm.xsd")));
        Validator validator = schema.newValidator();

        File[] files = GrammarDB2.getFilesForLoad(new File(dir));
        if (files == null || files.length == 0) {
            throw new Exception("Няма файлаў граматычнай базы ў " + new File(dir).getAbsolutePath());
        }
        for (File f : files) {
            if (f.getName().equals(GrammarDB2.THEMES_FILE)) {
                continue;
            }
            System.out.println("Правяраем структуру " + f.getName() + "...");
            validator.validate(new StreamSource(f));
        }
    }

    /*
     * колькасць форм STANDARD - звычайна 1
     */
    void checkNFormsCount(Paradigm p, Variant v) {
        if (needSkip("exFormsCount", p, v)) {
            return;
        }
        if (v.getForm().isEmpty()) {
            throw new KnownError("7_niama_formau", "Няма формаў");
        }
        if (v.getType()==VariantType.NONSTANDARD) {
            return;
        }
        Map<String, Integer> formsCount = new TreeMap<>();
        v.getForm().stream().filter(f -> f.getType() == null).forEach(f -> {
            Integer c = formsCount.get(f.getTag());
            c = c == null ? 1 : c.intValue() + 1;
            formsCount.put(f.getTag(), c);
        });
        char adu = BelarusianTags.getInstance().getValueOfGroup(SetUtils.tag(p, v), "Адушаўлёнасць");
        char rod = BelarusianTags.getInstance().getValueOfGroup(SetUtils.tag(p, v), "Род");
        char subst = BelarusianTags.getInstance().getValueOfGroup(SetUtils.tag(p, v), "Субстантываванасць");
        char sklan = BelarusianTags.getInstance().getValueOfGroup(SetUtils.tag(p, v), "Скланенне");
        boolean hasStandardS = v.getForm().stream().anyMatch(f -> f.getType() == null
                && BelarusianTags.getInstance().getValueOfGroup(SetUtils.tag(p, v, f), "Лік") == 'S');
        boolean hasStandardP = v.getForm().stream().anyMatch(f -> f.getType() == null
                && BelarusianTags.getInstance().getValueOfGroup(SetUtils.tag(p, v, f), "Лік") == 'P');
        boolean isMnoznalikavy = BelarusianTags.getInstance().getValueOfGroup(SetUtils.tag(p, v),
                "Множналікавыя") == 'P' || subst == 'U';
        if (isMnoznalikavy && hasStandardS) {
            throw new KnownError("7_kolkasc_formau_0MN", "Множналікавыя - не мусіць быць адзіночнага ліку");
        }
        if (subst == 'S' && sklan == '5') {
            // калі S5 - => S:субстантываваны, => 5:ад’ектыўнае: трохлітарныя тэгі - па 1 - памылкі у асобны файл
            checkNFormsCountSubst(p,v);
            return;
        }
        if (subst == 'U' && sklan == '5') {
            // калі U5 - => субстантываваныя множналікавыя, => 5:ад’ектыўнае: трохлітарныя тэгі - па 1 - памылкі у
            // асобны файл
            checkNFormsCountUSubst(p,v);
            return;
        }
        // TODO уласныя - пакуль не правяраць колькасць
        if (BelarusianTags.getInstance().getValueOfGroup(SetUtils.tag(p, v), "Уласнасць") == 'P') {
            return;
        }

        if (sklan == '0') {
            // нескланяльныя
            for (Form f : v.getForm()) {
                if (!f.getValue().equals(v.getLemma())) {
                    throw new KnownError("7_kolkasc_formau_0L", "Форма '" + f.getTag() + "' несупадае з лемай");
                }
            }
            if (hasStandardS) {
                // для нескланяльных - мусіць быць па 1 форме кожнага склону
                for (String tag : new String[] { "NS", "GS", "DS", "AS", "IS", "LS" }) {
                    int c = getFormsCount(v, tag);
                    if (c != 1) {
                        throw new KnownError("7_kolkasc_formau_0", "Колькасць '" + tag + "' : " + c);
                    }
                }
            }
            if (hasStandardP) {
                // для нескланяльных - мусіць быць па 1 форме кожнага склону
                for (String tag : new String[] { "NP", "GP", "DP", "AP", "IP", "LP" }) {
                    int c = getFormsCount(v, tag);
                    if (c != 1) {
                        throw new KnownError("7_kolkasc_formau_0", "Колькасць '" + tag + "' : " + c);
                    }
                }
            }
        } else {
            if (hasStandardS) {
                checkNFormsCountZvycaynyjaS(p, v, rod, sklan);
            }
            if (hasStandardP) {
                checkNFormsCountZvycaynyjaP(p, v, rod, sklan, adu);
            }
        }
    }

    void checkNFormsCountSubst(Paradigm p, Variant v) {
        if (v.getForm().stream().filter(f -> f.getType() == null && f.getTag().charAt(0) == 'M').count() > 0) {
            checkFormsCount(1, v, "MNS");
            checkFormsCount(1, v, "MGS");
            checkFormsCount(1, v, "MDS");
            checkFormsCount(1, v, "MAS");
            checkFormsCount(1, v, "MIS");
            checkFormsCount(1, v, "MLS");
            checkFormsCount(0, v, "MVS");
        }
        if (v.getForm().stream().filter(f -> f.getType() == null && f.getTag().charAt(0) == 'F').count() > 0) {
            checkFormsCount(1, v, "FNS");
            checkFormsCount(1, v, "FGS");
            checkFormsCount(1, v, "FDS");
            checkFormsCount(1, v, "FAS");
            checkFormsCount(2, v, "FIS");
            checkFormsCount(1, v, "FLS");
            checkFormsCount(0, v, "FVS");
        }
        if (v.getForm().stream().filter(f -> f.getType() == null && f.getTag().charAt(0) == 'N').count() > 0) {
            checkFormsCount(1, v, "NNS");
            checkFormsCount(1, v, "NGS");
            checkFormsCount(1, v, "NDS");
            checkFormsCount(1, v, "NAS");
            checkFormsCount(1, v, "NIS");
            checkFormsCount(1, v, "NLS");
            checkFormsCount(0, v, "NVS");
        }
        if (v.getForm().stream().filter(f -> f.getType() == null && f.getTag().charAt(0) == 'P').count() > 0) {
            checkFormsCount(1, v, "PNP");
            checkFormsCount(1, v, "PGP");
            checkFormsCount(1, v, "PDP");
            checkFormsCount(1, v, "PAP");
            checkFormsCount(1, v, "PIP");
            checkFormsCount(1, v, "PLP");
            checkFormsCount(0, v, "PVP");
        }
    }
    void checkNFormsCountUSubst(Paradigm p, Variant v) {
        checkFormsCount(1, v, "PNP");
        checkFormsCount(1, v, "PGP");
        checkFormsCount(1, v, "PDP");
        checkFormsCount(1, v, "PAP");
        checkFormsCount(1, v, "PIP");
        checkFormsCount(1, v, "PLP");
        checkFormsCount(0, v, "PVP");
    }
    void checkNFormsCountZvycaynyjaS(Paradigm p, Variant v, char rod, char sklaniennie) {
        checkFormsCount(1,v,"NS");
        if (!needSkip("exFormsCountGS", p, v)) {
            checkFormsCount(1,v,"GS");
        }
        checkFormsCount(1,v,"DS");
        checkFormsCount(1,v,"AS");
        if (!needSkip("exFormsCountIS", p, v)) {
            int c = getFormsCount(v, "IS");

            String unstressedLemma = StressUtils.unstress(v.getLemma());
            if ((rod == 'F' && sklaniennie == '2') || (rod == 'M' && sklaniennie == '2'
                    && (unstressedLemma.endsWith("а") || unstressedLemma.endsWith("я")))) {
                // IS: калі жаночы род адзіночны лік, калі канчатак -ай, -яй, мусіць быць такая самая
                // стандартная
                // форма -аю, -яю
                Form[] fs = v.getForm().stream().filter(f -> f.getType() == null && f.getTag().equals("IS"))
                        .sorted((a, b) -> GrammarDBSaver.BEL.compare(a.getValue(), b.getValue())).toArray(Form[]::new);
                if (rod == 'M' && fs.length == 1
                        && (fs[0].getValue().endsWith("ам") || fs[0].getValue().endsWith("ем"))) {
                    // ok
                } else {
                    if (c != 2) {
                        throw new KnownError("7_kolkasc_formau_IS", "Колькасць 'IS': " + c);
                    }
                    if (fs[0].getValue().endsWith("ай") && !fs[1].getValue().endsWith("аю")) {
                        throw new KnownError("7_kolkasc_formau_IS", "Формы ў 'IS' не -ай/-аю");
                    }
                    if (fs[0].getValue().endsWith("яй") && !fs[1].getValue().endsWith("яю")) {
                        throw new KnownError("7_kolkasc_formau_IS", "Формы ў 'IS' не -ай/-аю");
                    }
                }
            } else {
                if (c != 1) {
                    throw new KnownError("7_kolkasc_formau_ISother", "Колькасць 'IS': " + c);
                }
            }
        }
        if (!needSkip("exFormsCountLS", p, v)) {
            checkFormsCount(1,v,"LS");
        }
        {
            int c = getFormsCount(v, "VS");
            if (c > 1) {
                throw new KnownError("7_kolkasc_formau_VS", "Колькасць 'VS' : " + c);
            }
        }

    }

    void checkNFormsCountZvycaynyjaP(Paradigm p, Variant v, char rod, char sklaniennie, char adu) {
        checkFormsCount(1,v,"NP");
        if (!needSkip("exFormsCountGP", p, v) && !needSkip("exFormsCountGPAP", p, v)) {
            int c = getFormsCount(v, "GP");
            if (rod == 'F' && sklaniennie == '3') {
                // для F3 - мусіць быць 2 стандартныя формы, якія канчаюцца на ()-ей, ()-яў і лема ()-ь
                if (c != 2) {
                    throw new KnownError("7_kolkasc_formau_GP", "Колькасць 'GP' : " + c);
                }
                String[] fs = v.getForm().stream().filter(f -> f.getType() == null && f.getTag().equals("GP"))
                        .map(f -> StressUtils.unstress(f.getValue())).sorted((a, b) -> GrammarDBSaver.BEL.compare(a, b))
                        .toArray(String[]::new);
                String lemmaNoStress = StressUtils.unstress(v.getLemma());
                char lemmaLast = lemmaNoStress.charAt(lemmaNoStress.length() - 1);
                String base;
                if (lemmaLast == 'ь') {
                    base = lemmaNoStress.substring(0, lemmaNoStress.length() - 1);
                } else {
                    if (lemmaLast != 'ф' && lemmaLast != 'ч' && lemmaLast != 'м' && lemmaLast != 'б'
                            && lemmaLast != 'п') {
                        // не мусіць быць -ь калі -ф -ч -м -б -п: ве+рф
                        throw new KnownError("7_kolkasc_formau_GP_F3", "Праверка колькасці 'GP' : лема не на -ь");
                    }
                    base = lemmaNoStress;
                }
                if (!fs[0].equals(base + "ей") || !fs[1].equals(base + "яў")) {
                    throw new KnownError("7_kolkasc_formau_GP_F3_eja",
                            "Праверка колькасці 'GP' : формы не на -ей, -яў");
                }
            } else if (rod == 'N' && sklaniennie == '1') {
                if (c != douhikarotkiCount(v.getLemma())) {
                    throw new KnownError("7_kolkasc_formau_GP_N1", "Колькасць 'GP' : " + c);
                }
            } else if (c != 1) {
                throw new KnownError("7_kolkasc_formau_GP", "Колькасць 'GP' : " + c);
            }
        }
        if (!needSkip("exFormsCountDP", p, v)) {
            checkFormsCount(1, v, "DP");
        }
        if (!needSkip("exFormsCountAP", p, v) && !needSkip("exFormsCountGPAP", p, v)) {
            int c = getFormsCount(v, "AP");
            if (adu == 'A' && rod == 'F' && sklaniennie == '3') {
                // для F3 - мусіць быць 2 стандартныя формы, якія канчаюцца на ()-ей, ()-яў і лема ()-ь
                if (c != 2) {
                    throw new KnownError("7_kolkasc_formau_AP", "Колькасць 'AP' : " + c);
                }
            } else if (adu == 'A' && rod == 'N' && sklaniennie == '1') {
                if (c != douhikarotkiCount(v.getLemma())) {
                    throw new KnownError("7_kolkasc_formau_AP_N1", "Колькасць 'AP' : " + c);
                }
            } else if (c != 1) {
                throw new KnownError("7_kolkasc_formau_AP", "Колькасць 'AP' : " + c);
            }
        }
        if (!needSkip("exFormsCountIP", p, v)) {
            checkFormsCount(1, v, "IP");
        }
        if (!needSkip("exFormsCountLP", p, v)) {
            checkFormsCount(1, v, "LP");
        }
        {
            int c = getFormsCount(v, "VP");
            if (c > 1) {
                throw new KnownError("7_kolkasc_formau_VP", "Колькасць 'VP' : " + c);
            }
        }
    }

    static final String ZYCNYJA = "йцкнгшўзхфвпрлджчсмтбь";
    static final String HALOSNYJA = "ёуеыаоэяію";
    static final String HALOSNYJA_MIAKK = "ёеяію";
    int douhikarotkiCount(String lemma) throws KnownError {
        boolean vydalicKarotki = false;
        boolean vydalicDouhi = false;
        boolean asnounyKarotki = false;
        String base,end0;
        String le=lemma.replace(""+BelarusianWordNormalizer.pravilny_nacisk, "");
        if (HALOSNYJA.indexOf(le.charAt(le.length()-1))>=0) {
            base=le.substring(0,le.length()-1);
            end0=le.substring(le.length()-1);
        }else {
            throw new KnownError("7_kolkasc_formau_GP_N1h","Не на галосную для N1");
        }

        // бярэм апошнюю і перадапошнюю літару асновы
        char b0 = 0, b1 = 0, e0 = 0;
        for (int i = base.length() - 1; i >= 0; i--) {
            char c = Character.toLowerCase(base.charAt(i));
            if (c == BelarusianWordNormalizer.pravilny_nacisk) {
                continue;
            }
            if (b0 == 0) {
                b0 = c;
            } else if (b1 == 0) {
                b1 = c;
                break;
            }
        }
        // і першую літару канчатка
        try {
            e0 = Character.toLowerCase(end0.charAt(0));
        } catch (StringIndexOutOfBoundsException ex) {
            e0 = 0;
        }
        int syllCount0 = StressUtils.syllCount(base + end0);

        // 167.1 поўны канчатак
        // 167.2 нулявы канчатак, але часам і з поўным
        // 167.3 поўныя і нулявыя

        // 167.1а аснова якіх заканчваецца збегам зычных
        // 167.3а аснова заканчваецца збегам зд, сц, шч
        if (b0 == BelarusianWordNormalizer.pravilny_apostraf || "ёеяію".indexOf(e0) >= 0) {
            // аснова канчаецца на zz ці ьz, апостраф
            // ці канчатак назоўнага склону пачынаецца j
            vydalicKarotki = true;
        }
        if (ZYCNYJA.indexOf(b0) >= 0 && ZYCNYJA.indexOf(b1) >= 0) {
            // але калі аснова канчаецца на -зд -сц -шч - пусты канчатак будзе
            if (((b1 == 'з' && b0 == 'д') || (b1 == 'с' && b0 == 'ц') || (b1 == 'ш' && b0 == 'ч'))
                    && HALOSNYJA_MIAKK.indexOf(e0) < 0) {
                vydalicKarotki = false;
            } else {
                vydalicKarotki = true;
            }
        }

        // 167.1б некаторыя ..., націск перамяшчаецца на аснову - не ў гэтых тыпах
        // Піскунова

        // 167.1в двухскладовыя з асновай на л і націскам на аснове, а таксама
        // веча мора пуза права жыта
        if (syllCount0 == 2) {
            if (b0 == 'л') {
                vydalicKarotki = true;
            }
        }

        // 167.1г трохскладовыя з асновай на в і воблака
        // 167.2а трох- і больш складовыя з асновай на 1 зычны (акрамя в)
        if (syllCount0 >= 3) {
            if ((b0 == 'в' && HALOSNYJA.indexOf(b1) >= 0)) {
                vydalicKarotki = true;
            } else if (ZYCNYJA.indexOf(b0) >= 0 && HALOSNYJA.indexOf(b1) >= 0) {
                vydalicKarotki = false;
                asnounyKarotki = true;
            }
        }

        // 167.2б некаторыя двухскладовыя з асновай на 1 зычны

        // 167.3б са збегам зычных, калі паяўляецца галосны - не ў гэтых тыпах Піскунова


        return asnounyKarotki || vydalicDouhi || vydalicKarotki ? 1 : 2;
    }

    int getFormsCount(Variant v, String tag) {
        return (int) v.getForm().stream().filter(f -> f.getType() == null && f.getTag().equals(tag)).count();
    }

    void checkFormsCount(int expectedCount, Variant v, String tag) {
        int c = getFormsCount(v, tag);
        if (c != expectedCount) {
            throw new KnownError("7_kolkasc_formau_" + tag, "Колькасць '" + tag + "' : " + c);
        }
    }

    /*
     * AS/AP - вінавальны склон у залежнасці ад адушаўлёнасці мусіць супадаць : 
          адушаўлёныя - мужчынскі род - з родным(AS=GS,AP=GP), жаночы род - з родным ў множным ліку(AP=GP)
          неадушаўлёныя - мужчынскі род - з назоўным(AS=NS,AP=NP), жаночы род - з назоўным ў множным ліку(AP=NP)
          усё гэта для STANDARD,NONSTANDARD,POTENTIAL
     */
    void checkNEqualForms(Paradigm p, Variant v) {
        if (needSkip("exFormsCount", p, v) || needSkip("exFormsEquals", p, v)) {
            return;
        }
        if (v.getLemma().contains("-")) {
            return; // для двайных - не правяраем
        }
        if (v.getForm().isEmpty()) {
            return;
        }
        String tag = SetUtils.tag(p, v);
            char adu = BelarusianTags.getInstance().getValueOfGroup(tag, "Адушаўлёнасць");
            char rod;
            if (BelarusianTags.getInstance().getValueOfGroup(tag, "Субстантываванасць") !=0) {
                rod = BelarusianTags.getInstance().getValueOfGroup(tag, "Субстантываванасць");
            } else if (BelarusianTags.getInstance().getValueOfGroup(tag, "Множналікавыя") == 'P') {
                rod = 'P';
            } else {
                rod = BelarusianTags.getInstance().getValueOfGroup(tag, "Род");
            }
            char sklan = BelarusianTags.getInstance().getValueOfGroup(tag, "Скланенне");
            String type ="" + adu + rod + sklan; 
            switch (type) {
            case "AM1":
                checkNAEquals(type, p, v, "AS", "GS");
                checkNAEquals(type, p, v, "AP", "GP");
                break;
            case "AM2":
                checkNAEquals(type, p, v, "AP", "GP");
                break;
            case "AM4":
                checkNAEquals(type, p, v, "AP", "GP");
            case "AM6":
                checkNAEquals(type, p, v, "AP", "GP");
                break;
            case "AF1":
            case "AF2":
                checkNAEquals(type, p, v, "AP", "GP");
                break;
            case "AF3":
                checkNAEquals(type, p, v, "AS", "NS");
                checkNAEquals(type, p, v, "AP", "GP");
                break;
            case "AF4":
            case "AN4":
                checkNAEquals(type, p, v, "AP", "GP");
                break;
            case "AF6":
                checkNAEquals(type, p, v, "AP", "GP");
                break;
            case "AN1":
                checkNAEquals(type, p, v, "AP", "GP");
                break;
            case "IM1":
                checkNAEquals(type, p, v, "AS", "NS");
                checkNAEquals(type, p, v, "AP", "NP");
                break;
            case "IM2":
                checkNAEquals(type, p, v, "AP", "NP");
                break;
            case "IM6":
                checkNAEquals(type, p, v, "AS", "NS");
                checkNAEquals(type, p, v, "AP", "NP");
                break;
            case "IF1":
            case "IF2":
                checkNAEquals(type, p, v, "AP", "NP");
                break;
            case "IF3":
                checkNAEquals(type, p, v, "AP", "NP");
                break;
            case "AM0":
            case "AF0":
            case "AN0":
            case "AP0":
            case "IM0":
            case "IF0":
            case "IN0":
            case "IP0":
                checkAllEquals(p, v);
                break;
            case "IN1":
                checkNAEquals(type, p, v, "AS", "NS");
                checkNAEquals(type, p, v, "AP", "NP");
                break;
            case "IN4":
                checkNAEquals(type, p, v, "AS", "NS");
                checkNAEquals(type, p, v, "AP", "NP");
                break;
            case "AP7":
                checkNAEquals(type, p, v, "AP", "GP");
                break;
            case "IP7":
                checkNAEquals(type, p, v, "AP", "NP");
                break;
            case "AS5":
                checkNAEquals(type, p, v, "MGS", "MAS");
                checkNAEquals(type, p, v, "MIS", "MLS");
                checkNAEquals(type, p, v, "PGP", "PAP");
                break;
            case "IS5":
                checkNAEquals(type, p, v, "MNS", "MAS");
                checkNAEquals(type, p, v, "PNP", "PAP");
                break;
            case "AU5":
                checkNAEquals(type, p, v, "PGP", "PAP");
                break;
            case "IU5":
                checkNAEquals(type, p, v, "PNP", "PAP");
                break;
            default:
                throw new KnownError("7_ASAP_unk_"+adu + rod + sklan, "Камбінацыя: " + adu + rod + sklan);
            }
            if (adu == 'A' && rod == 'M' && sklan != '2') {
            } else if (adu == 'A' && rod == 'M' && sklan == '2') {
            } else if (adu == 'A' && rod == 'F') {
            } else if (adu == 'I' && rod == 'M' && sklan != '2') {
            } else if (adu == 'I' && rod == 'M' && sklan == '2') {
            } else if (adu == 'I' && rod == 'F') {
            }
    }

    /**
     * калі ёсць стандартныя формы, правяраем толькі іх
     * калі толькі нестандартныя ці патэнцыяныя: правяраем усе як ёсць
     * калі ёсць патэцыйныя і нестандартныя - правяраем толькі патэнцыяныя
     */
    void checkNAEquals(String type, Paradigm p, Variant v, String formTag1, String formTag2) {
        List<Form> fs1 = v.getForm().stream().filter(f -> f.getTag().equals(formTag1)).sorted(naeq)
                .collect(Collectors.toList());
        List<Form> fs2 = v.getForm().stream().filter(f -> f.getTag().equals(formTag2)).sorted(naeq)
                .collect(Collectors.toList());

        if (fs1.stream().anyMatch(f -> f.getType() == null) || fs2.stream().anyMatch(f -> f.getType() == null)) {
            // выдаляем нестандартныя
            fs1 = fs1.stream().filter(f -> f.getType() == null).collect(Collectors.toList());
            fs2 = fs2.stream().filter(f -> f.getType() == null).collect(Collectors.toList());
        } else if (fs1.stream().allMatch(f -> f.getType() == FormType.POTENTIAL)
                && fs2.stream().allMatch(f -> f.getType() == FormType.POTENTIAL)) {
        } else if (fs1.stream().allMatch(f -> f.getType() == FormType.NONSTANDARD)
                && fs2.stream().allMatch(f -> f.getType() == FormType.NONSTANDARD)) {
        } else {
            fs1 = fs1.stream().filter(f -> f.getType() == FormType.POTENTIAL).collect(Collectors.toList());
            fs2 = fs2.stream().filter(f -> f.getType() == FormType.POTENTIAL).collect(Collectors.toList());
        }

        if (fs1.size() != fs2.size()) {
            throw new KnownError("7_ASAP_"+type, "Несупадаюць " + formTag1 + "/" + formTag2);
        }
        for (int i = 0; i < fs1.size(); i++) {
            Form f1 = fs1.get(i);
            Form f2 = fs2.get(i);
            if (!f1.getValue().equals(f2.getValue()) || f1.getType() != f2.getType()) {
                throw new KnownError("7_ASAP_"+type, "Несупадаюць " + formTag1 + "/" + formTag2);
            }
        }
    }

    void checkAllEquals(Paradigm p, Variant v) {
        for (Form f:v.getForm()) {
            if (f.getValue().isEmpty()) {
                continue; // empty forms
            }
            if (!f.getValue().equals(v.getLemma())) {
                throw new KnownError("7_ASAP", "Несупадаюць " + f.getTag() + " з лемай");
            }
        }
    }

    static Comparator<Form> naeq = new Comparator<Form>() {
        @Override
        public int compare(Form o1, Form o2) {
            int c = o1.getValue().compareTo(o2.getValue());
            if (c == 0) {
                String t1 = o1.getType() != null ? o1.getType().name() : "";
                String t2 = o2.getType() != null ? o2.getType().name() : "";
                c = t1.compareTo(t2);
            }
            return c;
        }
    };

    static final Pattern RE_LS=Pattern.compile("(.+)([ёуеыаоэяію])");
    /*
     * LS: асабовыя: аснова на -ж, -ш, -ч, -дж, -ц, -р, -г, -к, -х: канчатак -у 
           неасабовыя: аснова на -ж, -ш, -ч, -дж, -ц, -р: канчатак -ы
     */
    void checkNasabovyja(Paradigm p, Variant va) {
        if (needSkip("exFormLSEnd", p, va)) {
            return;
        }
        String vTag = SetUtils.tag(p, va);
        if (vTag.startsWith("NP")) {
            return; // асабовыя не правяраем
        }
        if (BelarusianTags.getInstance().getValueOfGroup(vTag, "Скланенне") == '0') {
            return; // нескланяльныя не правяраем
        }
        char asabovasc = BelarusianTags.getInstance().getValueOfGroup(vTag, "Асабовасць");
        for (Form f : va.getForm()) {
            if (!f.getTag().equals("LS")) {
                continue;
            }
            Matcher m = RE_LS.matcher(StressUtils.unstress(f.getValue()));
            if (!m.matches()) {
                throw new KnownError("7_kancatki_asabovych", "LS не заканчваецца на галосную");
            }
            String asnova = m.group(1);
            switch (asabovasc) {
            case 'P': // асабовы
                if (asnova.endsWith("ж") && asnova.endsWith("ш") && asnova.endsWith("ч") && asnova.endsWith("дж")
                        && asnova.endsWith("ц") && asnova.endsWith("р") && asnova.endsWith("г") && asnova.endsWith("к")
                        && asnova.endsWith("х")) {
                    if (!m.group(2).equals("у")) {
                        throw new KnownError("7_kancatki_asabovych", "Асабовы LS канчаецца не на -у");
                    }
                }
                break;
            case 'I': // неасабовы
                if (asnova.endsWith("ж") && asnova.endsWith("ш") && asnova.endsWith("ч") && asnova.endsWith("дж")
                        && asnova.endsWith("ц") && asnova.endsWith("р")) {
                    if (!m.group(2).equals("ы")) {
                        throw new KnownError("7_kancatki_asabovych", "Асабовы LS канчаецца не на -ы");
                    }
                }
                break;
            default:
                throw new KnownError("7_kancatki_asabovych", "Невядомы тэг '" + asabovasc + "'");
            }
        }
    }

    Map<Integer, Paradigm> uniqPdgId = new HashMap<>();
    Map<String, Paradigm> uniqTagLemma = new HashMap<>();

    void checkUniqueParadigm(Paradigm p) {
        Paradigm prevId = uniqPdgId.put(p.getPdgId(), p);
        if (prevId != null) {
            to_pamylki(prevId, new KnownError("1_pautor_pdgid", "Паўтараецца pdgId"));
            throw new KnownError("1_pautor_pdgid", "Паўтараецца pdgId");
        }
        String tl = p.getTag() + '/' + p.getLemma() + "/" + p.getMeaning();
        Paradigm prevLemma = uniqTagLemma.put(tl, p);
        if (prevLemma != null) { // TODO націскі
            to_pamylki(prevLemma, new KnownError("1_pautor_taglemma", "Паўтараецца tag+lemma+meaning"));
            throw new KnownError("1_pautor_taglemma", "Паўтараецца tag+lemma+meaning");
        }
    }

    void checkUniqueVariants(Paradigm p) {
        Set<Character> variants = new TreeSet<>();
        for (Variant v : p.getVariant()) {
            if (v.getId() == null || v.getId().length() != 1) {
                throw new KnownError("1_variantid", "Няправільны variantId: " + v.getId());
            }
            if (!variants.add(v.getId().charAt(0))) {
                throw new KnownError("1_variantid", "Паўтараецца variantId у pdg#: " + p.getPdgId());
            }
        }
    }

    void checkLemmas(Paradigm p) {
        if (p.getVariant().isEmpty()) {
            throw new KnownError("5_lemmy", "Няма варыянтаў");
        }
        if (!p.getLemma().equals(p.getVariant().get(0).getLemma())) {
            throw new KnownError("5_lemmy", "Лема ў парадыгме несупадае з першым варыянтам");
        }
        if (p.getTag() != null && p.getTag().isEmpty()) {
            throw new KnownError("5_lemmy", "Пусты тэг");
        }
        for (Variant v : p.getVariant()) {
            if (needSkip("exLemma1Form", p, v) || v.getForm().isEmpty()) {
                continue;
            }
            if (!v.getLemma().equals(v.getForm().get(0).getValue())) {
                throw new KnownError("5_lemmy", "Лема ў варыянце несупадае з першай формай");
            }
        }
    }

    void checkBeg(Paradigm p) {
//        int pos=p.getLemma().indexOf(StressUtils.STRESS_CHAR)-3;
//        if (pos<0) {
//            return;
//        }
//        String prefix=p.getLemma().substring(0,pos);
//        for(Variant v:p.getVariant()) {
//            for(Form f:v.getForm()) {
//                if (!f.getValue().isEmpty() && !f.getValue().startsWith(prefix)) {
//                    throw new KnownError("5_start", "Пачатак формы несупадае з пачаткам лемы: "+f.getValue());
//                }
//            }
//        }
    }
    /**
     * Правяраем унікальнасьць форм.
     */
    void checkUniqueForms(Paradigm p, Variant v) {
            Set<String> u = new TreeSet<>();
            for (Form f : v.getForm()) {
                if (!u.add(f.getTag() + '/' + f.getType() + '/' + f.getValue())) {
                    throw new KnownError("1_pautor_formau",
                            "Паўтараецца форма tag/type/word=" + f.getTag() + '/' + f.getType() + '/' + f.getValue());
                }
            }
    }

    static final Set<String> KNOWN_SLOUNIKI = new HashSet<>(Arrays.asList("sbm2012", "prym2009", "prym2013", "dzsl2007", "dzsl2013",
            "nazounik2008", "nazounik2013", "tsbm1984", "tsblm1996", "tsbm2016", "krapivabr2012", "krapivarb2012",
            "biryla1987", "bulykasis1999", "paronimy1994", "epitety1998", "klyskassbs1993", "paciupatrb",
            "sankorbp1991", "lastouskirb1924", "bulykarb2001", "niekrasevicbajkourb1928", "hsbm1982", "stblks1997",
            "liepiesauef2004", "subaprnz1993", "kaurussal2013", "bntem1922", "bntlm1923", "bntnc1923",
            "bntlp1923", "bnthmk1924", "bnthl1927", "bntbt1928", "sudnikvaisk1997", "cyhunacny1926",
            "matenc2001", "matfizrb1995", "ekanterm1998", "ekantermrb1993", "sielliasnterm1998",
            "bijalterm1998", "farmakrb1995", "fizijalrb1993", "miedtermrbbr2001", "linhtermrb1988",
            "zvieras1924", "polbiel2004", "amparpolisiem2004", "samabslov1994", "stankievic1989",
            "bulykabr2001", "bajkouniekrasevicbr1925", "piskunou2012"));
    static final Set<String> KNOWN_PRAVAPIS = new HashSet<>(Arrays.asList("A1933", "A1957", "A2008", "T1929", "K2005"));

    void checkSlounikPravapis(Paradigm p, Variant v) {
        Set<String> slv = SetUtils.getSlouniki(v.getSlouniki()).keySet();
        slv.removeAll(KNOWN_SLOUNIKI);
        if (!slv.isEmpty()) {
            throw new KnownError("2_nieviadomyja_slouniki", "Невядомы слоўнік ў " + v.getSlouniki());
        }
        if (hasUnknown(KNOWN_PRAVAPIS, v.getPravapis())) {
            throw new KnownError("2_nieviadomyja_pravapisy", "Невядомы правапіс ў " + v.getPravapis());
        }
        for (Form f : v.getForm()) {
            Set<String> slf = SetUtils.getSlouniki(f.getSlouniki()).keySet();
            slf.removeAll(KNOWN_SLOUNIKI);
            if (!slf.isEmpty()) {
                throw new KnownError("2_nieviadomyja_slouniki", "Невядомы слоўнік ў " + f.getSlouniki());
            }
            if (hasUnknown(KNOWN_PRAVAPIS, f.getPravapis())) {
                throw new KnownError("2_nieviadomyja_pravapisy", "Невядомы правапіс ў " + f.getPravapis());
            }
        }
    }

    boolean hasUnknown(Set<String> known, String list) {
        if (list == null || list.isEmpty()) {
            return false;
        }
        for (String w : list.split(",")) {
            if (!known.contains(w.trim())) {
                return true;
            }
        }
        return false;
    }

    void check2(Paradigm p, Variant v) {
//        char cascinaMovy = p.getTag().charAt(0);
//        if (cascinaMovy == 'C' || cascinaMovy == 'Y' || cascinaMovy == 'I' || cascinaMovy == 'E'
//                || cascinaMovy == 'K' || cascinaMovy == 'S' || cascinaMovy == 'R') {
//            return;
//        }
        String tag = SetUtils.tag(p, v);
        String l = BelarusianTags.getInstance().getValueOfGroup(tag, "Часціна мовы") == 'K' ? lettersK : letters;
        if (isWordValid(p.getLemma(), l) != null) {
            throw new KnownError("2_niapravilnyja_symbali",
                    "Няправільныя сімвалы ў леме: " + isWordValid(p.getLemma(), l));
        }
            for (Form f : v.getForm()) {
                if (isWordValid(f.getValue(), l) != null) {
                    throw new KnownError("2_niapravilnyja_symbali",
                            "Няправільныя сімвалы ў форме " + f.getValue() + ": " + isWordValid(f.getValue(), l));
                }
            }
    }

    Set<Integer> SKIP_KCAST = new TreeSet<>();//Arrays.asList(1193270, 1133782, 1182759, 1182760, 1143892));

    void check3(Paradigm p, Variant v) {
        char cascinaMovy = BelarusianTags.getInstance().getValueOfGroup(SetUtils.tag(p, v), "Часціна мовы");

        // cascinaMovy != 'R' &&
        if (!SKIP_KCAST.contains(p.getPdgId())) {
                String[] lemma = v.getLemma().split("\\-");
                for (Form f : v.getForm()) {
                    if (f.getValue() == null || f.getValue().length() == 0) {
                        continue;
                    }
                    String[] forma = f.getValue().split("\\-");
                    if (lemma.length != forma.length) {
                        throw new KnownError("3_castki", "Несупадае колькасьць частак у форме і леме");
                    }
                }
        }

        if (!needSkip("exStress", p, v)) {
            try {
                StressUtils.checkStress(v.getLemma());
            } catch (Exception ex) {
                throw new KnownError("3_niapravilny_nacisk", "Няправільны націск у леме" + ": " + ex.getMessage());
            }
            for (Form f : v.getForm()) {
                try {
                    StressUtils.checkStress(f.getValue());
                } catch (Exception ex) {
                    throw new KnownError("3_niapravilny_nacisk",
                            "Няправільны націск у форме " + f.getValue() + ": " + ex.getMessage());
                }
            }
        }

            if (cascinaMovy != 'E' && cascinaMovy != 'I' && cascinaMovy != 'C' && cascinaMovy != 'K'
                    && cascinaMovy != 'Y' && cascinaMovy != 'F') {
                if (!needSkip("exSyllCount", p, v)) {
                    if (!isAllUpper(v.getLemma()) && StressUtils.syllCount(v.getLemma()) < 1) {
                        throw new KnownError("3_niama_halosnych", "Няма галосных у леме");
                    }
                    for (Form f : v.getForm()) {
                        if (!f.getValue().isEmpty() && !isAllUpper(f.getValue())
                                && StressUtils.syllCount(f.getValue()) < 1) {
                            throw new KnownError("3_niama_halosnych", "Няма галосных у форме " + f.getValue());
                        }
                    }
                }
            }
    }


    void checkTags(Paradigm p) {
        for (Variant v : p.getVariant()) {
            String tag = SetUtils.tag(p, v);
            if (!BelarusianTags.getInstance().isValidParadigmTag(tag, null)) {
                throw new KnownError("1_tag", "Няправільны тэг варыянта: " + tag);
            }
            for(Form f:v.getForm()) {
                tag = SetUtils.tag(p, v,f);
                if (!BelarusianTags.getInstance().isValidFormTag(tag, null)) {
                    throw new KnownError("1_tag", "Няправільны тэг формы: " + tag);
                }
            }
        }
    }

    static final String letters = "ёйцукенгшўзхфывапролджэячсмітьбюЁЙЦУКЕНГШЎЗХФЫВАПРОЛДЖЭЯЧСМІТЬБЮ-" + BelarusianWordNormalizer.pravilny_nacisk
            + BelarusianWordNormalizer.pravilny_apostraf;
    static final String lettersK = letters+"()";
    static final String letters_valid_yet = " .,";
    static final String galosnyja = "ёуеыаоэяіюЁУЕЫАОЭЯІЮ";
    static final String upper = "ЁЙЦУКЕНГШЎЗХФЫВАПРОЛДЖЭЯЧСМІТЬБЮ";

    public String isWordValid(String word, String letters) {
        char prev = ' ';
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (letters_valid_yet.indexOf(c) < 0 && letters.indexOf(c) < 0) {
                return word.substring(0, i)+"{"+c+"}"+word.substring(i+1);
            }
            if (i==0 &&(c=='ў' || c=='Ў')) {
                return "{"+c+"}"+word.substring(i+1);
            }
            if (c == 'у' && galosnyja.indexOf(prev) >= 0) {
                // у можа ісьці пасьля галоснай: шоу, анчоус
                //return word.substring(0, i)+"{"+c+"}"+word.substring(i+1);
            }
            if (c == 'ў' && galosnyja.indexOf(prev) < 0) {
                return word.substring(0, i)+"{"+c+"}"+word.substring(i+1);
            }
            if (c != StressUtils.STRESS_CHAR && c!='-' && c!=' ') {
                prev = c;
            }
        }

        return null;
    }
    
    public boolean isAllUpper(String word) {
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (upper.indexOf(c) < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Правяраем супадзеньне леммы і 0 формы.
     */
    void checkV1(Paradigm p, Variant v) {
            Form f = getForm(v, "0");
            if (f == null) {
                return;
            }
            if (!v.getLemma().equals(f.getValue())) {
                throw new KnownError("1_formy", "Лема не супадае з 0 формай");
            }
    }

    /**
     * Правяраем зворотнасьць
     */
    void checkV4(Paradigm p, Variant v) {
        String vTag = SetUtils.tag(p, v);
        if (vTag.charAt(1) == BelarusianWordNormalizer.pravilny_nacisk) {
            return;
        }
        char zv = BelarusianTags.getInstance().getValueOfGroup(vTag, "Зваротнасць");
        char zvp;
        String lemma = StressUtils.unstress(p.getLemma());
        if (lemma.endsWith("ся") || lemma.endsWith("цца")) {
            zvp = 'R';
        } else if (lemma.endsWith("ць") || lemma.endsWith("чы") || lemma.endsWith("ці")) {
            zvp = 'N';
        } else {
            throw new KnownError("4_zvarotnasc", "Незразумелая зваротнасьць па канчатку лемы");
        }
        if (zv != 'X' && zv != zvp) {
            throw new KnownError("4_zvarotnasc", "Зваротнасьць няправільна пазначана");
        }
    }

    /**
     * Правяраем спражэньне
     */
    void checkV5(Paradigm p, Variant v) {
        String vTag = SetUtils.tag(p, v);
        if (vTag.charAt(1) == BelarusianWordNormalizer.pravilny_nacisk) {
            return;
        }
        if (p.getLemma().equals("бы\u0301ць") || p.getLemma().equals("е\u0301сці")) {
            return;
        }
        
        String tag;
        switch (BelarusianTags.getInstance().getValueOfGroup(vTag, "Трыванне")) {
        case 'P':
            tag = "F3P";
            break;
        case 'M':
            tag = "R3P";
            break;
        default:
            throw new KnownError("5_sprazenni", "Невядомае трыванне");
        }

        if (v.getForm().isEmpty()) {
            return;
        }

        if (!v.getForm().isEmpty()) {
            // толькі для разгорнутых
            Form r3p = getForm(v, tag);
            if (r3p == null && !needSkip("exFormsCount", p, v)) {
                throw new KnownError("5_sprazenni", "Няма " + tag);
            }
            if (r3p != null) {
                char sp = BelarusianTags.getInstance().getValueOfGroup(vTag, "Спражэнне");
                char spp;
                String w = StressUtils.unstress(r3p.getValue());
                if (!w.isEmpty()) {
                    if (w.endsWith("уць") || w.endsWith("юць") || w.endsWith("уцца") || w.endsWith("юцца")) {
                        spp = '1';
                    } else if (w.endsWith("аць") || w.endsWith("яць") || w.endsWith("ацца") || w.endsWith("яцца")) {
                        spp = '2';
                    } else {
                        throw new KnownError("5_sprazenni", "Незразумелае спражэньне па канчатку " + r3p.getValue());
                    }
                    if (sp != '3' && sp != spp) {
                        throw new KnownError("5_sprazenni", "Спражэньне няправільна пазначана");
                    }
                }
            }
        }
    }

    /**
     * 6. V?M??: павінны быць формы RG: -учы, -ючы, -учыся, -ючыся
     * 
     * 7. V?P??: PG толькі канчаткі: -аўшы, -яўшы, -аўшыся, -яўшыся
     */
    void checkV67(Paradigm p, Variant v) {
        String vTag = SetUtils.tag(p, v);
        if (vTag.charAt(1) == BelarusianWordNormalizer.pravilny_nacisk) {
            return;
        }
        char tr = BelarusianTags.getInstance().getValueOfGroup(vTag, "Трыванне");
        switch (tr) {
        case 'M':
                if (v.getForm().isEmpty()) {
                    return;
                }
                Form rg = getForm(v, "RG");
                if (rg == null) {
                    throw new KnownError("6_tryvanni", "Няма RG");
                } else {
                    if (!rg.getValue().isEmpty()) {
                        String wk = StressUtils.unstress(rg.getValue());
                        if (!wk.endsWith("учы") && !wk.endsWith("ючы") && !wk.endsWith("учыся") && !wk.endsWith("ючыся")
                                && !wk.endsWith("ячы") && !wk.endsWith("ячыся") && !wk.endsWith("ачы")
                                && !wk.endsWith("ачыся")) {
                            throw new KnownError("6_tryvanni", "Няправільны канчатак RG: " + wk);
                        }
                    }
                }
            break;
        case 'P':
                if (v.getForm().isEmpty()) {
                    return;
                }
                Form pg = getForm(v, "PG");
                if (pg == null) {
                    throw new KnownError("6_tryvanni", "Няма PG");
                } else {
                    if (!pg.getValue().isEmpty()) {
                        String wk = StressUtils.unstress(pg.getValue());
                        if (!wk.endsWith("шы") && !wk.endsWith("шыся")) {
                            throw new KnownError("6_tryvanni", "Няправільны канчатак PG: " + wk);
                        }
                    }
                }
            break;
        default:
            throw new KnownError("6_tryvanni", "Непазначана трываньне");
        }
    }

    /**
     * праверыць усе формы - павінны быць аднолькавымі ва ўсіх дзеясловах для закончанага і незакончанага
     * трываньня
     */
    void checkV8(Paradigm p, Variant v) {
        String tag = SetUtils.tag(p, v);
        char tr = BelarusianTags.getInstance().getValueOfGroup(tag, "Трыванне");
        String[] formTags;
        switch (tr) {
        case 'M':
            formTags = new String[] { "0", "R1S", "R2S", "R3S", "R1P", "R2P", "R3P", "PXSM", "PXSF", "PXSN",
                    "PXPX", "I2S", "I2P", "RG" };
            break;
        case 'P':
            formTags = new String[] { "0", "F1S", "F2S", "F3S", "F1P", "F2P", "F3P", "PXSM", "PXSF", "PXSN",
                    "PXPX", "I2S", "I2P", "PG" };
            break;
        default:
            return;
        }

        List<Form> standardForms = new ArrayList<>();
        standardForms.addAll(v.getForm());
        for (int i = 0; i < standardForms.size(); i++) {
            if (standardForms.get(i).getType() == FormType.NONSTANDARD) {
                standardForms.remove(i);
                i--;
            }
            // if ("potential".equals(standardForms.get(i).getType())) {
            // standardForms.remove(i);
            // i--;
            // }
        }

        if (standardForms.size() != formTags.length) {
            throw new KnownError("8_tryvanni", "Няправільныя формы");
        }
        for (int i = 0; i < formTags.length; i++) {
            if (!standardForms.get(i).getTag().equals(formTags[i])) {
                throw new KnownError("8_tryvanni", "Няправільная форма");
            }
        }
    }

    /**
     * MNP->PNP
     */
    void check9(Paradigm p, Variant v) {
        String tag = SetUtils.tag(p, v);
        char cascinaMovy = BelarusianTags.getInstance().getValueOfGroup(tag, "Часціна мовы");
        char subst = BelarusianTags.getInstance().getValueOfGroup(tag, "Субстантываванасць");
        if (cascinaMovy == 'N' && subst != 0) {
            check9do(p, v);
        }
        if (cascinaMovy == 'A') {
            check9do(p, v);
        }
    }

    void check9do(Paradigm p, Variant v) {
            for (Form f : v.getForm()) {
                if (f.getTag().length() == 3 && f.getTag().charAt(2) == 'P' && f.getTag().charAt(0) != 'P') {
                    throw new KnownError("9_prymietnikavyja_formy",
                            "Павінна быць P у першый літары формы " + f.getTag());
                }
            }
    }

    /**
     * Пустыя формы.
     */
    void check10(Paradigm p, Variant v) {
        for (Form f : v.getForm()) {
            if (f.getValue()==null || f.getValue().trim().isEmpty()) {
//                throw new KnownError("10_pustyja_formy", "Пустыя формы ў парадыгме");
                // TODO пакуль ігнаруем
            }
        }
    }

    /**
     * Колькасць злучкоў.
     */
    void check11(Paradigm p, Variant v) {
            int c = zlucki(v.getLemma());
            for (Form f : v.getForm()) {
                if (f.getValue() == null || f.getValue().isEmpty()) {
                    continue;
                }
                if (zlucki(f.getValue()) != c) {
                    throw new KnownError("11_zlucki", "Колькасць злучкоў несупадае ў форме " + f.getValue());
                }
            }
    }

    int zlucki(String w) {
        int c=0;
        for(int i=0;i<w.length();i++) {
            if (w.charAt(i)=='-') {
                c++;
            }
        }
        return c;
    }

    Form getForm(Variant v, String formTag) {
        for (Form f : v.getForm()) {
            if (f.getTag().equals(formTag)) {
                return f;
            }
        }
        return null;
    }

    static class KnownError extends RuntimeException {
        final String fileprefix, text;
        public KnownError(String fileprefix, String text) {
            this.fileprefix = fileprefix;
            this.text = text;
        }
    }
}
