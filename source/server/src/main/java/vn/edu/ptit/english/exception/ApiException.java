package vn.edu.ptit.english.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final java.util.Map<String,String> fieldErrors;

    public ApiException(HttpStatus status, String code, String message) {
        this(status,code,message,java.util.Map.of());
    }
    public ApiException(HttpStatus status,String code,String message,java.util.Map<String,String> fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = java.util.Map.copyOf(fieldErrors);
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    public java.util.Map<String,String> fieldErrors(){return fieldErrors;}
}
