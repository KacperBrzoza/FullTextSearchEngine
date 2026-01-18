package service;

import dto.FileDto;
import lombok.extern.slf4j.Slf4j;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.FileIO;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.io.fs.ResourceId;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.*;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionView;
import org.apache.beam.sdk.values.TypeDescriptors;

import java.io.Serializable;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Klasa do wykonywania procesów na pipeline
 */
@Slf4j
public class PipelineService {

    /**
     * Metoda wczytuje wszystkie pliki pasujące do podanego path
     * @param path ścieżka do plików
     * @return PCollection(ścieżka, zawartość)
     */
    public static PCollection<KV<String, String>> loadFiles(Pipeline p, String path){
        return p.apply("MatchFiles", FileIO.match().filepattern(path))
                .apply("ReadMatches", FileIO.readMatches())
                .apply("ToKV(path, content)", ParDo.of(new DoFn<FileIO.ReadableFile, KV<String, String>>() {
                    @ProcessElement
                    public void processElement(ProcessContext c) {
                        FileIO.ReadableFile f = c.element();
                        if(f != null){
                            ResourceId rid = f.getMetadata().resourceId();
                            String path1 = rid.toString();

                            try (var in = Channels.newInputStream(f.open())) {
                                String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                                c.output(KV.of(path1, content));
                            }
                            catch (Exception e){
                                log.error("Nie udało się otworzyć pliku: {}", f);
                            }
                        }

                    }
                }));
    }

    /**
     * Metoda ładuje stop wordsy z pliku o zadanej ścieżce
     * @return zwraca kolekcję stop wordsów jako widok
     */
    public static PCollectionView<List<String>> loadStopWords(Pipeline p, String path){
        PCollection<String> stopWords = p.apply("LoadStopWords", TextIO.read().from(path))
                .apply(MapElements.via(new SimpleFunction<>() {
                    @Override
                    public String apply(String s) {
                        return s.trim().toUpperCase();
                    }
                }));

        return stopWords.apply(View.asList());
    }

    /**
     * Metoda dzieli teksty na linie i emituje pary (ścieżka, linia z tekstu)
     */
    public static PCollection<KV<String, String>> emitLines(PCollection<KV<String, String>> texts){
        return texts.apply("GetLines",
                ParDo.of(new DoFn<KV<String, String>, KV<String, String>>(){
                    @ProcessElement
                    public void process(@Element KV<String, String> fileWithContent,
                                        OutputReceiver<KV<String, String>> out) {
                        String key = fileWithContent.getKey();
                        String content = fileWithContent.getValue();
                        if(content != null){
                            List<String> lines = content.lines().toList();
                            for(String line : lines){
                                out.output(KV.of(key, line));
                            }
                        }
                    }
                })
        );
    }

    /**
     * Metoda przyjmująca pary (klucz, linia tekstu) i zwracająca pary (klucz, token)
     */
    public static PCollection<KV<String, String>> tokenize(PCollection<KV<String, String>> pathWithLines){
        return pathWithLines.apply("Tokenize",
                ParDo.of(new DoFn<KV<String, String>, KV<String, String>>() {
                    @ProcessElement
                    public void process(@Element KV<String, String> doc, OutputReceiver<KV<String, String>> out) {
                        String key = doc.getKey();
                        String line = doc.getValue();
                        if(line != null){
                            for (String word : line.split("[^\\p{L}]+")) {
                                if (!word.isBlank()) {
                                    out.output(KV.of(key, word.toUpperCase()));
                                }
                            }
                        }
                    }
                })
        );
    }

