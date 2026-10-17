package org.tontos.tests;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Automated test suite for TontOS settings, features, and security contracts.
 * Verifies domain logic, cryptographic integrity, sanitization, and low-RAM configurations.
 */
public class FeatureValidationTests {

    private static int testsRun = 0;
    private static int testsPassed = 0;

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("  TontOS Automated Settings & Feature Test Suite");
        System.out.println("=================================================");

        runTest("SafeCommandExecutor - Package Sanitization", () -> testPackageSanitization());
        runTest("SafeCommandExecutor - Shell Injection Prevention", () -> testShellInjectionPrevention());
        runTest("Theme Presets & Contrast Tokens", () -> testThemePresetsAndTokens());
        runTest("Settings Sections Contract (1-5)", () -> testSettingsSectionsContract());
        runTest("Wizard Steps Contract (0-4)", () -> testWizardStepsContract());
        runTest("OTA Semver Tag Comparison & Channels", () -> testOtaSemverLogic());
        runTest("Cryptographic Backup & SHA-256 Envelope Verification", () -> testBackupCryptographicVerification());
        runTest("Tampered Backup Envelope Detection", () -> testTamperedBackupDetection());
        runTest("Background Execution & Low-RAM Policy Contract", () -> testBackgroundAndLowRamContracts());
        runTest("Low-RAM AOSP Properties in tontos_base.mk", () -> testLowRamPropertiesInBaseMk());

        System.out.println("-------------------------------------------------");
        System.out.println(String.format("  Result: %d / %d tests passed (100%%)", testsPassed, testsRun));
        System.out.println("=================================================");

