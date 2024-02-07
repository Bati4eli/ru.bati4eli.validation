package ru.bati4eli;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Validate all fields of your class in one go!
 *
 * @param <T> the class type which you want to validate
 * @author Ivanov.Sergey.V@russianpost.ru
 */
public class Validation<T> {
    private T object;
    private String description = "";
    private String fieldPrefix = "";
    private Map<String, List<String>> violations = new HashMap<>();

    public static final int ZERO = 0;
    public static final String FORMAT_FOR_EACH_FIELD = "%s %s";
    public static final String DESC_FOR_SIZE = "must contain only <%d> object(s)";
    public static final String DESC_MUST_BE_NULL = "must be NULL";
    public static final String DESC_MUST_NOT_BE_NULL = "must not be NULL";
    public static final String DESC_MUST_NOT_BE_EMPTY = "must not be EMPTY";
    public static final String DESC_NOT_EQUALS = "doesn't equal to '%s'";
    public static final String DESC_NOT_EQUALS_ANY = "actual value `%s` is not equal to any item of %s";
    public static final String DESC_NOT_ALLOWED = "is not allowed";
    public static final String DESC_ZERO_OR_NULL = "must be 0 or NULL";

    private Validation() {
    }

    private Validation(T obj, String fieldPrefix, String description, Map<String, List<String>> violations) {
        this.object = obj;
        this.fieldPrefix = fieldPrefix + ".";
        this.description = description;
        this.violations = violations;
    }

    /* PUBLIC METHODS FOR INIT VALIDATION AND GET RESULTS */
    public static <T> Validation<T> of(T obj) {
        Validation<T> newInstance = new Validation<>();
        newInstance.object = obj;
        return newInstance;
    }

    public boolean hasViolations() {
        return !violations.isEmpty();
    }

    public Map<String, List<String>> getViolations() {
        return violations;
    }

    public String getFullErrorMessage() {
        if (hasViolations()) {
            final List<String> collection = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : violations.entrySet()) {
                final String field = entry.getKey();
                final List<String> errors = entry.getValue();
                for (String error : errors) {
                    String msg = String.format(FORMAT_FOR_EACH_FIELD, field, error);
                    collection.add(msg);
                }
            }
            return description + collection;
        }
        return "";
    }

    public void orElseThrow(Function<String, Exception> exceptionProcessor) throws Exception {
        if (hasViolations()) {
            throw exceptionProcessor.apply(this.getFullErrorMessage());
        }
    }

    public void orElseThrow() throws ValidationException {
        if (hasViolations()) {
            throw ValidationException.of(violations);
        }
    }

    /**
     * Method for moving to the next level
     */
    public <B> Validation<T> map(String fieldPath, Function<T, B> extractor, Consumer<Validation<B>> mappedValidator) {
        B checkingObject = safeExtract(extractor);
        final String path = isNotEmpty(fieldPrefix) ? fieldPrefix + fieldPath : fieldPath;

        if (Objects.isNull(checkingObject)) {
            this.nonNull(path, extractor::apply);
            return this;
        }

        final Validation<B> bValidation = new Validation<>(checkingObject, path, description, violations);
        mappedValidator.accept(bValidation);
        mergeMaps(this.violations, bValidation.getViolations());
        return this;
    }

    /* PUBLIC METHODS FOR VALIDATION */

    public Validation<T> description(String description) {
        this.description = description;
        return this;
    }

    public <B> Validation<T> equals(String fieldPath, B expectedValue, Function<T, B> extractor) {
        final String msg = String.format(DESC_NOT_EQUALS, expectedValue);
        return validate(fieldPath, msg, obj -> Objects.equals(expectedValue, safeExtract(extractor)));
    }

    public Validation<T> stringNotEmpty(String fieldPath, Function<T, String> extractor) {
        return validate(fieldPath, DESC_MUST_NOT_BE_EMPTY, obj -> isNotEmpty(safeExtract(extractor)));
    }

    public Validation<T> nonNull(String fieldPath, Function<T, Object> extractor) {
        return validate(fieldPath, DESC_MUST_NOT_BE_NULL, obj -> Objects.nonNull(safeExtract(extractor)));
    }

    public Validation<T> isNull(String fieldPath, Function<T, Object> extractor) {
        return validate(fieldPath, DESC_MUST_BE_NULL, obj -> !Objects.nonNull(safeExtract(extractor)));
    }

    public Validation<T> isZeroOrNull(String fieldPath, Function<T, Number> extractor) {
        Number checkingNumber = safeExtract(extractor);
        if (Objects.isNull(checkingNumber)) {
            return this;
        }
        return validate(fieldPath, DESC_ZERO_OR_NULL, num -> extractor.apply(num).intValue() == ZERO);
    }

    public Validation<T> sizeEquals(String fieldPath, int expectedSize, Function<T, Collection> extractor) {
        if (Objects.isNull(safeExtract(extractor))) {
            return nonNull(fieldPath, extractor::apply);
        }
        final String format = String.format(DESC_FOR_SIZE, expectedSize);
        return validate(fieldPath, format, obj -> extractor.apply(obj).size() == expectedSize);
    }

    public Validation<T> collectionIsNotEmpty(String fieldPath, Function<T, Collection> extractor) {
        if (Objects.isNull(safeExtract(extractor))) {
            return nonNull(fieldPath, extractor::apply);
        }
        return validate(fieldPath, DESC_MUST_NOT_BE_EMPTY, obj -> !extractor.apply(obj).isEmpty());
    }

    public Validation<T> collectionIsEmpty(String fieldPath, Function<T, Collection> extractor) {
        final Collection validateValue = safeExtract(extractor);
        if (Objects.isNull(validateValue)) {
            return this;
        }
        return validate(fieldPath, DESC_NOT_ALLOWED, obj -> extractor.apply(obj).isEmpty());
    }

    /**
     * validating whether a value matches one of the group elements
     *
     * @param fieldPath      - name for the field / path
     * @param extractor      - function for getting the field value
     * @param expectedValues - list of valid values
     */
    public <ParamType> Validation<T> equalsAny(String fieldPath, Function<T, ParamType> extractor, ParamType... expectedValues) {
        ParamType fieldValue = safeExtract(extractor);
        final String msg = String.format(DESC_NOT_EQUALS_ANY, fieldValue, Arrays.toString(expectedValues));
        return validate(fieldPath, msg, obj -> Arrays.asList(expectedValues).contains(fieldValue));
    }

    public Validation<T> validate(String fieldPath, String msg, Predicate<T> condition) {
        if (object == null) {
            return this;
        }
        if (!safeConditionTest(condition)) {
            final String fullFieldPath = fieldPrefix + fieldPath;
            List<String> errors = violations.get(fullFieldPath);
            if (errors == null) {
                errors = new ArrayList<>();
            }
            errors.add(msg);
            violations.put(fullFieldPath, errors);
        }
        return this;
    }

    private boolean safeConditionTest(Predicate<T> condition) {
        try {
            return condition.test(object);
        } catch (Exception ignored) {
            return false;
        }
    }

    private <B> B safeExtract(Function<T, B> extractor) {
        try {
            return extractor.apply(object);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean isNotEmpty(String str) {
        return str != null && !str.equals("");
    }

    public static void mergeMaps(Map<String, List<String>> map1, Map<String, List<String>> map2) {
        for (Map.Entry<String, List<String>> entry : map2.entrySet()) {
            String key = entry.getKey();
            List<String> values = entry.getValue();

            if (map1.containsKey(key)) {
                List<String> existingValues = map1.get(key);
                existingValues.addAll(values);
            } else {
                map1.put(key, values);
            }
        }
    }


}
