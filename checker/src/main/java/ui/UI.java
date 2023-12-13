package ui;

import java.awt.Component;
import java.awt.Container;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.swing.JFrame;
import javax.swing.SwingWorker;

public class UI {
    public static CreateUI ui;

    public static void init(String title) {
        ui = new CreateUI();
        ui.setTitle(title);
        ui.setBounds(200, 100, 900, 500);
        ui.setVisible(true);
    }

    public static void addTab(Component c) {
        ui.tab.add(c, c.getName());
    }

    public static JFrame getFrame() {
        return ui;
    }

    public static void call(final Exec exec) {
        ui.message.setText("");
        ui.log.setText("");
        new SwingWorker<String, Object>() {

            protected String doInBackground() throws Exception {
                ui.setEnabled(false);

                try {
                    exec.execute();

                    UI.message("Зроблена");
                    UI.setStepsCount(0);
                } catch (Throwable ex) {
                    UI.addError(ex);
                    UI.message("Памылка");
                    UI.setStepsCount(0);
                }
                return null;
            }

            protected void done() {
                ui.setEnabled(true);
            }
        }.execute();
    }

    public static void callNoDisable(final Exec exec) {
        ui.message.setText("");

        new SwingWorker<String, Object>() {

            protected String doInBackground() throws Exception {
                try {
                    exec.execute();

                    UI.message("Зроблена");
                    UI.setStepsCount(0);
                } catch (Throwable ex) {
                    UI.addError(ex);
                    UI.message("Памылка");
                    UI.setStepsCount(0);
                }
                return null;
            }

            protected void done() {
                ui.setEnabled(true);
            }
        }.execute();
    }

    public static void message(String msg) {
        ui.message.setText(msg);
    }

    public static void setStepsCount(int count) {
        ui.bar.setMaximum(count);
        ui.bar.setValue(0);
    }

    public static void progress() {
        ui.bar.setValue(ui.bar.getValue() + 1);
    }

    public static void addError(String msg) {
        ui.log.append(msg + "\n");
        ui.log.setCaretPosition(ui.log.getText().length());
    }

    public static void addError(Throwable ex) {
        StringWriter o = new StringWriter();
        ex.printStackTrace(new PrintWriter(o));
        ui.log.append(o + "\n");
        ui.log.setCaretPosition(ui.log.getText().length());
    }

    public static void setEnabled(Container comp, boolean enabled) {
        comp.setEnabled(enabled);
        for (int i = 0; i < comp.getComponentCount(); i++) {
            Component c = comp.getComponent(i);
            c.setEnabled(enabled);
            if (c instanceof Container) {
                setEnabled((Container) c, enabled);
            } else {
                c.setEnabled(enabled);
            }
        }
    }
}
