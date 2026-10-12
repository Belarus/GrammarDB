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

import org.alex73.grammardb.tags.TagLetter.OneLetterInfo;
import org.alex73.grammardb.tags.TagsReader.TagNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Рэалізацыя {@link IGrammarTags}, якая счытвае структуру граматычных тэгаў
 * з канфігурацыйнага файла {@code belarusian_tags.txt}.
 * <p>
 * Апісанне фармату знаходзіцца ў каментарах у самім файле {@code belarusian_tags.txt}.
 */
public class BelarusianTags implements IGrammarTags {
    private final List<TagNode> tagNodes;
    private final TagLetter root;

    public BelarusianTags() {
        this.tagNodes = TagsReader.read();
        this.root = new TagLetter(null, null);
        for (TagNode node : this.tagNodes) {
            buildTagLetter(root, node);
        }

        checkParadigmMarks(root, "", 0);
        checkDuplicateGroups(root, new ArrayList<>());
    }

    private void buildTagLetter(TagLetter parent, TagNode node) {
        List<String> valuesList = new ArrayList<>();
        if (node.values != null) {
            for (Map.Entry<String, String> entry : node.values.entrySet()) {
                valuesList.add(entry.getKey() + ":" + entry.getValue());
            }
        }

        TagLetter c = new TagLetter(node.group, valuesList.toArray(new String[0]));
        if (node.paradigm) {
            c.latestInParadigm();
        }
        parent.children.add(c);

        if (node.values != null) {
            for (Map.Entry<String, String> entry : node.values.entrySet()) {
                char code = entry.getKey().charAt(0);
                OneLetterInfo newLetter = new OneLetterInfo();
                newLetter.groupName = c.groupName;
                newLetter.letter = code;
                newLetter.description = entry.getValue();
                newLetter.nextLetters = c;
                for (OneLetterInfo li : parent.letters) {
                    if (li.letter == newLetter.letter) {
                        throw new RuntimeException("Already exist in letters: " + newLetter.letter);
                    }
                }
                parent.letters.add(newLetter);
            }
        }

        if (node.children != null) {
            for (TagNode childNode : node.children) {
                buildTagLetter(c, childNode);
            }
        }
    }

    @Override
    public TagLetter getRoot() {
        return root;
    }

    /**
     * Шукаем ва ўсіх тэгах count(latestInParadigm)==1
     */
    private void checkParadigmMarks(TagLetter tl, String code, int pmCount) {
        if (tl.isLatestInParadigm()) {
            pmCount++;
        }
        if (tl.isFinish()) {
            if (pmCount != 1) {
                throw new RuntimeException("pmCount=" + pmCount + " for " + code);
            }
        } else {
            for (OneLetterInfo letterInfo : tl.letters) {
                checkParadigmMarks(letterInfo.nextLetters, code + letterInfo.letter, pmCount);
            }
        }
    }

    /**
     * Правярае ці няма аднолькавых назваў груп у іерархіі.
     */
    private void checkDuplicateGroups(TagLetter tl, List<String> path) {
        for (OneLetterInfo li : tl.letters) {
            if (path.contains(li.groupName)) {
                throw new RuntimeException("Duplicate group '" + li.groupName + "' in " + path);
            }
            path.add(li.groupName);
            checkDuplicateGroups(li.nextLetters, path);
            path.remove(path.size() - 1);
        }
    }

    /**
     * Ці правільны тэг у парадыгме? latestInParadigm==true
     */
    public boolean isValidParadigmTag(String code, String w) {
        TagLetter after = getTagLetterAfter(code, w);
        if (after == null) {
            return false;
        }
        if (!after.isLatestInParadigm()) {
            if (w != null) {
                System.out.println(code + " " + w + " - няправільны тэг парадыгмы");
            }
            return false;
        }
        return true;
    }

    public boolean isValidFormTag(String code, String w) {
        TagLetter after = getTagLetterAfter(code, w);
        if (after == null) {
            return false;
        }
        if (!after.isFinish()) {
            if (w != null) {
                System.out.println(code + " " + w + " - замалы код");
            }
            return false;
        }
        return true;
    }

    private TagLetter getTagLetterAfter(String code, String w) {
        TagLetter tags = root;
        for (char c : code.toCharArray()) {
            if (c == 'x') {
                if (tags.isFinish()) {
                    if (w != null) {
                        System.out.println(code + " " + w + " - зашмат літараў у кодзе");
                    }
                    return null;
                }
                TagLetter first = tags.letters.get(0).nextLetters;
                for (TagLetter.OneLetterInfo li : tags.letters) {
                    if (li.nextLetters != first) {
                        if (w != null) {
                            System.out.println(code + " " + w + " - незразумелы шлях раскадаваньня");
                        }
                        return null;
                    }
                }
                tags = first;
            } else {
                tags = tags.next(c);
                if (tags == null) {
                    if (w != null) {
                        System.out.println(code + " " + w + " - невядомая літара ў кодзе");
                    }
                    return null;
                }
            }
        }
        return tags;
    }

    @Override
    public TagLetter getNextAfter(String codeBegin) {
        TagLetter tags = root;
        for (char c : codeBegin.toCharArray()) {
            tags = tags.next(c);
            if (tags == null) {
                throw new RuntimeException("Error code: " + codeBegin);
            }
        }
        return tags;
    }

    @Override
    public List<String> describe(String codeBegin, Set<String> excludeGroups) {
        List<String> result = new ArrayList<>();
        TagLetter tags = root;
        for (char c : codeBegin.toCharArray()) {
            OneLetterInfo info = tags.getLetterInfo(c);
            if (info == null) {
                throw new RuntimeException("Wrong tag: " + codeBegin);
            }
            tags = tags.next(c);
            if (tags == null) {
                throw new RuntimeException("Wrong tag: " + codeBegin);
            }
            if (excludeGroups == null || !excludeGroups.contains(info.groupName)) {
                result.add(info.description);
            }
        }
        return result;
    }

    @Override
    public char getValueOfGroup(String code, String group) {
        TagLetter tags = root;
        for (char c : code.toCharArray()) {
            OneLetterInfo li = tags.getLetterInfo(c);
            if (li == null) {
                throw new RuntimeException("Wrong tag: " + code);
            }
            if (group.equals(li.groupName)) {
                return li.letter;
            }
            tags = tags.next(c);
            if (tags == null) {
                throw new RuntimeException("Wrong tag: " + code);
            }
        }
        return 0;
    }
}
