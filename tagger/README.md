# Tagger: прадказанне прапушчаных граматычных значэнняў ('X')


Гэтыя дзве нейрасеткі (LightGBM і BiLSTM) прызначаны для аўтаматычнага запаўнення прапушчаных значэнняў у граматычных тэгах словаформ беларускай мовы.
Але яны навучаліся на асобных формах, а не на парадыгмах, таму вызначэнне тэгаў парадыгмы будзе працаваць не найлепшым чынам.
Яны даюць неблагую расстаноўку тэгаў для словаформ, але моцна залежаць ад граматычнай катэгорыі - напрыклад,
для спосаба ўтварэння прыслоўя і пераходнасці дзеяслова іх выкарыстоўваць не атрымліваецца.

Граматычная база на сёння амаль не мае непазначаных тэгаў ('X'), таму гэтыя нейрасеткі не будуць актыўна выкарыстоўвацца,
і прыведзены толькі "каб было".

Модуль для аднаўлення і прадказання прапушчаных значэнняў (`X`) у граматычных тэгах словаформ беларускай мовы з
выкарыстаннем структурызаванай класіфікацыі па дрэве тэгаў (`TagLetter` / `tag_tree.json`).

Падрабязнае апісанне архітэктуры і пастаноўкі задачы знаходзіцца ў [IMPLEMENTATION.md](IMPLEMENTATION.md).

---

## Змест каталога `tagger`

### Асноўныя скрыпты

1. **`train_baseline.py`** — навучанне базавай мадэлі (Baseline).
    - Выкарыстоўвае градыентны бустынг (LightGBM): асобны класіфікатар для кожнай граматычнай катэгорыі (групы) дрэва
      тэгаў з падтрымкай раннега прыпынку (Early Stopping).
    - Прыкметы: n-грамы канчаткаў слова і лемы, а таксама ўжо вядомыя папярэднія катэгорыі тэга.
    - Навучаецца на прыкладах з суфіксным маскіраваннем (`common.masking`).
    - Вынікі:
        - `artifacts/baseline.zip` — захаваныя мадэлі LightGBM (аптымізаваныя да лепшай ітэрацыі) у zip-архіве;
        - `artifacts/baseline_metrics.json` — справаздача па дакладнасці, `best_iteration` і `total_iterations` для кожнай катэгорыі;
        - `artifacts/baseline_loss.csv` — гісторыя страт (`train_loss`, `val_loss`) па кожнай катэгорыі і ітэрацыі;
        - `artifacts/baseline_loss.png` — мульты-панэльны графік крывых навучання і валідацыі з адзнакай кропкі прыпынку.

2. **`train_bilstm.py`** — навучанне адзінай нейрасеціўнай мадэлі (BiLSTM).
    - Архітэктура: сімвальны BiLSTM-энкодэр слова і лемы + GRU-дэкодэр па пазіцыях тэга + агульная лінейная галава з
      маскаваным Softmax (`masked-softmax`) па дазволеных дзецях бягучага вузла дрэва.
    - Навучанне праз teacher forcing за адзін праход па поўным тэгу з вылічэннем страт навучання і валідацыі пасля кожнай эпохі.
    - Вынікі:
        - `artifacts/bilstm.pt` — вагі мадэлі, слоўнікі і гіперпараметры;
        - `artifacts/bilstm_metrics.json` — выніковая дакладнасць і поўная гісторыя эпох;
        - `artifacts/bilstm_loss.csv` — паэпохавая табліца `epoch,train_loss,val_loss` (запісваецца ў рэальным часе);
        - `artifacts/bilstm_loss.png` — графік крывых страт навучання і валідацыі для кантролю перанавучання.

3. **`evaluate_compare.py`** — параўнанне Baseline (LightGBM) і BiLSTM на тэставай выбарцы (`test` split па лемах).
    - Ацэньвае абедзве мадэлі па дзвюх метрыках:
        - **Next-letter accuracy**: дакладнасць прадказання першай невядомай літары пасля вядомага прэфіксу (па асобных
          групах і агульная).
        - **Full-suffix exact-match**: аўта-рэгрэсіўнае аднаўленне ўсіх астатніх літар да канца тэга і праверка на 100%
          супадзенне з эталонам.
    - Вынік захоўваецца ў `artifacts/comparison.json` і выводзіцца ў кансоль.

