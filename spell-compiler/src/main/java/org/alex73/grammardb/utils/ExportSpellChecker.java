package org.alex73.grammardb.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.alex73.grammardb.FormsReadyFilter;
import org.alex73.grammardb.GrammarDB2;
import org.alex73.grammardb.SetUtils;
import org.alex73.grammardb.StressUtils;
import org.alex73.grammardb.structures.Form;
import org.alex73.grammardb.structures.Variant;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Экспартуе спіс слоў для spell checker.
 */
@Mojo(name = "export-spell-checker", defaultPhase = LifecyclePhase.COMPILE)
public class ExportSpellChecker extends AbstractMojo {
    static final Collator BE = Collator.getInstance(Locale.forLanguageTag("be"));

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    MavenProject project;

    @Parameter(property = "xmlDataDir", required = true)
    String xmlDataDir;

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            run();
        } catch (Exception ex) {
            throw new MojoFailureException(ex);
        }
    }

    private void run() throws Exception {
        GrammarDB2 db = GrammarDB2.initializeFromDir(Path.of(xmlDataDir).toAbsolutePath().toString());

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
        // List<String> list2008flc =
        // temp1.stream().sorted(BE).distinct().collect(Collectors.toList());
        List<String> list2008uniq = temp2.stream().sorted(BE).distinct().collect(Collectors.toList());
        List<String> list2008uniqStress = temp3.stream().sorted(BE).distinct().collect(Collectors.toList());

        // Files.write(Paths.get("slovy-2008-z_naciskami_i_razdialicielami.txt"),
        // list2008);
        // Files.write(Paths.get("slovy-2008-forma+lemma+cascinamovy.txt"),
        // list2008flc);

        Path outDir = Path.of(project.getBuild().getDirectory()).resolve("spellchecker/");
        Files.createDirectories(outDir);
        Files.write(outDir.resolve("slovy-2008.txt"), list2008uniq);
        Files.write(outDir.resolve("slovy-2008-stress.txt"), list2008uniqStress);
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
}
