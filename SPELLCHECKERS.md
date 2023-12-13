# Як ствараць файлы для праверкі правапісу

## LanguageTool

Апісанне: https://dev.languagetool.org/hunspell-support

- спампаваць https://languagetool.org/download/LanguageTool-stable.zip і распакаваць

- скампілявать hunspell слоўнік: java -cp languagetool.jar org.languagetool.tools.SpellDictionaryBuilder be-BY -i /tmp/slovy-2008.txt -info ~/gits/languagetool/languagetool-language-modules/be/src/main/resources/org/languagetool/resource/be/hunspell/be_BY.info -o /tmp/be_BY.dict

- зрабіць pull request у https://github.com/languagetool-org/languagetool

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