    /**
     * Metoda do odfiltrowywania tych tokenów, które są stop wordsami
     */
    public static PCollection<KV<String, String>> removeStopWords(PCollection<KV<String, String>> tokens, PCollectionView<List<String>> stopWordsView){
        return tokens.apply("RemoveStopWords",
                ParDo.of(new DoFn<KV<String, String>, KV<String, String>>() {
                    @ProcessElement
                    public void process(@Element KV<String, String> pathWithToken, OutputReceiver<KV<String, String>> out,
                                        ProcessContext ctx) {
                        List<String> stop = ctx.sideInput(stopWordsView);
                        String key = pathWithToken.getKey();
                        String token = pathWithToken.getValue();
                        if (stop != null && !stop.contains(token)) {
                            out.output(KV.of(key, token));
                        }
                    }
                }).withSideInputs(stopWordsView)
        );
    }

    /**
     * Zbiorcza metoda do policzenia tfidf
     * @param filtered pary (ścieżka do pliku, token z pliku)
     * @return pary (ścieżka do pliku, pary tokenów z wynikiem występowania w plikach)
     */
    public static PCollection<KV<String, Iterable<KV<String, Double>>>> tfidf(PCollection<KV<String, String>> filtered){
        PCollection<KV<String, Long>> tf = tf(filtered);
        PCollection<KV<String, Long>> df = df(filtered);

        //Liczba dokumentów
        PCollectionView<Long> docCount = filtered
                .apply("ExtractDocs", Keys.create())
                .apply("DistinctDocs", Distinct.create())
                .apply("CountDocs", Count.globally())
                .apply(View.asSingleton());

        PCollectionView<Map<String, Double>> idf = idf(df, docCount);
        PCollection<KV<String, KV<String, Long>>> tfByTerm = tfByTerm(tf);
        PCollection<KV<String, Double>> tfidf = tfidf(tfByTerm, idf);

        PCollection<KV<String, KV<String, Double>>> byPath = tfidf.apply("RekeyToPath",
                MapElements.into(TypeDescriptors.kvs(TypeDescriptors.strings(),
                                TypeDescriptors.kvs(TypeDescriptors.strings(), TypeDescriptors.doubles()))
                ).via(kv -> {
                    String[] parts = kv.getKey().split("###", 2);
                    String key = parts[0];
                    String token = parts[1];
                    return KV.of(key, KV.of(token, kv.getValue()));
                })
        );

        return byPath.apply("GroupByPath", GroupByKey.create());
    }

    /**
     * Metoda pomocnicza do obliczenia TF (częstość tokenów w pliku).
     * @param filtered odfiltrowane tokeny
     * @return pary (ścieżka###token, liczba wystąpień tokenu w pliku)
     */
    private static PCollection<KV<String, Long>> tf(PCollection<KV<String, String>> filtered){
        return filtered.apply("Pair(doc,term)",
                        MapElements.into(TypeDescriptors.kvs(TypeDescriptors.strings(), TypeDescriptors.longs()))
                                .via(kv -> KV.of(kv.getKey() + "###" + kv.getValue(), 1L)))
                .apply("TFPer(doc,term)", Sum.longsPerKey());
    }

    /**
     * Metoda pomocnicza do obliczenia DF (ilości przypadków, że token wystąpił w jakimś pliku).
     * @param filtered odfiltrowane tokeny
     * @return pary (token, liczba dokumentów, w których wystąpił)
     */
    private static PCollection<KV<String, Long>> df(PCollection<KV<String, String>> filtered){
        PCollection<KV<String, String>> termDoc = filtered.apply("term->doc",
                        MapElements.into(TypeDescriptors.kvs(TypeDescriptors.strings(), TypeDescriptors.strings()))
                                .via(kv -> KV.of(kv.getValue(), kv.getKey())))
                .apply("DistinctTerm-doc", Distinct.create());

        return termDoc.apply("DFPerTerm", Count.perKey());
    }

