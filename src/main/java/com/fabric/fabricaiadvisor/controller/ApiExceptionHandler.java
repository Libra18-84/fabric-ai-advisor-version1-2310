package com.fabric.fabricaiadvisor.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.Map;

@RestControllerAdvice(basePackages = "com.fabric.fabricaiadvisor.controller")
public class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> databaseUnavailable(DataAccessException exception) {
        LOG.warn("Không đọc được SQLite: {}", exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "status", "error", "code", "DATABASE_UNAVAILABLE",
                "error", "Không đọc được dữ liệu SQLite."));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of(
                "status", "error", "code", "INVALID_REQUEST",
                "error", "Yêu cầu thiếu thông tin hoặc có kiểu dữ liệu không hợp lệ."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> invalidArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of(
                "status", "error", "code", "INVALID_REQUEST",
                "error", "Điều kiện đầu vào không hợp lệ."));
    }
}
