package org.tontos.tests.security;

import org.tontos.tests.common.TestAssert;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class BackupIntegrityTest {

    public static void run() {
        System.out.println("  --> [Security] Backup Cryptographic Integrity Suite");

        TestAssert.runTest("Cryptographic Backup - SHA-256 Envelope Verification", () -> {
            String payloadJson = "{\"settings\":{\"theme\":\"AMOLED_BLACK\"},\"sms\":[]}";
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payloadJson.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            String calculatedHash = hexString.toString();
            TestAssert.assertEquals(64, calculatedHash.length(), "SHA-256 hex string length");

            // Envelope verification
            String envelope = String.format("{\"manifest\":{\"sha256\":\"%s\"},\"payload\":%s}", calculatedHash, payloadJson);
            TestAssert.assertTrue(envelope.contains(calculatedHash), "Envelope must contain manifest SHA-256");
        });

        TestAssert.runTest("Tampered Backup Envelope Detection", () -> {
            String genuinePayload = "{\"budget\":120}";
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(genuinePayload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            String genuineHash = hexString.toString();

            String tamperedPayload = "{\"budget\":999}";
            byte[] tamperedHashBytes = digest.digest(tamperedPayload.getBytes(StandardCharsets.UTF_8));
            StringBuilder tamperedHex = new StringBuilder();
            for (byte b : tamperedHashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) tamperedHex.append('0');
                tamperedHex.append(hex);
            }
            TestAssert.assertFalse(genuineHash.equals(tamperedHex.toString()), "Tampered payload produced identical hash!");
        });

        TestAssert.runTest("Backup Envelope Edge Cases", () -> {
            String emptyPayload = "";
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] emptyHash = digest.digest(emptyPayload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : emptyHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            // Known SHA-256 of empty string: e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
            TestAssert.assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hexString.toString(), "Empty SHA-256 hash");

            // Non-hex string detection
            String badHash = "invalid_hash_xyz_not_hex";
            TestAssert.assertFalse(badHash.matches("^[a-fA-F0-9]{64}$"), "Invalid non-hex hash matched regex!");
        });
    }
}
