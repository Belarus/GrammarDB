/**************************************************************************
 Korpus - Corpus Linguistics Software.

 Copyright (C) 2013 Aleś Bułojčyk (alex73mail@gmail.com)

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

package org.alex73.grammardb.tags;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Чытае структуру граматычных тэгаў з канфігурацыйнага файла тэгаў.
 */
public class TagsReader {

    public static class TagNode {
        public String group;
        public boolean paradigm;
        public LinkedHashMap<String, String> values;
        public List<TagNode> children;

        public String getGroup() {
            return group;
        }

        public boolean isParadigm() {
            return paradigm;
        }

        public LinkedHashMap<String, String> getValues() {
            return values;
        }

        public List<TagNode> getChildren() {
            return children;
        }
    }

    private static class StackEntry {
        final int indent;
        final TagNode node;

        StackEntry(int indent, TagNode node) {
            this.indent = indent;
            this.node = node;
        }
    }

    public static List<TagNode> read() {
        try (Reader reader = new InputStreamReader(openDefaultResource(), StandardCharsets.UTF_8)) {
            return parse(reader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<TagNode> parse(Reader reader) throws IOException {
        BufferedReader br = (reader instanceof BufferedReader) ? (BufferedReader) reader : new BufferedReader(reader);
        List<TagNode> rootNodes = new ArrayList<>();
        Deque<StackEntry> stack = new ArrayDeque<>();

        String line;
        while ((line = br.readLine()) != null) {
            int indent = 0;
            while (indent < line.length() && line.charAt(indent) == ' ') {
                indent++;
            }
            String content = line.substring(indent).trim();
            if (content.isEmpty() || content.startsWith("#")) {
                continue;
            }

            boolean paradigm = false;
            if (content.startsWith("*")) {
                paradigm = true;
                content = content.substring(1).trim();
            }

            int colon = content.indexOf(':');
            if (colon == -1) {
                throw new IllegalArgumentException("Invalid format, missing ':': " + line);
            }
            String group = content.substring(0, colon).trim();
            String valuesPart = content.substring(colon + 1).trim();

            LinkedHashMap<String, String> values = new LinkedHashMap<>();
            if (!valuesPart.isEmpty()) {
                String[] pairs = valuesPart.split(";");
                for (String pair : pairs) {
                    String p = pair.trim();
                    if (p.isEmpty()) {
                        continue;
                    }
                    int eq = p.indexOf('=');
                    if (eq == -1) {
                        throw new IllegalArgumentException("Invalid value pair, missing '=': " + p);
                    }
                    String code = p.substring(0, eq).trim();
                    String desc = p.substring(eq + 1).trim();
                    values.put(code, desc);
                }
            }

            TagNode node = new TagNode();
            node.group = group;
            node.paradigm = paradigm;
            node.values = values;

            while (!stack.isEmpty() && stack.peek().indent >= indent) {
                stack.pop();
            }

            if (stack.isEmpty()) {
                rootNodes.add(node);
            } else {
                TagNode parent = stack.peek().node;
                if (parent.children == null) {
                    parent.children = new ArrayList<>();
                }
                parent.children.add(node);
            }
            stack.push(new StackEntry(indent, node));
        }

        return rootNodes;
    }

    public static InputStream openDefaultResource() {
        InputStream is = TagsReader.class.getResourceAsStream("belarusian_tags.txt");
        if (is == null) {
            throw new RuntimeException("Resource not found: belarusian_tags.txt");
        }
        return is;
    }
}

