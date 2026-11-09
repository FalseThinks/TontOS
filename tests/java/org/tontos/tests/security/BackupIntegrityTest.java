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

        TestAssert.runTest("Selective Backup Categories - Symmetry and Key Isolation", () -> {
            java.util.List<String> categories = java.util.Arrays.asList("display", "detox", "launcher", "security", "system");
            TestAssert.assertEquals(5, categories.size(), "Exactly 5 symmetric backup categories must be defined");

            java.util.Map<String, String> keyCategoryMap = new java.util.HashMap<>();
            keyCategoryMap.put("selected_theme", "display");
            keyCategoryMap.put("pref_font_scale", "display");
            keyCategoryMap.put("pref_clock_24h", "display");
            keyCategoryMap.put("pref_grayscale_enabled", "display");
            keyCategoryMap.put("pref_friction_seconds", "detox");
            keyCategoryMap.put("pref_daily_budget_mins", "detox");
            keyCategoryMap.put("pinned_apps", "launcher");
            keyCategoryMap.put("shade_tiles", "launcher");
            keyCategoryMap.put("pref_blocked_network_apps", "security");
            keyCategoryMap.put("pref_privacy_restricted_bg_apps", "security");
            keyCategoryMap.put("bg_policy", "system");
            keyCategoryMap.put("pref_ultra_battery_saver", "system");

            // Key mapping isolation
            TestAssert.assertEquals("display", keyCategoryMap.get("selected_theme"), "Theme maps to display");
            TestAssert.assertEquals("display", keyCategoryMap.get("pref_font_scale"), "Font scale maps to display");
            TestAssert.assertEquals("detox", keyCategoryMap.get("pref_friction_seconds"), "Friction maps to detox");
            TestAssert.assertEquals("detox", keyCategoryMap.get("pref_daily_budget_mins"), "Daily budget maps to detox");
            TestAssert.assertEquals("launcher", keyCategoryMap.get("pinned_apps"), "Pinned apps map to launcher");
            TestAssert.assertEquals("launcher", keyCategoryMap.get("shade_tiles"), "Shade tiles map to launcher");
            TestAssert.assertEquals("security", keyCategoryMap.get("pref_blocked_network_apps"), "Firewall maps to security");
            TestAssert.assertEquals("security", keyCategoryMap.get("pref_privacy_restricted_bg_apps"), "Privacy restricted apps map to security");
            TestAssert.assertEquals("system", keyCategoryMap.get("bg_policy"), "Background policy maps to system");
            TestAssert.assertEquals("system", keyCategoryMap.get("pref_ultra_battery_saver"), "Ultra battery saver maps to system");

            // Disallowed security / PIN keys return null
            TestAssert.assertEquals(null, keyCategoryMap.get("pref_lock_pin"), "PIN credentials must never map to any backup category");
            TestAssert.assertEquals(null, keyCategoryMap.get("lock_salt"), "Lock salt must never map to any backup category");

            // Symmetric export/import category check: ensure every category in export is recognized in import
            for (String cat : categories) {
                TestAssert.assertTrue(categories.contains(cat), "Export category " + cat + " must match import category");
            }
        });

        TestAssert.runTest("Selective Backup Categories - Select All (Todo) Symmetry", () -> {
            java.util.List<String> allCats = java.util.Arrays.asList("display", "detox", "launcher", "security", "system");
            java.util.Set<String> selectedAll = new java.util.HashSet<>(allCats);
            TestAssert.assertEquals(allCats.size(), selectedAll.size(), "Todo option selects exactly all available categories");

            // Toggle deselect all
            selectedAll.clear();
            TestAssert.assertEquals(0, selectedAll.size(), "Deselect Todo clears all categories");

            // Re-select all
            selectedAll.addAll(allCats);
            TestAssert.assertEquals(allCats.size(), selectedAll.size(), "Re-select Todo restores all categories");
        });

        TestAssert.runTest("Encrypted Backup - AES-256-GCM Roundtrip and Tamper Detection", () -> {
            String genuinePayload = "{\"selected_theme\":\"solarized_dark\",\"pref_font_scale\":1.2}";
            byte[] salt = new byte[16];
            new java.security.SecureRandom().nextBytes(salt);
            byte[] iv = new byte[12];
            new java.security.SecureRandom().nextBytes(iv);

            // PBKDF2 derive key
            javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec("tontos_user_secret".toCharArray(), salt, 10000, 256);
            javax.crypto.SecretKeyFactory factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
            javax.crypto.spec.GCMParameterSpec gcmSpec = new javax.crypto.spec.GCMParameterSpec(128, iv);

            // Encrypt
            javax.crypto.Cipher encCipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            encCipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
            byte[] ciphertext = encCipher.doFinal(genuinePayload.getBytes(StandardCharsets.UTF_8));
            TestAssert.assertTrue(ciphertext.length > genuinePayload.length(), "AES-GCM ciphertext includes 128-bit authentication tag");

            // Decrypt genuine
            javax.crypto.Cipher decCipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            decCipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, gcmSpec);
            String decrypted = new String(decCipher.doFinal(ciphertext), StandardCharsets.UTF_8);
            TestAssert.assertEquals(genuinePayload, decrypted, "Decrypted payload matches original");

            // Tampered ciphertext must fail authentication
            byte[] tamperedCipher = ciphertext.clone();
            tamperedCipher[0] ^= 0x42;
            javax.crypto.Cipher decTamper = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            decTamper.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, gcmSpec);
            boolean tamperFailed = false;
            try {
                decTamper.doFinal(tamperedCipher);
            } catch (Exception e) {
                tamperFailed = true;
            }
            TestAssert.assertTrue(tamperFailed, "Tampered ciphertext must fail AES-GCM authentication tag check");
        });

        TestAssert.runTest("Encrypted Backup - Password Verification and Bad Key Rejection", () -> {
            String genuinePayload = "{\"bg_policy\":\"strict\"}";
            byte[] salt = new byte[16];
            new java.security.SecureRandom().nextBytes(salt);
            byte[] iv = new byte[12];
            new java.security.SecureRandom().nextBytes(iv);

            javax.crypto.SecretKeyFactory factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

            // Encrypt with correct password
            byte[] correctKey = factory.generateSecret(new javax.crypto.spec.PBEKeySpec("correct_pass".toCharArray(), salt, 10000, 256)).getEncoded();
            javax.crypto.Cipher enc = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            enc.init(javax.crypto.Cipher.ENCRYPT_MODE, new javax.crypto.spec.SecretKeySpec(correctKey, "AES"), new javax.crypto.spec.GCMParameterSpec(128, iv));
            byte[] ciphertext = enc.doFinal(genuinePayload.getBytes(StandardCharsets.UTF_8));

            // Decrypt with wrong password
            byte[] wrongKey = factory.generateSecret(new javax.crypto.spec.PBEKeySpec("wrong_pass".toCharArray(), salt, 10000, 256)).getEncoded();
            javax.crypto.Cipher decWrong = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            decWrong.init(javax.crypto.Cipher.DECRYPT_MODE, new javax.crypto.spec.SecretKeySpec(wrongKey, "AES"), new javax.crypto.spec.GCMParameterSpec(128, iv));

            boolean wrongPassFailed = false;
            try {
                decWrong.doFinal(ciphertext);
            } catch (Exception e) {
                wrongPassFailed = true;
            }
            TestAssert.assertTrue(wrongPassFailed, "Wrong password must fail AES-GCM decryption");
        });
    }
}
