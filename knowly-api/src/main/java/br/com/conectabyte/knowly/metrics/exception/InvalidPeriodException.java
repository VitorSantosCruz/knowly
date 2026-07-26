package br.com.conectabyte.knowly.metrics.exception;

public class InvalidPeriodException extends RuntimeException {

    public InvalidPeriodException() {
        super("Invalid period value");
    }
}
