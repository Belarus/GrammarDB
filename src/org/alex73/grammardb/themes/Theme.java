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

package org.alex73.grammardb.themes;

import java.util.ArrayList;
import java.util.List;

import org.alex73.grammardb.themes.Theme;

import org.alex73.grammardb.themes.Theme;

/**
 * Сховішча дрэва тэмаў слоў.
 */
public class Theme {
    public String name;
    public List<Theme> children = new ArrayList<>();

    /**
     * Constructor for deserialization only.
     */
    public Theme() {
    }

    public Theme(String name) {
        this.name = name;
    }

    public Theme getOrCreateChild(String name) {
        for (Theme child : children) {
            if (child.name.equals(name)) {
                return child;
            }
        }
        Theme child = new Theme(name);
        children.add(child);
        return child;
    }
}
