package engine;

import dto.FileDto;
import lombok.extern.slf4j.Slf4j;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.*;
import org.apache.beam.sdk.values.PCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

@Slf4j
public class Indexer {

    private static Logger logger = LoggerFactory.getLogger(Indexer.class);

    public static void main(String[] args) throws FileNotFoundException {
        PipelineOptions options = PipelineOptionsFactory.fromArgs(args).create();
        Pipeline p = Pipeline.create(options);
        String path = "C:\\Users\\Kacper\\Desktop\\shortcuts.txt";
        PCollection<String> text = p.apply(TextIO.read().from(path));

        PCollection<String> textWithoutStopWords = text.apply(Filter.by(  //TODO wywalać stop wordsy
                        (SerializableFunction<String, Boolean>) input1 -> input1.length() > 2)
                );

        PCollection<FileDto> dtos = textWithoutStopWords.apply("Log", ParDo.of(new DoFn<String, FileDto>() {
                    @ProcessElement
                    public void processElement(@Element String word, OutputReceiver<FileDto> out) throws Exception {
                        FileDto dto = new FileDto();
                        dto.setId(UUID.randomUUID());
                        dto.setPath(path);
                        dto.setFrequencyDict(new HashMap<>());
                        out.output(dto);
                    }
                }));

        p.run();
//        Set<String> paths = FileSystemScanner.getAllFilesFromPath(args[0]);
//        for(String path : paths){
//            PCollection<String> lines = p.apply("ReadTXT", TextIO.read().from(path));
//        }

    }

    private String[] split_file_content(String fileContent){
        return fileContent.split(" ");
    }
}
