package engine;

import dto.FileDto;
import lombok.extern.slf4j.Slf4j;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionView;
import org.apache.beam.sdk.values.KV;
import service.PipelineService;

import java.io.FileNotFoundException;
import java.util.*;

@Slf4j
public class Indexer {

    public static void main(String[] args) throws FileNotFoundException {
        PipelineOptions pipelineOptions = PipelineOptionsFactory.fromArgs(args).withValidation().create();
        Pipeline p = Pipeline.create(pipelineOptions);
        String path = "C:\\Users\\hatoj\\Desktop\\*.txt";

        PCollectionView<List<String>> stopWordsView = PipelineService.loadStopWords(p, "C:\\Programowanie\\FullTextSearchEngine\\src\\main\\resources\\stopwords-pl.txt");

        PCollection<KV<String, String>> texts = PipelineService.loadFiles(p, path);
        PCollection<KV<String, String>> pathWithLines = PipelineService.emitLines(texts);
        PCollection<KV<String, String>> tokens = PipelineService.tokenize(pathWithLines);
        PCollection<KV<String, String>> filtered = PipelineService.removeStopWords(tokens, stopWordsView);
        PCollection<KV<String, Iterable<KV<String, Double>>>> grouped = PipelineService.tfidf(filtered);
        PCollection<FileDto> dto = PipelineService.toDto(grouped);

        p.run();
    }

}
