package br.com.conectabyte.knowly.article.exception;

import br.com.conectabyte.knowly.article.dto.ArticleErrorResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ArticleExceptionHandler {

    @ExceptionHandler(UnsupportedFileTypeException.class)
    public ResponseEntity<ArticleErrorResponseDto> handleUnsupportedFileType(
            UnsupportedFileTypeException ex) {
        return ResponseEntity.badRequest()
                .body(new ArticleErrorResponseDto("UNSUPPORTED_FILE_TYPE"));
    }

    @ExceptionHandler(FileTooLargeException.class)
    public ResponseEntity<ArticleErrorResponseDto> handleFileTooLarge(FileTooLargeException ex) {
        return ResponseEntity.badRequest().body(new ArticleErrorResponseDto("FILE_TOO_LARGE"));
    }

    @ExceptionHandler(ArticleNotFoundException.class)
    public ResponseEntity<ArticleErrorResponseDto> handleArticleNotFound(
            ArticleNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ArticleErrorResponseDto("ARTICLE_NOT_FOUND"));
    }
}