    /**
     * Metoda pomocnicza do obliczenia IDF (log(liczba dokumentów / wartość df)).<br>
     * Jeśli IDF jest wysokie, to znaczy, że token pojawia się w wielu dokumentach.
     * @param df ilości przypadków, że token wystąpił w jakimś pliku
     * @param docCount ilość dokumentów
     * @return widok z mapą token -> wartość idf
     */
    private static PCollectionView<Map<String, Double>> idf(PCollection<KV<String, Long>> df, PCollectionView<Long> docCount){
        return df.apply("ComputeIDF", ParDo.of(new DoFn<KV<String,Long>, KV<String, Double>>() {
            @ProcessElement
            public void process(@Element KV<String,Long> kv, OutputReceiver<KV<String, Double>> out, ProcessContext ctx) {
                long dfVal = kv.getValue();
                long N = ctx.sideInput(docCount);
                double idfVal = Math.log((double)N / (double)dfVal);
                out.output(KV.of(kv.getKey(), idfVal)); // term -> idf
            }
        }).withSideInputs(docCount)).apply(View.asMap());
    }

    /**
     * Metoda pomocnicza do zmiany klucza
     * @param tf pary (ścieżka###token, liczba wystąpień tokenu w pliku)
     * @return pary (token, (ścieżka, liczba wystąpień tokenu w pliku))
     */
    private static PCollection<KV<String, KV<String, Long>>> tfByTerm(PCollection<KV<String,Long>> tf){
        return tf.apply("RekeyTF", ParDo.of(new DoFn<KV<String,Long>, KV<String,KV<String,Long>>>() {
            @ProcessElement
            public void process(@Element KV<String,Long> kv, OutputReceiver<KV<String,KV<String,Long>>> out) {
                String[] parts = kv.getKey().split("###");
                String doc = parts[0];
                String term = parts[1];
                out.output(KV.of(term, KV.of(doc, kv.getValue())));
            }
        }));
    }

    /**
     * Metoda pomocnicza do obliczenia właściwego tfidf (wagi słów)
     * @param tfByTerm pary (token, (ścieżka, liczba wystąpień tokenu w pliku))
     * @param idf widok z mapą token -> wartość idf
     * @return (ścieżka###token, wartość tfidf)
     */
    private static PCollection<KV<String, Double>> tfidf(PCollection<KV<String, KV<String, Long>>> tfByTerm, PCollectionView<Map<String, Double>> idf){
        return tfByTerm.apply("TFIDF", ParDo.of(new DoFn<KV<String,KV<String,Long>>, KV<String,Double>>() {
            @ProcessElement
            public void process(@Element KV<String,KV<String,Long>> kv, OutputReceiver<KV<String,Double>> out,
                                ProcessContext ctx) {

                String term = kv.getKey();
                String doc = kv.getValue().getKey();
                long tfVal = kv.getValue().getValue();

                Map<String, Double> idfMap = ctx.sideInput(idf);

                double idfVal = idfMap.get(term);
                double tfidfVal = tfVal * idfVal;
                out.output(KV.of(doc + "###" + term, tfidfVal));
            }
        }).withSideInputs(idf));
    }

    /**
     * Metoda generująca DTO obsłużonego pliku. W procesie tworzenia obiektu transportowego przydziela plikowi unikalne ID
     * @param grouped zebrane dane, gotowe do zapisu
     * @return obiekty transportowe metadanych dokumentu
     */
    public static PCollection<FileDto> toDto(PCollection<KV<String, Iterable<KV<String, Double>>>> grouped){
        return grouped.apply(ParDo.of(new DoFn<KV<String, Iterable<KV<String, Double>>>, FileDto>(){
            @ProcessElement
            public void process(@Element KV<String, Iterable<KV<String, Double>>> kv,
                                OutputReceiver<FileDto> out) {
                FileDto fileDto = new FileDto();
                fileDto.setId(UUID.randomUUID());
                fileDto.setPath(kv.getKey());
                Map<String, Double> tfidfPerFile = new HashMap<>();
                for(KV<String, Double> tokenWithScore : kv.getValue()){
                    tfidfPerFile.put(tokenWithScore.getKey(), tokenWithScore.getValue());
                }
                fileDto.setFrequencyDict(tfidfPerFile);
                out.output(fileDto);
            }
        }));
    }
}
