package org.alex73.grammardb.utils;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "compile-lt-spell-checker", defaultPhase = LifecyclePhase.COMPILE)
public class CompileLTSpellChecker extends AbstractMojo {
    @Parameter(property = "inputText", required = true)
    String inputText;
    @Parameter(property = "inputInfo", required = true)
    String inputInfo;
    @Parameter(property = "outputDict", required = true)
    String outputDict;

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            run();
        } catch (Exception ex) {
            throw new MojoFailureException(ex);
        }
    }

    private void run() throws Exception {
        org.languagetool.tools.RunSpellDictionaryBuilder.main(new String[] { "be-BY", "-i", inputText, "-info", inputInfo, "-o", outputDict });
    }
}
