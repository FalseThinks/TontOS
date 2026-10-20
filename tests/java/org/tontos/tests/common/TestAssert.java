package org.tontos.tests.common;

public class TestAssert {

    private static int totalTests = 0;
    private static int passedTests = 0;

    @FunctionalInterface
    public interface TestCase {
        void run() throws Throwable;
    }

    public static void runTest(String name, TestCase test) {
        totalTests++;
        try {
            test.run();
            passedTests++;
            System.out.println(String.format("    [PASS] %s", name));
        } catch (Throwable t) {
            System.err.println(String.format("    [FAIL] %s: %s", name, t.getMessage()));
            t.printStackTrace(System.err);
        }
    }

    public static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }

    public static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError(message + " (Expected: " + expected + ", Actual: " + actual + ")");
    }

    public static void assertNotNull(Object obj, String message) {
        if (obj == null) {
            throw new AssertionError(message + " (Expected non-null)");
        }
    }

    public static int getTotalTests() {
        return totalTests;
    }

    public static int getPassedTests() {
        return passedTests;
    }
}
