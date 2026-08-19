package com.procurement.engine.revalidation.model;

/**
 * Detailed result of an individual pre-purchase revalidation check.
 */
public class RevalidationCheckDto {

    private final String checkName;
    private final String expected;
    private final String actual;
    private final boolean passed;
    private final String message;

    public RevalidationCheckDto(String checkName, String expected, String actual, boolean passed, String message) {
        this.checkName = checkName;
        this.expected = expected;
        this.actual = actual;
        this.passed = passed;
        this.message = message;
    }

    public static RevalidationCheckDto pass(String checkName, String expected, String actual, String message) {
        return new RevalidationCheckDto(checkName, expected, actual, true, message);
    }

    public static RevalidationCheckDto fail(String checkName, String expected, String actual, String message) {
        return new RevalidationCheckDto(checkName, expected, actual, false, message);
    }

    public String getCheckName() { return checkName; }
    public String getExpected() { return expected; }
    public String getActual() { return actual; }
    public boolean isPassed() { return passed; }
    public String getMessage() { return message; }
}
