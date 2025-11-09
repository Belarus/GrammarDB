package org.alex73.grammardb.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

import org.alex73.grammardb.FormsReadyFilter;
import org.alex73.grammardb.GrammarDB2;
import org.alex73.grammardb.GrammarDBSaver;
import org.alex73.grammardb.structures.Form;
import org.alex73.grammardb.structures.Paradigm;
import org.alex73.grammardb.structures.Variant;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Экспартуе звесткі для праверкі правапісу і рэлізу базы.
 */
@Mojo(name = "export-database", defaultPhase = LifecyclePhase.COMPILE)
public class ExportDatabase extends AbstractMojo {

    @Parameter(property = "xmlDataDir", required = true)
    String xmlDataDir;
    @Parameter(property = "exportDir", required = true)
    String exportDir;
    @Parameter(property = "reviewDir", required = true)
    String reviewDir;

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            run();
        } catch (Exception ex) {
            throw new MojoFailureException(ex);
        }
    }

    private void run() throws Exception {
        Path inDir = Paths.get(xmlDataDir);
        Path exportDirPath = Paths.get(exportDir);
        Path reviewDirPath = Paths.get(reviewDir);
        deleteDirectory(exportDirPath);
        deleteDirectory(reviewDirPath);

        GrammarDB2 db = GrammarDB2.initializeFromDir(inDir.toAbsolutePath().toString());
        System.out.println("Фільтраванне базы - што не паказваем - у " + reviewDirPath.toAbsolutePath() + "...");
        db.getAllParadigms().parallelStream().forEach(p -> {
            for (Variant v : p.getVariant()) {
                if (v.getForm().isEmpty()) {
                    continue;
                }
                List<Form> fs = FormsReadyFilter.getAcceptedForms(FormsReadyFilter.MODE.SHOW, p, v);
                if (fs != null && !fs.isEmpty()) {
                    v.getForm().clear();
                }
            }
        });
        removeEmpty(db.getAllParadigms());
        GrammarDBSaver.sortAndStore(db, reviewDirPath.toAbsolutePath().toString());

        System.out.println("Фільтраванне базы для паказу - у " + exportDirPath.toAbsolutePath() + "...");
        db = GrammarDB2.initializeFromDir(inDir.toAbsolutePath().toString());
        db.getAllParadigms().parallelStream().forEach(p -> {
            for (Variant v : p.getVariant()) {
                List<Form> fs = FormsReadyFilter.getAcceptedForms(FormsReadyFilter.MODE.SHOW, p, v);
                v.getForm().clear();
                if (fs == null) {
                    continue;
                }
                v.getForm().addAll(fs);
            }
        });
        removeEmpty(db.getAllParadigms());
        GrammarDBSaver.sortAndStore(db, exportDirPath.toAbsolutePath().toString());

        System.out.println("Запісваем кэш у " + exportDirPath.resolve("cache/").toAbsolutePath() + "...");
        db.makeCache(exportDirPath.resolve("binary-cache/").toAbsolutePath().toString());
    }

    static void deleteDirectory(Path d) throws IOException {
        if (Files.exists(d)) {
            Files.walk(d).sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
        }
    }

    static void removeEmpty(List<Paradigm> paradigms) {
        for (int i = 0; i < paradigms.size(); i++) {
            Paradigm p = paradigms.get(i);
            for (int j = 0; j < p.getVariant().size(); j++) {
                if (p.getVariant().get(j).getForm().isEmpty()) {
                    p.getVariant().remove(j);
                    j--;
                }
            }
            if (p.getVariant().isEmpty()) {
                paradigms.remove(i);
                i--;
            }
        }
    }
}
