package br.com.conectabyte.knowly.article;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.util.Set;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;

/** PDF text extraction and image OCR — Tika's AutoDetectParser dispatches to the right parser. */
@Component
public class TikaTextExtractor implements TextExtractor {

    private static final Set<String> SUPPORTED_CONTENT_TYPES =
            Set.of("application/pdf", "image/png", "image/jpeg");

    @Override
    public boolean supports(String contentType) {
        return SUPPORTED_CONTENT_TYPES.contains(contentType);
    }

    @Override
    public String extract(byte[] content, String fileName) throws Exception {
        AutoDetectParser parser = new AutoDetectParser();
        StringWriter writer = new StringWriter();
        BodyContentHandler handler = new BodyContentHandler(writer);

        try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {
            parser.parse(input, handler, new Metadata(), new ParseContext());
        }

        return writer.toString().trim();
    }
}
