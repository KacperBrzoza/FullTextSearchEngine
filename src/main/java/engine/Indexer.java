package engine;

import dto.FileDto;
import lombok.extern.slf4j.Slf4j;
import org.apache.beam.sdk.transforms.*;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionView;
import org.apache.beam.sdk.values.KV;

import java.io.FileNotFoundException;
import java.util.*;

@Slf4j
public class Indexer {

    public static void main(String[] args) throws FileNotFoundException {
        PipelineService ps = new PipelineService(args);
        String path = "C:\\Users\\hatoj\\Desktop\\*.txt";

        PCollectionView<List<String>> stopWordsView = ps.loadStopWords("C:\\Programowanie\\FullTextSearchEngine\\src\\main\\resources\\stopwords-pl.txt");

        PCollection<KV<String, String>> texts = ps.loadFiles(path);
        PCollection<KV<String, String>> pathWithLines = ps.emitLines(texts);
        PCollection<KV<String, String>> tokens = ps.tokenize(pathWithLines);
        PCollection<KV<String, String>> filtered = ps.removeStopWords(tokens, stopWordsView);
        PCollection<KV<String, Iterable<KV<String, Double>>>> grouped = ps.tfidf(filtered);
        PCollection<FileDto> dto = ps.toDto(grouped);

        ps.run();
    }

}
