package com.tradingreporting.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Writes {@code {"detail": "..."}} error bodies from within the servlet filter chain, matching
 * the FastAPI backend's error envelope for authentication failures raised before the
 * DispatcherServlet's exception handling takes over. */
@Component
public class ApiErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ApiErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, int status, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setHeader("WWW-Authenticate", "Bearer");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of("detail", detail)));
    }
}