4. **`set_tags.py`** — інферэнс (прымяненне мадэляў для запаўнення `X`).
    - Чытае табліцу формаў з невызначанымі значэннямі (`fixtag.tsv`), прадказвае прапушчаныя літары абедзвюма мадэлямі
      (LightGBM і BiLSTM), дадае тэкставае апісанне значэнняў і захоўвае вынік у `fixtag-fixed.tsv`.

### Дапаможныя модулі (`common/`)

- `tag_tree.py` — навігацыя па дрэве тэгаў з `tag_tree.json` (Python-аналаг Java-класаў `BelarusianTags`/`TagLetter`).
- `dataset.py` — струменевае чытанне `forms.tsv` з разбіццём па сплітах.
- `splits.py` — дэтэрмінаванае разбіццё па леме (`train` 80%, `val` 10%, `test` 10%), якое выключае ўцечку словаформ
  аднаго слова паміж выбаркамі.
- `masking.py` — генерацыя суфіксных масак для трэніроўкі таблічнага baseline і валідацыі.
- `text.py` — выдаленне сімвалаў націску (`+`, combining acute).
- `vocab.py` — сімвальны алфавіт і табліца токенаў для BiLSTM.
- `baseline_model.py` — фармаванне фіч і абгортка над мадэлямі LightGBM.
- `bilstm_model.py` — PyTorch-рэалізацыя сеткі `TaggerModel`, дэкадаванне па дрэве і серыялізацыя чекпоінта.

---

## Патрабаванні і ўсталяванне

Для працы патрэбны Python 3.10+ (рэкамендуецца 3.13) і залежнасці з `requirements.txt`:

```bash
pip install -r requirements.txt
```

Асноўныя бібліятэкі: `torch`, `lightgbm`, `pandas`, `scikit-learn`, `numpy`, `scipy`, `tqdm`, `matplotlib`.

---

## Звязаныя скрыпты з іншых каталогаў (падрыхтоўка дадзеных)

Перад запускам навучання і інферэнсу неабходна згенераваць уваходныя файлы праз утыліты ў модулі `compiler` (Java):

1. **`compiler/.../ExportToTagger.java`**
    - **Шлях**: `compiler/src/main/java/org/alex73/grammardb/utils/ExportToTagger.java`
    - **Прызначэнне**: чытае слоўнікавыя XML-файлы (`data/*.xml`) і структуру тэгаў `BelarusianTags`, экспартуе:
        - `tagger/tag_tree.json` — поўнае іерархічнае дрэва тэгаў у фармаце JSON;
        - `tagger/forms.tsv` — навучальны датасэт (каля 4.5 млн радкоў): `словаформа \t лема \t поўны_тэг` (толькі формы
          з цалкам вызначанымі тэгамі, без `X` і `+`).
    - **Запуск** (з кораня рэпазіторыя):
      ```bash
      mvn -q exec:java -pl compiler -Dexec.mainClass=org.alex73.grammardb.utils.ExportToTagger
      ```

2. **`compiler/.../ExportToFixTag.java`**
    - **Шлях**: `compiler/src/main/java/org/alex73/grammardb/utils/fix/ExportToFixTag.java`
    - **Прызначэнне**: знаходзіць у `data/*.xml` усе формы, у якіх у тэгу засталіся нявызначаныя значэнні `'X'`, і
      экспартуе іх у `fixtag.tsv` (у корань рэпазіторыя) для далейшай апрацоўкі мадэлямі.
    - **Запуск** (з кораня рэпазіторыя):
      ```bash
      mvn -q exec:java -pl compiler -Dexec.mainClass=org.alex73.grammardb.utils.fix.ExportToFixTag
      ```

---

## Інструкцыя па запуску скрыптоў `tagger`

Усе каманды ніжэй выконваюцца з каталога `tagger/`:

```bash
cd tagger
```

### 1. Навучанне Baseline (LightGBM)

