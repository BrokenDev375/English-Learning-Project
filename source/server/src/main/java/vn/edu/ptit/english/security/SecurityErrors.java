package vn.edu.ptit.english.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import vn.edu.ptit.english.dto.ApiError;

public final class SecurityErrors {
    private SecurityErrors() {}
    public static void write(ObjectMapper json, HttpServletResponse response, int status,
                             String code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getWriter(), new ApiError(code, message));
    }
}
