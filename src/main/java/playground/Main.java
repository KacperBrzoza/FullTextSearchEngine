package playground;

import lombok.extern.slf4j.Slf4j;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.values.PCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
public class Main {

    private static Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        PipelineOptions options = PipelineOptionsFactory.fromArgs(args)
                .withValidation()
                .create();

        Pipeline p = Pipeline.create(options);

        PCollection<String> lines = p.apply("ReadPDF", TextIO.read().from("src/main/resources/pdf/RU.17.2025.pdf"));
    }
}