Параметры запуску `train_baseline.py`:
* `--forms` — шлях да формы-датасэту (па змаўчанні: `forms.tsv`);
* `--tag-tree` — шлях да дрэва тэгаў (па змаўчанні: `tag_tree.json`);
* `--out` — шлях да zip-файла для захавання мадэляў (па змаўчанні: `artifacts/baseline.zip`);
* `--limit-train` — ліміт радкоў з `train` спліта (`0` — усе, для тэстаў: напр. `5000`);
* `--limit-eval` — ліміт радкоў з `val`/`test` сплітаў (`0` — усе);
* `--max-positions-per-tag` — максімум выпадковых суфіксных пазіцый на тэг (па змаўчанні: `3`);
* `--n-estimators` — максімальная колькасць дрэў бустынгу на катэгорыю (па змаўчанні: `1000`);
* `--lr` — крок навучання learning rate (па змаўчанні: `0.04`);
* `--early-stopping-rounds` — ранні прыпынак пры адсутнасці паляпшэння на `val` (па змаўчанні: `15`, `0` — адключыць);
* `--seed` — random seed (па змаўчанні: `0`).

Выніковыя артыфакты (захоўваюцца ў бацькоўскім каталогу для `--out`, па змаўчанні `artifacts/`):
* `artifacts/baseline.zip` — мадэлі LightGBM і `manifest.json` у zip-архіве;
* `artifacts/baseline_metrics.json` — справаздача па дакладнасці і кропцы прыпынку бустынгу (`best_iteration`, `total_iterations`, `train_accuracy`);
* `artifacts/baseline_loss.csv` — табліца гісторыі страт (`group,iteration,train_loss,val_loss,best_iteration,total_iterations,is_best`);
* `artifacts/baseline_loss.png` — графік крывых Train/Val Loss па кожнай катэгорыі з адзнакай лепшай ітэрацыі.

Прыклад поўнага навучання:

```bash
python3 train_baseline.py \
    --forms forms.tsv \
    --tag-tree tag_tree.json \
    --out artifacts/baseline.zip
```

Хуткі тэставы прагон (smoke test) на частцы дадзеных:

```bash
python3 train_baseline.py \
    --forms forms.tsv \
    --tag-tree tag_tree.json \
    --out artifacts-test/baseline.zip \
    --limit-train 5000 \
    --limit-eval 1000
```

### 2. Навучанне BiLSTM

Параметры запуску `train_bilstm.py`:
* `--forms` — шлях да `forms.tsv` (па змаўчанні: `forms.tsv`);
* `--tag-tree` — шлях да `tag_tree.json` (па змаўчанні: `tag_tree.json`);
* `--out` — шлях да захаванага чекпоінта (па змаўчанні: `artifacts/bilstm.pt`);
* `--limit-train` / `--limit-eval` — абмежаванне колькасці радкоў для навучання і ацэнкі (`0` — без абмежавання);
* `--epochs` — колькасць эпох (па змаўчанні: `10`);
* `--batch-size` — памер батча (па змаўчанні: `1024`);
* `--lr` — learning rate для аптымізатара Adam (па змаўчанні: `1e-3`);
* `--log-every` — перыядычнасць лагавання крокаў у stderr (па змаўчанні: кожныя `200` крокаў);
* `--vocab-scan-limit` — колькасць радкоў для збору алфавіту (па змаўчанні: `200000`);
* Памеры слаёў: `--char-embed-dim` (`32`), `--word-hidden-dim` (`96`), `--lemma-hidden-dim` (`64`), `--tag-embed-dim` (`24`), `--decoder-hidden-dim` (`128`).

Выніковыя артыфакты (захоўваюцца ў каталогу мадэлі, па змаўчанні `artifacts/`):
* `artifacts/bilstm.pt` — PyTorch-чекпоінт мадэлі;
* `artifacts/bilstm_metrics.json` — выніковая дакладнасць на `val`/`test` і поўная гісторыя эпох;
* `artifacts/bilstm_loss.csv` — табліца страт `epoch,train_loss,val_loss` у рэальным часе;
* `artifacts/bilstm_loss.png` — графік крывых навучання і валідацыі.

Прыклад поўнага навучання (напрыклад, на 10 эпох):

```bash
python3 train_bilstm.py \
    --forms forms.tsv \
    --tag-tree tag_tree.json \
    --out artifacts/bilstm.pt
```

