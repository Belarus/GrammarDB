package org.alex73.grammardb.check;

import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.alex73.korpus.base.GrammarDB2;
import org.alex73.korpus.base.GrammarDBSaver;

import ui.Exec;
import ui.UI;

public class DbUtilUI {

    public static void main(String[] args) {
        DbUtilPanel panel;
        UI.init("Граматычная база");
        UI.addTab(panel = new DbUtilPanel());

        System.setOut(new PrintStream(System.out) {
            @Override
            public void println(String x) {
                UI.addError(x);
            }
        });
        System.setErr(new PrintStream(System.err) {
            @Override
            public void println(String x) {
                UI.addError(x);
            }
        });

        panel.btnCheck.addActionListener(l -> {
            UI.call(CHECK);
        });
        panel.btnSort.addActionListener(l -> {
            UI.call(SORT);
        });
        panel.btnMakeCache.addActionListener(l -> {
            UI.call(MAKE_CACHE);
        });
    }

    static String backup() throws Exception {
        String d = "bak/" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        new File(d).mkdirs();
        for (File f : new File(".").listFiles()) {
            if (f.getName().endsWith(".xml")) {
                if (!f.renameTo(new File(d,f.getName()))) {
                    throw new Exception("Памылка пераносу " + f);
                }
            }
        }
        return d;
    }

    protected static Exec CHECK = new Exec() {
        public void execute() throws Exception {
            try {
                new CheckGrammarDB().validateXMLs(".");
                String dir = backup();
                new CheckGrammarDB().check(dir);
                System.out.println("Зроблена");
            } catch (Throwable ex) {
                StringWriter s = new StringWriter();
                ex.printStackTrace(new PrintWriter(s));
                System.out.println("Памылка: " + ex + "\n" + s);
            }
        }
    };
    protected static Exec SORT = new Exec() {
        public void execute() throws Exception {
            System.out.println("Чытаем граматычную базу...");
            try {
                new CheckGrammarDB().validateXMLs(".");
                String dir = backup();
                GrammarDB2 db = GrammarDB2.initializeFromDir(dir);
                new CheckGrammarDB().removeErrors(db);
                System.out.println("Запісваем граматычную базу...");
                GrammarDBSaver.sortAndStore(db, ".");
            } catch (Throwable ex) {
                StringWriter s = new StringWriter();
                ex.printStackTrace(new PrintWriter(s));
                System.out.println("Памылка: " + ex + "\n" + s);
            }
        }
    };
    protected static Exec MAKE_CACHE = new Exec() {
        public void execute() throws Exception {
            System.out.println("Чытаем граматычную базу...");
            GrammarDB2 db = GrammarDB2.initializeFromDir(".");
            System.out.println("Запісваем кэш...");
            db.makeCache(".");
            System.out.println("Зроблена");
        }
    };
}
