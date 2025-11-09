# Як ствараць файлы для праверкі правапісу

Для праверкі правапісу экспартуецца толькі правераная частка базы. Дадаюцца словы, якія маюць пазнакі:
- правапіс 2008(атрыбут pravapis="A2008")
- наяўнасць у слоўніках(атрыбут slouniki=...), за выключэннем слоўніка "piskunou2012"
- атрыбут regulation="add"

Адкідаюцца словы з K.xml(абрэвіятуры), F.xml(часткі слоў), а таксама парадыгмы, што ўтрымліваюць прагалы і шматкроп'е.

Словы, што адкідаем для праверкі правапісу, можна паглядзець падчас зборкі ў data-assembly/target/to-review-no-show/.

## LanguageTool

1. Сабраць артэфакт linguistics.grammardb.spell.languagetool
2. выправіць у https://github.com/languagetool-org/languagetool праз merge request: версію артэфакта belarusian-pos-dict.version у /pom.xml

## LibreOffice

Апісанне працы з git: https://wiki.documentfoundation.org/Development/GetInvolved

- забраць код праз "git clone https://git.libreoffice.org/core" як напісана на https://www.libreoffice.org/about-us/source-code/

- выцягнуць слоўнікі праз "git submodule update --init dictionaries" як напісана на https://wiki.documentfoundation.org/Development/Submodules

- наладзіць gerrit к напісана на https://wiki.documentfoundation.org/Development/gerrit/setup#Setting_yourself_up_for_gerrit_-_the_manual_way

- спампаваць модулі (як напісана на https://wiki.documentfoundation.org/Development/GetInvolved):

```
./g pull -r
./g -z
```

- скампактаваць слоўнік праз affixcompress, і стварыць каміт, як напісана на https://wiki.documentfoundation.org/Development/Submodules

- змяніць каміт пасля нейкага выпраўлення: git push origin HEAD:refs/for/master
