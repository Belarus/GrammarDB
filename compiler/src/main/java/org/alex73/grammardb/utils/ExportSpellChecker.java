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
    @Parameter(property = "textOutputFile2008", required = true)
    String textOutputFile2008;
    @Parameter(property = "textOutputFile2008withStress", required = true)
    String textOutputFile2008withStress;
    @Parameter(property = "textOutputFileAll", required = false)
    String textOutputFileAll;

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            run();
        } catch (Exception ex) {
            throw new MojoFailureException(ex);
        }
    }

    private void run() throws Exception {
        GrammarDB2 db = GrammarDB2.initializeFromDir(Path.of(xmlDataDir).toAbsolutePath().toString());

        List<String> list2008stressed = new ArrayList<>();
        List<String> allWordsStressed = new ArrayList<>();

        System.out.println("Збор слоў для праверкі правапісу...");
        db.getAllParadigms().forEach(p -> {
            for (Variant v : p.getVariant()) {
                for (Form f : v.getForm()) {
                    if (!f.getValue().isEmpty()) {
                        allWordsStressed.add(f.getValue().replace('+', '\u0301').replace(GrammarDB2.pravilny_apostraf, '\''));
                    }
                }
                List<Form> fsSpell = FormsReadyFilter.getAcceptedForms(FormsReadyFilter.MODE.SPELL, p, v);
                if (fsSpell != null) {
                    for (Form f : fsSpell) {
                        list2008stressed.add(f.getValue().replace('+', '\u0301').replace(GrammarDB2.pravilny_apostraf, '\''));
                    }
                }
            }
        });

        System.out.println("Апрацоўка спісаў...");
        duplicateU(list2008stressed);
        duplicateU(allWordsStressed);
        System.out.println("Сартаванне спісаў...");

        List<String> list2008uniqUnstessed = list2008stressed.stream().map(w -> StressUtils.unstress(w)).sorted(BE).distinct().collect(Collectors.toList());
        List<String> list2008uniqStressed = list2008stressed.stream().sorted(BE).distinct().collect(Collectors.toList());
        List<String> allWordsUniqUnstessed = allWordsStressed.stream().map(w -> StressUtils.unstress(w)).sorted(BE).distinct().collect(Collectors.toList());

        Path out = Path.of(textOutputFile2008);
        Path outStress = Path.of(textOutputFile2008withStress);
        Path all = Path.of(textOutputFileAll);
        Files.createDirectories(out.getParent());
        Files.createDirectories(outStress.getParent());
        Files.createDirectories(all.getParent());
        Files.write(out, list2008uniqUnstessed);
        Files.write(outStress, list2008uniqStressed);
        Files.write(all, allWordsUniqUnstessed);
    }

    static void duplicateU(List<String> words) {
        for (int i = 0; i < words.size(); i++) {
            if (words.get(i).startsWith("у") && !words.get(i).startsWith("у" + GrammarDB2.pravilny_nacisk)) {
                // пачынаецца з ненаціскнога "у", дадаем варыянт з "ў"
                String neww = "ў" + words.get(i).substring(1);
                words.add(neww);
            }
        }
    }
}
