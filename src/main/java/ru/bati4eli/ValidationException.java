package ru.bati4eli;

import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class ValidationException extends Exception {

    private Map<String, List<String>> violations = new HashMap<>();

    private ValidationException(Map<String, List<String>> violations){
        this.violations = violations;
    }

    public static ValidationException of(Map<String, List<String>> violations){
        return new ValidationException(violations);
    }
}