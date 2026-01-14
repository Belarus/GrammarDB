This mapping connects the tags from the Belarusian Grammar Database (BNKorpus) to the UniMorph feature schema.

## Part of Speech (POS)

| GrammarDB Tag | UniMorph POS | UniMorph supported features | Notes |
| :--- | :--- | :--- | :--- |
| `N`&nbsp;Назоўнік | `N`&nbsp;Noun | Case, Number, Gender, Animacy | |
| `NP`&nbsp;Назоўнік&nbsp;(уласны) | `PROPN`&nbsp;Proper&nbsp;Name | Case, Number, Gender, Animacy | |
| `A`&nbsp;Прыметнік | `ADJ`&nbsp;Adjective | Case, Number, Gender, Comparison, Animacy | |
| `M`&nbsp;Лічэбнік | `NUM`&nbsp;Numeral | Case, Number, Gender, Animacy | Парадкавыя лічэбнікі падаюцца як прыметнікі |
| `S`&nbsp;Займеннік | `PRO`&nbsp;Pronoun | Case, Number, Gender, Animacy | |
| `V`&nbsp;Дзеяслоў | `V`&nbsp;Verb | Case, Number, Gender, Tense, Aspect, Voice, Person | |
| `P`&nbsp;Дзеепрыметнік | `V.PTCP`&nbsp;Participle | Case, Number, Gender, Animacy | |
| `V`&nbsp;Дзеяслоў (Дзеепрыслоўе) | `V.CVB`&nbsp;Converb | Aspect | |
| `R`&nbsp;Прыслоўе | `ADV`&nbsp;Adverb | Comparison | |
| `C`&nbsp;Злучнік | `CONJ`&nbsp;Conjunction | | |
| `I`&nbsp;Прыназоўнік | `ADP`&nbsp;Adposition | |  |
| `E`&nbsp;Часціца | `PART`&nbsp;Particle | | |
| `Y`&nbsp;Выклічнік | `INTJ`&nbsp;Interjection | | |
| `S`&nbsp;Займеннік&nbsp;(прыналежны) | `DET`&nbsp;Determiner | Case, Number, Gender, Comparison, Animacy | |
| `A`&nbsp;Прыметнік&nbsp;(прыналежны) | `DET`&nbsp;Determiner | Case, Number, Gender, Comparison, Animacy | |


## Features

### Animacy (Адушаўлёнасць)
| GrammarDB Tag | UniMorph Feature |
| :--- | :--- |
| `A`&nbsp;Адушаўлёны | `ANIM`&nbsp;Animate |
| `I`&nbsp;Неадушаўлёны | `INAN`&nbsp;Inanimate |

### Aspect (Трыванне)
| GrammarDB Tag | UniMorph Feature |
| :--- | :--- |
| `P`&nbsp;Закончанае | `PFV`&nbsp;Perfective |
| `M`&nbsp;Незакончанае | `IPFV`&nbsp;Imperfective |

### Case (Склон)
| GrammarDB Tag | UniMorph Feature |
| :--- | :--- |
| `N`&nbsp;Назоўны | `NOM`&nbsp;Nominative |
| `G`&nbsp;Родны | `GEN`&nbsp;Genitive |
| `D`&nbsp;Давальны | `DAT`&nbsp;Dative |
| `A`&nbsp;Вінавальны | `ACC`&nbsp;Accusative |
| `I`&nbsp;Творны | `INS`&nbsp;Instrumental |
| `L`&nbsp;Месны | `ESS`&nbsp;Essive |
| `V`&nbsp;Клічны | `VOC`&nbsp;Vocative |

### Comparison (Ступень параўнання)
| GrammarDB Tag | UniMorph Feature |
| :--- | :--- |
| `C`&nbsp;Вышэйшая | `CMPR`&nbsp;Comparative |
| `S`&nbsp;Найвышэйшая | `SPRL`&nbsp;Superlative |

### Gender (Род)
| GrammarDB Tag | UniMorph Feature |
| :--- | :--- |
| `M`&nbsp;Мужчынскі | `MASC`&nbsp;Masculine |
| `F`&nbsp;Жаночы | `FEM`&nbsp;Feminine |
| `N`&nbsp;Ніякі | `NEUT`&nbsp;Neuter |

### Number (Лік)
| GrammarDB Tag | UniMorph Feature |
| :--- | :--- |
| `S`&nbsp;Адзіночны | `SG`&nbsp;Singular |
| `P`&nbsp;Множны | `PL`&nbsp;Plural |

### Person (Асоба)
| GrammarDB Tag | UniMorph Feature |
| :--- | :--- |
| `1`&nbsp;Першая | `1`&nbsp;1st |
| `2`&nbsp;Другая | `2`&nbsp;2nd |
| `3`&nbsp;Трэцяя | `3`&nbsp;3rd |

### Tense (Час)
| GrammarDB Tag | UniMorph Feature |
| :--- | :--- |
| `R`&nbsp;Цяперашні | `PRS`&nbsp;Present |
| `P`&nbsp;Прошлы | `PST`&nbsp;Past |
| `F`&nbsp;Будучы | `FUT`&nbsp;Future |

### Voice (Стан)
| GrammarDB Tag | UniMorph Feature |
| :--- | :--- |
| `A`&nbsp;Незалежны | `ACT`&nbsp;Active |
| `P`&nbsp;Залежны | `PASS`&nbsp;Passive |

## Links

GrammarDB Tags: https://bnkorpus.info/articles/grammardb.html

UniMorph tags: https://unimorph.github.io/doc/unimorph-schema.pdf
