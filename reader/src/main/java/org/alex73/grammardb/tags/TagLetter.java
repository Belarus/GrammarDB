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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.alex73.grammardb.tags.TagLetter;

public class TagLetter {

    public List<OneLetterInfo> letters = new ArrayList<OneLetterInfo>();

    /** true if letter must be latest in paradigm */
    private boolean latestInParadigm;

    /**
     * Add child info like:
     * 
     * Cascina => A:Nazounik;V:Dziejaslou;...
     */
    public TagLetter add(String text) {
        int pos = text.indexOf("=>");
        if (pos <= 0) {
            throw new RuntimeException("Error in add: " + text);
        }
        String groupName = text.substring(0, pos).trim();
        String values = text.substring(pos + 2).trim();

        TagLetter c = new TagLetter();
        for (String v : values.split(";")) {
            char code = v.charAt(0);
            if (!(code >= 'A' && code <= 'Z') && !(code >= '0' && code <= '9') && !(code == '+')) {
                throw new RuntimeException("Error in letters: " + values);
            }
            if (v.charAt(1) != ':') {
                throw new RuntimeException("Error in letters: " + values);
            }
            OneLetterInfo newLetter = new OneLetterInfo();
            newLetter.groupName = groupName;
            newLetter.letter = code;
            newLetter.description = v.substring(2);
            newLetter.nextLetters = c;
            for (OneLetterInfo li : letters) {
                if (li.letter == newLetter.letter) {
                    throw new RuntimeException("Already exist in letters: " + values);
                }
            }
            letters.add(newLetter);
        }
        return c;
    }

    public boolean isLatestInParadigm() {
        return latestInParadigm;
    }

    public TagLetter latestInParadigm() {
        latestInParadigm = true;
        return this;
    }

    public TagLetter next(char c) {
        for (OneLetterInfo li : letters) {
            if (li.letter == c) {
                return li.nextLetters;
            }
        }
        return null;
    }

    public String getLetterDescription(char c) {
        for (OneLetterInfo li : letters) {
            if (li.letter == c) {
                return li.description;
            }
        }
        return null;
    }

    public OneLetterInfo getLetterInfo(char c) {
        for (OneLetterInfo li : letters) {
            if (li.letter == c) {
                return li;
            }
        }
        return null;
    }

    public String getNextGroupNames() {
        if (letters.isEmpty()) {
            return null;
        }
        Set<String> uniqNames = new TreeSet<String>();
        for (OneLetterInfo li : letters) {
            uniqNames.add(li.groupName);
        }
        String t = "";
        for (String n : uniqNames) {
            t += " / " + n;
        }
        return t.substring(3);
    }

    public boolean isFinish() {
        return letters.isEmpty();
    }

    public static class OneLetterInfo {
        public String groupName;
        public char letter;
        public String description;
        public TagLetter nextLetters;
    }
}
