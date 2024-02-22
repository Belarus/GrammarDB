package org.alex73.grammardb;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.alex73.grammardb.structures.Form;
import org.alex73.grammardb.structures.Paradigm;
import org.alex73.grammardb.structures.Variant;
import org.alex73.grammardb.structures.Wordlist;
import org.alex73.grammardb.themes.Theme;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class GrammarDB2 {
    public static final String THEMES_FILE = "themes.txt";
    
    public static final char pravilny_nacisk = '\u0301'; // see BelarusianWordNormalizer
    public static final char pravilny_apostraf = '\u02BC'; // see BelarusianWordNormalizer

    private Map<String, Theme> themes;
    private List<Paradigm> allParadigms = new ArrayList<>();

    private static JAXBContext CONTEXT;

    public static synchronized JAXBContext getContext() throws Exception {
        if (CONTEXT == null) {
            CONTEXT = JAXBContext.newInstance(Wordlist.class.getPackage().getName());
        }
        return CONTEXT;
    }

    public List<Paradigm> getAllParadigms() {
        return allParadigms;
    }

    public static GrammarDB2 initializeFromJar() throws Exception {
        long be = System.currentTimeMillis();
        GrammarDB2 r = null;
        try (InputStream in = GrammarDB2.class.getResourceAsStream("/db.cache")) {
            if (in != null) {
                Input input = new Input(in, 65536);
                r = loadFromCache(input);
            }
        }
        if (r == null) {
            r = initializeFromDir("GrammarDB");
        }
        long af = System.currentTimeMillis();
        System.out.println("GrammarDB deserialization time: " + (af - be) + "ms");
        return r;
    }

    public static GrammarDB2 empty() {
        return new GrammarDB2();
    }

    public static GrammarDB2 initializeFromDir(String dir) throws Exception {
        File[] forLoads = getFilesForLoad(new File(dir));
        GrammarDB2 r = new GrammarDB2(forLoads);
        return r;
    }

    public static GrammarDB2 initializeFromFile(File file) throws Exception {
        GrammarDB2 r = new GrammarDB2(file);
        return r;
    }

    public void makeCache(String dir) throws IOException {
        Path cacheFile = Paths.get(dir).resolve("db.cache");

        Files.createDirectories(cacheFile.getParent());
        long be = System.currentTimeMillis();
        Kryo kryo = new Kryo();
        try (Output output = new Output(Files.newOutputStream(cacheFile), 65536)) {
            kryo.writeObject(output, this);
        }
        long af = System.currentTimeMillis();
        System.out.println("GrammarDB serialization time: " + (af - be) + "ms");
    }

    /**
     * Get files for load(xml and themes.txt)
     */
    public static File[] getFilesForLoad(File dir) throws Exception {
        File[] result = dir.listFiles(new FileFilter() {
            public boolean accept(File pathname) {
                return pathname.isFile() && (pathname.getName().endsWith(".xml")
                        || pathname.getName().endsWith(".xml.gz") || pathname.getName().equals(THEMES_FILE));
            }
        });
        if (result == null) {
            throw new Exception("There are no files for GrammarDB in the " + dir.getAbsolutePath());
        }
        /*
         * Arrays.sort(result, new Comparator<File>() {
         * 
         * @Override public int compare(File o1, File o2) { String n1 =
         * o1.getName(); String n2 = o2.getName(); if (n1.equals(THEMES_FILE)) {
         * return -1; } else if (n2.equals(THEMES_FILE)) { return 1; } else {
         * return n1.compareTo(n2); } } });
         */
        Arrays.sort(result);
        return result;
    }

    private static GrammarDB2 loadFromCache(Input input) throws Exception {
        Kryo kryo = new Kryo();
        return kryo.readObject(input, GrammarDB2.class);
    }

    private void addTheme(String part, String theme) {
        Theme th = themes.get(part);
        if (th == null) {
            th = new Theme("");
            themes.put(part, th);
        }
        for (String p : theme.split("/")) {
            th = th.getOrCreateChild(p);
        }
    }

    public synchronized void addThemesFile(File file) throws Exception {
        themes = new TreeMap<>();
        BufferedReader rd = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        String s;
        String part = "";
        while ((s = rd.readLine()) != null) {
            s = s.trim();
            if (s.length() == 1) {
                part = s;
            } else if (s.length() > 1) {
                addTheme(part, s);
            }
        }
        rd.close();
    }

    /**
     * Minimize memory usage.
     */
    public static void optimize(Paradigm p) {
        p.setLemma(optimizeString(fix(p.getLemma())));
        p.setTag(optimizeString(p.getTag()));
        for (Variant v : p.getVariant()) {
            v.setLemma(optimizeString(fix(v.getLemma())));
            v.setPravapis(optimizeString(v.getPravapis()));
            v.setPrystauki(fix(v.getPrystauki()));
            for (Form f : v.getForm()) {
                f.setTag(optimizeString(f.getTag()));
                f.setValue(optimizeString(fix(f.getValue())));
                f.setSlouniki(optimizeString(f.getSlouniki()));
                f.setPravapis(optimizeString(f.getPravapis()));
            }
        }
    }

    /**
     * Remove duplicate strings from memory. 
     */
    public static String optimizeString(String s) {
        return s == null ? null : s.intern();
    }

    /**
     * Змяняе націск і апостраф.
     */
    public static String fix(String s) {
        return s == null ? null : s.replace('\'', pravilny_apostraf).replace('+', pravilny_nacisk);
    }

    public void addXMLFile(File file) throws Exception {
        Unmarshaller unm = getContext().createUnmarshaller();

        InputStream in;
        if (file.getName().endsWith(".gz")) {
            in = new BufferedInputStream(new GZIPInputStream(new FileInputStream(file), 16384), 65536);
        } else {
            in = new BufferedInputStream(new FileInputStream(file), 65536);
        }
        try {
            Wordlist words = (Wordlist) unm.unmarshal(in);
            for (Paradigm p : words.getParadigm()) {
                optimize(p);
            }
            synchronized (this) {
                allParadigms.addAll(words.getParadigm());
            }
        } finally {
            in.close();
        }
    }

    /**
     * Only for kryo instantiation.
     */
    private GrammarDB2() {
    }

    /**
     * Read xml files for initialize.
     */
    private GrammarDB2(File... forLoads) throws Exception {
        long be = System.currentTimeMillis();

        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(16);
        long latest = 0;
        Future<?>[] results = new Future<?>[forLoads.length];
        for (int i = 0; i < forLoads.length; i++) {
            latest = Math.max(latest, forLoads[i].lastModified());
            System.out.println(forLoads[i].getPath());
            final File process = forLoads[i];
            results[i] = executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (process.getName().equals(THEMES_FILE)) {
                            addThemesFile(process);
                        } else {
                            addXMLFile(process);
                        }
                    } catch (Exception ex) {
                        throw new RuntimeException("Error in " + process, ex);
                    }
                }
            });
        }
        executor.shutdown();
        if (!executor.awaitTermination(10, TimeUnit.MINUTES)) {
            throw new Exception("Load GrammarDB2 timeout");
        }
        for (Future<?> f : results) {
            f.get();
        }
        long af = System.currentTimeMillis();
        System.out.println("GrammarDB loading time: " + (af - be) + "ms");
    }

    public Theme getThemes(String grammar) {
        return themes.get(grammar);
    }
}
