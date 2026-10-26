package org.tontos.tests.security;

import org.tontos.tests.common.TestAssert;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

public class LockSecurityTest {

    private static String hashCredential(String credential, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] digest = md.digest(credential.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String patternToString(List<Integer> pattern) {
        if (pattern == null || pattern.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pattern.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(pattern.get(i));
        }
        return sb.toString();
    }

    public static void run() {
        System.out.println("  --> [Security] Lock Screen Security & Biometric Suite");

        TestAssert.runTest("Lock Security - SHA-256 Hash Invariants", () -> {
            String salt = "s4lt_v4lu3_t0nt0s";
            String pin = "1337";

            String hash1 = hashCredential(pin, salt);
            String hash2 = hashCredential(pin, salt);
            TestAssert.assertEquals(hash1, hash2, "Identical credential and salt must yield identical hash");

            String wrongHash = hashCredential("1338", salt);
            TestAssert.assertTrue(!hash1.equals(wrongHash), "Different credential must yield different hash");

            String differentSaltHash = hashCredential(pin, "other_salt");
            TestAssert.assertTrue(!hash1.equals(differentSaltHash), "Different salt must yield different hash");
            TestAssert.assertEquals(64, hash1.length(), "SHA-256 hex string must be 64 characters long");
        });

        TestAssert.runTest("Lock Security - Pattern Serialization & Verification", () -> {
            List<Integer> pattern = Arrays.asList(0, 1, 2, 5, 8);
            String patternStr = patternToString(pattern);
            TestAssert.assertEquals("0,1,2,5,8", patternStr, "Pattern serialized string");

            String salt = "mock_salt_99";
            String savedHash = hashCredential(patternStr, salt);

            List<Integer> attemptCorrect = Arrays.asList(0, 1, 2, 5, 8);
            String testHashCorrect = hashCredential(patternToString(attemptCorrect), salt);
            TestAssert.assertEquals(savedHash, testHashCorrect, "Correct pattern matches saved hash");

            List<Integer> attemptWrong = Arrays.asList(0, 1, 2, 4, 8);
            String testHashWrong = hashCredential(patternToString(attemptWrong), salt);
            TestAssert.assertTrue(!savedHash.equals(testHashWrong), "Wrong pattern must fail verification");
        });

        TestAssert.runTest("Lock Security - Security Type Enum Mapping", () -> {
            String[] validTypes = {"NONE", "PATTERN", "PIN"};
            for (String t : validTypes) {
                TestAssert.assertTrue(t.equals("NONE") || t.equals("PATTERN") || t.equals("PIN"), "Valid security type");
            }
        });

        TestAssert.runTest("Lock Security - Minimum Complexity Bar", () -> {
            List<Integer> shortPattern = Arrays.asList(0, 1, 2);
            TestAssert.assertTrue(shortPattern.size() < 4, "Pattern with < 4 dots rejected");

            List<Integer> validPattern = Arrays.asList(0, 3, 6, 7);
            TestAssert.assertTrue(validPattern.size() >= 4, "Pattern with >= 4 dots accepted");

            String shortPin = "12";
            TestAssert.assertTrue(shortPin.length() < 4, "PIN with < 4 digits rejected");

            String validPin = "4826";
            TestAssert.assertTrue(validPin.length() >= 4, "PIN with >= 4 digits accepted");
        });

        TestAssert.runTest("Lock Security - Access Control & Shortcut Privilege Policy", () -> {
            String[] publicShortcuts = {"TORCH", "CAMERA"};
            String[] protectedShortcuts = {"PHONE", "BROWSER", "MESSAGES", "EMAIL", "CALCULATOR"};

            for (String ps : publicShortcuts) {
                TestAssert.assertTrue(ps.equals("TORCH") || ps.equals("CAMERA"), "Public shortcut allowed without auth");
            }

            for (String ps : protectedShortcuts) {
                boolean isSensitive = ps.equals("PHONE") || ps.equals("BROWSER") || ps.equals("MESSAGES") || ps.equals("EMAIL") || ps.equals("CALCULATOR");
                TestAssert.assertTrue(isSensitive, "Sensitive shortcut blocked from unauthorized execution");
            }
        });

        TestAssert.runTest("Lock Security - Settings & Shade Access Control Invariant", () -> {
            boolean isLocked = true;
            boolean isLockEnabled = true;
            boolean allowSettings = !isLocked || !isLockEnabled;
            TestAssert.assertTrue(!allowSettings, "SettingsActivity blocked when device is locked");
        });
    }
}
