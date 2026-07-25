package br.com.conectabyte.knowly.article;

import java.util.Set;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

@Component
public class AudioTranscriptionExtractor implements TextExtractor {

    private static final Set<String> SUPPORTED_CONTENT_TYPES =
            Set.of("audio/mpeg", "audio/wav", "audio/mp4");

    private final TranscriptionModel transcriptionModel;

    public AudioTranscriptionExtractor(TranscriptionModel transcriptionModel) {
        this.transcriptionModel = transcriptionModel;
    }

    @Override
    public boolean supports(String contentType) {
        return SUPPORTED_CONTENT_TYPES.contains(contentType);
    }

    @Override
    public String extract(byte[] content, String fileName) throws Exception {
        ByteArrayResource resource =
                new ByteArrayResource(content) {
                    @Override
                    public String getFilename() {
                        return fileName;
                    }
                };

        var response = transcriptionModel.call(new AudioTranscriptionPrompt(resource));

        return response.getResult().getOutput().trim();
    }
}