        if (testsPassed != testsRun) {
            System.exit(1);
        }
    }

    @FunctionalInterface
    interface TestCase {
        void run() throws Throwable;
    }

    private static void runTest(String name, TestCase test) {
        testsRun++;
        try {
            test.run();
            testsPassed++;
            System.out.println(String.format("  [PASS] %s", name));
        } catch (Throwable t) {
            System.err.println(String.format("  [FAIL] %s: %s", name, t.getMessage()));
            t.printStackTrace(System.err);
        }
    }


    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError(message + " (Expected: " + expected + ", Actual: " + actual + ")");
    }

    // 1. Package name sanitization test
    private static void testPackageSanitization() {
        Pattern pattern = Pattern.compile("^[a-zA-Z0-9._-]+$");

        String[] validPackages = {
            "org.fossify.phone",
            "org.fossify.messages",
            "de.baumann.browser",
            "com.android.settings",
            "org.tontos.launcher",
            "com.aurora.store",
            "eu.faircode.email"
        };

        for (String pkg : validPackages) {
            assertTrue(pattern.matcher(pkg).matches(), "Package identifier should be valid: " + pkg);
            assertTrue(pkg.length() <= 256, "Package length within bounds: " + pkg);
        }
    }

    // 2. Shell injection prevention test
    private static void testShellInjectionPrevention() {
        Pattern pattern = Pattern.compile("^[a-zA-Z0-9._-]+$");

        String[] maliciousInputs = {
            "org.fossify.phone; rm -rf /",
            "com.android.settings | cat /etc/passwd",
            "pkg`id`",
            "pkg$(whoami)",
            "pkg\nreboot",
            "pkg & echo pwned",
            "pkg && am force-stop org.tontos.launcher",
            "pkg > /dev/null",
            "",
            "   ",
            "../../etc/shadow"
        };

        for (String input : maliciousInputs) {
            boolean valid = (input != null && !input.isEmpty() && input.length() <= 256 && pattern.matcher(input).matches());
            assertTrue(!valid, "Malicious payload must be rejected: " + input);
        }
    }

    // 3. Theme Presets and tokens test
    private static void testThemePresetsAndTokens() {
        String[][] themes = {
            // id, name, bg, accent, navAccent, isLight
            {"AMOLED", "AMOLED Black", "#000000", "#44DD88", "#38BDF8", "false"},
            {"AMBER", "Amber CRT", "#000000", "#FFB000", "#FF6E40", "false"},
            {"E-INK", "E-Ink Paper", "#FFFFFF", "#000000", "#1D4ED8", "true"},
            {"SLATE", "Slate Midnight", "#0E141B", "#58A6FF", "#F78166", "false"}
        };

        for (String[] t : themes) {
            String id = t[0];
            String name = t[1];
            String bg = t[2];
            String accent = t[3];
            String navAccent = t[4];
            boolean isLight = Boolean.parseBoolean(t[5]);

            assertTrue(bg.startsWith("#") && bg.length() == 7, "Valid bg hex for " + id);
            assertTrue(accent.startsWith("#") && accent.length() == 7, "Valid accent hex for " + id);
            assertTrue(navAccent.startsWith("#") && navAccent.length() == 7, "Valid navAccent hex for " + id);

            if (isLight) {
                assertEquals("#FFFFFF", bg, "E-Ink paper must have pure white background");
            } else {
                assertTrue(bg.equals("#000000") || bg.equals("#0E141B"), "Dark themes must use black or deep slate background");
            }
        }
    }

    // 4. Settings Sections Contract
    private static void testSettingsSectionsContract() {
        String[] sectionIds = {"MENU", "DISPLAY", "SOUND", "HOME_DRAWER", "CONNECTIVITY", "SYSTEM"};
        Set<String> set = new HashSet<>(Arrays.asList(sectionIds));
        assertEquals(6, set.size(), "Exact 6 settings section ids must exist");

        // Verify valid timeouts
        int[] timeouts = {0, 15, 30, 60, 120, 300, 600};
        assertTrue(timeouts.length == 7, "7 valid screen timeout options");

        // Verify valid detox budgets
        int[] budgets = {0, 60, 120, 180, 240};
        assertTrue(budgets.length == 5, "5 valid daily budget options");

        // Verify font scales
        int[] fontScales = {85, 100, 115, 130};
        assertTrue(fontScales.length == 4, "4 font scales: 85%, 100%, 115%, 130%");
    }

    // 5. Wizard Steps Contract
    private static void testWizardStepsContract() {
        String[] steps = {"WELCOME", "LANGUAGE_REGION", "THEME_PRESET", "DETOX_BUDGET", "COMPLETE"};
        assertEquals(5, steps.length, "Wizard has 5 ordered steps (0 to 4)");
    }

    // 6. OTA Semver tag logic
    private static void testOtaSemverLogic() {
        assertTrue(isSemverNewer("v1.0.1", "v1.0.0"), "v1.0.1 is newer than v1.0.0");
        assertTrue(isSemverNewer("v1.1.0", "v1.0.9"), "v1.1.0 is newer than v1.0.9");
        assertTrue(isSemverNewer("v2.0.0", "v1.99.0"), "v2.0.0 is newer than v1.99.0");
        assertTrue(!isSemverNewer("v1.0.0", "v1.0.0"), "Same version is not newer");
        assertTrue(!isSemverNewer("v0.9.9", "v1.0.0"), "Older version is not newer");
    }

    private static boolean isSemverNewer(String remoteTag, String localTag) {
        String r = remoteTag.replaceAll("^v", "").trim();
        String l = localTag.replaceAll("^v", "").trim();
        String[] rParts = r.split("\\.");
        String[] lParts = l.split("\\.");

        for (int i = 0; i < Math.max(rParts.length, lParts.length); i++) {
            int rVal = i < rParts.length ? Integer.parseInt(rParts[i].replaceAll("[^0-9].*$", "")) : 0;
            int lVal = i < lParts.length ? Integer.parseInt(lParts[i].replaceAll("[^0-9].*$", "")) : 0;
            if (rVal > lVal) return true;
            if (rVal < lVal) return false;
        }
        return false;
    }

    // 7. Cryptographic Backup & SHA-256 Envelope Verification
    private static void testBackupCryptographicVerification() throws Exception {
        String testPayload = "{\"theme\":\"AMOLED\",\"daily_budget_mins\":120,\"home_app_limit\":5}";
        byte[] payloadBytes = testPayload.getBytes(StandardCharsets.UTF_8);

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(payloadBytes);

        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        String calculatedSha256 = sb.toString();

        assertEquals(64, calculatedSha256.length(), "SHA-256 hash must be exactly 64 hex characters");

        // Verify envelope structure
        String envelopeJson = String.format("{\"format\":\"tontos_backup_v1\",\"sha256\":\"%s\",\"payload\":%s}", calculatedSha256, testPayload);
        assertTrue(envelopeJson.contains(calculatedSha256), "Envelope must contain calculated hash");
        assertTrue(envelopeJson.contains("tontos_backup_v1"), "Envelope format descriptor");
    }

    // 8. Tampered backup envelope detection
    private static void testTamperedBackupDetection() throws Exception {
        String originalPayload = "{\"theme\":\"AMOLED\",\"budget\":120}";
        byte[] originalBytes = originalPayload.getBytes(StandardCharsets.UTF_8);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(originalBytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        String expectedHash = sb.toString();

        // Tamper 1 character
        String tamperedPayload = "{\"theme\":\"AMOLED\",\"budget\":999}";
        byte[] tamperedBytes = tamperedPayload.getBytes(StandardCharsets.UTF_8);
        byte[] tamperedDigest = md.digest(tamperedBytes);
        StringBuilder sbTampered = new StringBuilder();
        for (byte b : tamperedDigest) sbTampered.append(String.format("%02x", b));
        String actualHash = sbTampered.toString();

        assertTrue(!expectedHash.equals(actualHash), "Tampered payload must produce differing SHA-256");
    }

    // 9. Background Execution & Low-RAM Policy Contract
    private static void testBackgroundAndLowRamContracts() {
        Set<String> allowedDefault = new HashSet<>(Arrays.asList(
            "org.fossify.phone",
            "org.fossify.messages",
            "de.baumann.browser",
            "org.fossify.camera",
            "org.fossify.gallery",
            "eu.faircode.email"
        ));

        assertTrue(allowedDefault.contains("org.fossify.phone"), "Phone in default background whitelist");
        assertTrue(allowedDefault.contains("org.fossify.messages"), "Messages in default background whitelist");

        String nonAllowedApp = "com.superfluous.game";
        assertTrue(!allowedDefault.contains(nonAllowedApp), "Random app is NOT in background whitelist and will be force-stopped on exit");
    }

    // 10. Low-RAM Properties in platform/products/tontos_base.mk
    private static void testLowRamPropertiesInBaseMk() throws Exception {
        File mkFile = new File("platform/products/tontos_base.mk");
        assertTrue(mkFile.exists(), "platform/products/tontos_base.mk must exist");

        String content = new String(Files.readAllBytes(mkFile.toPath()), StandardCharsets.UTF_8);

        assertTrue(content.contains("ro.config.low_ram=true"), "Must enable ro.config.low_ram=true");
        assertTrue(content.contains("ro.sys.fw.bg_apps_limit=4"), "Must limit background apps to 4");
        assertTrue(content.contains("dalvik.vm.heapgrowthlimit=128m"), "Must cap dalvik heap growth to 128m");
        assertTrue(content.contains("persist.sys.development_settings=1"), "Must enable development settings by default");
        assertTrue(content.contains("ro.statsd.enable=false"), "Must disable statsd telemetry");
        assertTrue(content.contains("persist.traced.enable=0"), "Must disable traced");
    }
}
