package org.alex73.grammardb.utils;

import java.io.FileWriter;
import java.io.PrintWriter;

import org.alex73.grammardb.tags.BelarusianTags;
import org.alex73.grammardb.tags.TagLetter;

/**
 * This code exports list of grammar tags into graph for GraphViz visualization.
 */
public class ExportGraphviz {
    static PrintWriter writer;
    static int nodeId;

    static void exportNodeToDot(TagLetter current, int parentNodeId, boolean formTag) {
        int currentNodeId = nodeId++;

        // 1. Generate the Label (Values inside the rectangle)
        String label = "shape=box, label=<";
        if (current.groupName != null) {
            label += "<b>" + current.groupName + "</b>";
            for (String value : current.valuesList) {
                label += "<br/>" + value;
            }
        } else {
            label += "/";
        }
        label += ">";

        // 2. Determine Styling
        String color = formTag ? "color=\"#0000ff\", penwidth=1, " : "";

        String nodeStyle = "";
        if (current.groupName == null) {
            nodeStyle = " style=\"invis\"";
        }
        String edgeStyle = "";
        if (parentNodeId == 0) {
            edgeStyle = " style=\"invis\"";
        }

        // 3. Write node definition and link from parent
        writer.println("  node_" + currentNodeId + " [" + color + label + nodeStyle + "];");
        writer.println("  node_" + parentNodeId + " -> node_" + currentNodeId + "[" + edgeStyle + "];");
        for (TagLetter c : current.children) {
            exportNodeToDot(c, currentNodeId, formTag || current.isLatestInParadigm());
        }
    }

    public static void main(String[] args) throws Exception {
        BelarusianTags tags = new BelarusianTags();
        writer = new PrintWriter(new FileWriter("tags.dot"));
        writer.println("digraph BelarusianGrammar {");
        writer.println("  rankdir=LR;");
        writer.println("  graph [pad=0.5];");
        writer.println("  node [shape=box, fontname=\"Arial\", style=filled, fillcolor=\"#f9f9f9\"];");
        writer.println("  edge [fontname=\"Arial\", fontsize=9, color=\"#555555\"];");

        exportNodeToDot(tags.getRoot(), 0, false);

        writer.println("  Legend [label=\"Сіняя рамка - тэгі форм\", shape=note, fillcolor=\"#fff9c4\"];");
        writer.println("  node_0 -> Legend [style=\"invis\"];");
        writer.println("}");
        writer.close();

        System.out.println("Output file created, run Graphviz: dot -Gsize=10,15 -Tsvg tags.dot > tags.svg");
    }
}
