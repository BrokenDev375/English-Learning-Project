package vn.edu.ptit.english.exception;

import java.util.LinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import vn.edu.ptit.english.dto.ApiError;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException ex) {
        return ResponseEntity.status(ex.status()).body(new ApiError(ex.code(), ex.getMessage(),ex.fieldErrors()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        var fields = new LinkedHashMap<String, String>();
        ex.getBindingResult().getFieldErrors().forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", "Vui lòng kiểm tra thông tin đã nhập.", fields));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> malformed() {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", "Dữ liệu yêu cầu không đúng định dạng."));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> uploadSize(){return ResponseEntity.status(413).body(new ApiError("FILE_TOO_LARGE","File tối đa 5 MiB."));}
    @ExceptionHandler({MultipartException.class,MissingServletRequestPartException.class,MissingServletRequestParameterException.class})
    ResponseEntity<ApiError> multipart(){return ResponseEntity.badRequest().body(new ApiError("VALIDATION_FAILED","Cần gửi một file và mã yêu cầu hợp lệ."));}
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> invalidParameter() {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", "Mã tài nguyên không đúng định dạng."));
    }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> method() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(new ApiError("METHOD_NOT_ALLOWED", "Phương thức không được hỗ trợ."));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> conflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("DATA_CONFLICT", "Dữ liệu đã thay đổi hoặc bị trùng. Vui lòng tải lại."));
    }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> database(DataAccessException ex) {
        log.error("Database request failed", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiError("DATABASE_UNAVAILABLE", "Chưa truy cập được dữ liệu. Vui lòng thử lại sau."));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex) {
        log.error("Unhandled request failure", ex);
        return ResponseEntity.internalServerError().body(new ApiError("INTERNAL_ERROR", "Yêu cầu chưa được xử lý. Vui lòng thử lại sau."));
    }
}
