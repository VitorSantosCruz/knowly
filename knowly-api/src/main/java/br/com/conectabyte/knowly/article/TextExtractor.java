package br.com.conectabyte.knowly.article;

public interface TextExtractor {

    boolean supports(String contentType);

    /** Throws on any extraction failure — callers must not let this propagate uncaught. */
    String extract(byte[] content, String fileName) throws Exception;
}
