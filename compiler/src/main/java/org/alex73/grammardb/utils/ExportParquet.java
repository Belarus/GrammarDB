package org.alex73.grammardb.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.alex73.grammardb.GrammarDB2;
import org.alex73.grammardb.SetUtils;
import org.alex73.grammardb.structures.Form;
import org.alex73.grammardb.structures.Paradigm;
import org.alex73.grammardb.structures.Variant;
import org.alex73.grammardb.tags.BelarusianTags;
import org.apache.avro.reflect.Nullable;
import org.apache.avro.reflect.ReflectData;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.LocalOutputFile;

/**
 * Export parquet for https://huggingface.co/datasets/alex73/GrammarDB
 */
public class ExportParquet {
    static final Path OUT = Path.of("/tmp/grammardb.parquet");
    static ParquetWriter<Line> writer;

    static BelarusianTags tags = new BelarusianTags();

    public static void main(String[] args) throws Exception {
        GrammarDB2 db = GrammarDB2.initializeFromDir(Path.of("../data").toAbsolutePath().toString());

        Files.deleteIfExists(OUT);
        var builder = AvroParquetWriter.<Line>builder(new LocalOutputFile(OUT));
        builder.withSchema(ReflectData.get().getSchema(Line.class));
        builder.withDataModel(ReflectData.get());
        builder.withCompressionCodec(CompressionCodecName.SNAPPY);
        writer = builder.build();
        for (Paradigm p : db.getAllParadigms()) {
            for (Variant v : p.getVariant()) {
                for (Form f : v.getForm()) {
                    Line line = new Line();
                    line.pdgId = p.getPdgId();
                    line.variantId = v.getId();
                    line.variantLemma = v.getLemma();
                    line.variantType = v.getType() != null ? v.getType().value() : null;
                    line.paradigmOptions = p.getOptions() != null ? p.getOptions().value() : null;
                    line.regulationType = v.getRegulation() != null ? v.getRegulation().name() : null;
                    line.meaning = p.getMeaning();
                    line.theme = p.getTheme();
                    line.tag = SetUtils.tag(p, v, f);
                    line.slounik = collect(v.getSlouniki(), f.getSlouniki());
                    line.pravapis = collect(v.getPravapis(), f.getPravapis());
                    line.formType = f.getType() != null ? f.getType().value() : null;
                    line.formOptions = f.getOptions() != null ? f.getOptions().value() : null;
                    line.form = f.getValue();
                    writer.write(line);
                }
            }
        }
        writer.close();
    }

    static String[] collect(String... values) {
        List<String> result = new ArrayList<>();
        for (String s : values) {
            if (s != null) {
                for (String v : s.split(",")) {
                    if (!v.isBlank()) {
                        result.add(v);
                    }
                }
            }
        }
        return result.toArray(new String[0]);
    }

    public static class Line {
        public int pdgId;
        public String variantId;
        public String variantLemma;
        @Nullable
        public String variantType;
        @Nullable
        public String paradigmOptions;
        @Nullable
        public String regulationType;
        @Nullable
        public String meaning;
        @Nullable
        public String theme;
        public String tag;
        @Nullable
        public String[] slounik;
        @Nullable
        public String[] pravapis;
        @Nullable
        public String formType;
        @Nullable
        public String formOptions;
        public String form;
    }
}