Хуткі тэставы прагон (smoke test):

```bash
python3 train_bilstm.py \
    --forms forms.tsv \
    --tag-tree tag_tree.json \
    --out artifacts-test/bilstm.pt \
    --limit-train 5000 \
    --limit-eval 1000 \
    --epochs 1
```

### 3. Параўнанне і ацэнка мадэляў (`evaluate_compare.py`)

Параметры запуску:
* `--forms` — шлях да `forms.tsv`;
* `--tag-tree` — шлях да `tag_tree.json`;
* `--baseline-model` — шлях да zip-файла мадэляў LightGBM (па змаўчанні: `artifacts/baseline.zip`);
* `--bilstm-checkpoint` — чекпоінт BiLSTM (па змаўчанні: `artifacts/bilstm.pt`);
* `--split` — выбарка для тэставання: `test` (па змаўчанні), `val`, альбо `train`;
* `--limit` — ліміт радкоў для ацэнкі (`0` — усе);
* `--max-positions-per-tag` — колькасць выпадковых зрэзаў на тэг (па змаўчанні: `2`);
* `--out` — шлях для захавання вынікаў (па змаўчанні: `artifacts/comparison.json`);
* `--seed` — random seed (`0`).

Прыклад запуску:

```bash
python3 evaluate_compare.py \
    --forms forms.tsv \
    --tag-tree tag_tree.json \
    --baseline-model artifacts/baseline.zip \
    --bilstm-checkpoint artifacts/bilstm.pt \
    --split test \
    --out artifacts/comparison.json
```

Абмежаваны прагон (напрыклад, першыя 2000 радкоў з `test`):

```bash
python3 evaluate_compare.py \
    --forms forms.tsv \
    --tag-tree tag_tree.json \
    --baseline-model artifacts/baseline.zip \
    --bilstm-checkpoint artifacts/bilstm.pt \
    --split test \
    --limit 2000
```

### 4. Запаўненне невядомых значэнняў 'X' (`set_tags.py`)

Параметры запуску:
* `--input` — уваходны TSV з нявызначанымі тэгамі (па змаўчанні: `../fixtag.tsv`);
* `--output` — выхадны выніковы TSV (па змаўчанні: `../fixtag-fixed.tsv`);
* `--tag-tree` — шлях да `tag_tree.json` (па змаўчанні: `tag_tree.json`);
* `--baseline-model` — шлях да zip-файла baseline мадэляў (па змаўчанні: `artifacts/baseline.zip`);
* `--bilstm-checkpoint` — чекпоінт BiLSTM (па змаўчанні: `artifacts/bilstm.pt`);
* `--batch-size` — памер батча пры аўта-рэгрэсіўным BiLSTM інферэнсе (па змаўчанні: `1000`).

Прыклад запуску:

```bash
python3 set_tags.py \
    --input ../fixtag.tsv \
    --output ../fixtag-fixed.tsv \
    --tag-tree tag_tree.json \
    --baseline-model artifacts/baseline.zip \
    --bilstm-checkpoint artifacts/bilstm.pt \
    --batch-size 1000
```

---

## Поўны працоўны працэс (Workflow)

```
data/*.xml
    │
    ├─► [ExportToTagger.java] ──► tagger/forms.tsv + tagger/tag_tree.json
    │                                │
    │                                ├──► train_baseline.py ──► artifacts/baseline.zip
    │                                │                          artifacts/baseline_metrics.json
    │                                │                          artifacts/baseline_loss.csv
    │                                │                          artifacts/baseline_loss.png
    │                                │
    │                                ├──► train_bilstm.py   ──► artifacts/bilstm.pt
    │                                │                          artifacts/bilstm_metrics.json
    │                                │                          artifacts/bilstm_loss.csv
    │                                │                          artifacts/bilstm_loss.png
    │                                │
    │                                └──► evaluate_compare.py ◄── artifacts/comparison.json
    │
    └─► [ExportToFixTag.java] ──► fixtag.tsv
                                     │
                                     ▼
                                set_tags.py ◄── artifacts/baseline.zip + artifacts/bilstm.pt
                                     │
                                     ▼
                                fixtag-fixed.tsv (прадказаныя тэгі і апісанні)
```

