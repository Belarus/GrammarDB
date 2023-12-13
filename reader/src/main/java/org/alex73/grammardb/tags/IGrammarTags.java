package org.alex73.grammardb.tags;

import java.util.List;
import java.util.Set;

public interface IGrammarTags {
    TagLetter getRoot();

    List<String> describe(String codeBegin, Set<String> excludeGroups);

    char getValueOfGroup(String code, String group);

    TagLetter getNextAfter(String codeBegin);
}
