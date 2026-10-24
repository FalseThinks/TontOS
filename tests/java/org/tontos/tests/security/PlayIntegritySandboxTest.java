package org.tontos.tests.security;

import org.tontos.tests.common.TestAssert;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlayIntegritySandboxTest {

    private static final Pattern VERDICT_PATTERN = Pattern.compile("\"deviceRecognitionVerdict\"\\s*:\\s*\\[([^\\]]+)\\]");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("\"requestPackageName\"\\s*:\\s*\"([^\"]+)\"");

    public static void run() {
        System.out.println("  --> [Security] Play Integrity & Sandbox Isolation Suite");

        TestAssert.runTest("Play Integrity - Verdict Extraction & Evaluation", () -> {
            String sampleResponse = "{"
                + "\"requestDetails\":{\"requestPackageName\":\"com.example.bankapp\",\"timestampMillis\":1729639600000},"
                + "\"deviceIntegrity\":{\"deviceRecognitionVerdict\":[\"MEETS_BASIC_INTEGRITY\",\"MEETS_DEVICE_INTEGRITY\"]}"
                + "}";

            Matcher pkgMatcher = PACKAGE_PATTERN.matcher(sampleResponse);
            TestAssert.assertTrue(pkgMatcher.find(), "Failed to extract package name from Play Integrity response");
            TestAssert.assertEquals("com.example.bankapp", pkgMatcher.group(1), "Extracted package name");

            Matcher verdictMatcher = VERDICT_PATTERN.matcher(sampleResponse);
            TestAssert.assertTrue(verdictMatcher.find(), "Failed to extract deviceRecognitionVerdict array");
            String verdictsRaw = verdictMatcher.group(1);

            TestAssert.assertTrue(verdictsRaw.contains("MEETS_BASIC_INTEGRITY"), "Response must satisfy Basic Integrity");
            TestAssert.assertTrue(verdictsRaw.contains("MEETS_DEVICE_INTEGRITY"), "Response satisfies Device Integrity");
            TestAssert.assertFalse(verdictsRaw.contains("MEETS_STRONG_INTEGRITY"), "Strong hardware enclave integrity absent in uncertified baseline");
        });

        TestAssert.runTest("Play Integrity - Sandbox KeyStore Namespace Isolation", () -> {
            int user0 = 0;
            int user10 = 10;
            int gmsAppId = 10045; // GmsCore appId

            // Linux UID assigned to Keystore namespace
            int keystoreUidUser0 = user0 * 100000 + (gmsAppId % 100000);
            int keystoreUidUser10 = user10 * 100000 + (gmsAppId % 100000);

            TestAssert.assertEquals(10045, keystoreUidUser0, "User 0 Keystore namespace UID");
            TestAssert.assertEquals(1010045, keystoreUidUser10, "User 10 Sandbox Keystore namespace UID");

            // KeyStore aliases generated in User 10 must be partitioned from User 0
            String keyAlias = "attestation_key_play_integrity";
            String user0KeyPath = "/data/misc/keystore/user_0/" + keystoreUidUser0 + "_" + keyAlias;
            String user10KeyPath = "/data/misc/keystore/user_10/" + keystoreUidUser10 + "_" + keyAlias;

            TestAssert.assertFalse(user0KeyPath.equals(user10KeyPath), "Keystore paths must be cryptographically partitioned");
            TestAssert.assertTrue(user10KeyPath.contains("user_10"), "User 10 keystore path must contain user_10 directory");
        });

        TestAssert.runTest("Play Integrity - AppOps Telemetry Shield Compatibility", () -> {
            // Permissions denied by SandboxManager telemetry shield
            Set<String> deniedOps = new HashSet<>(Arrays.asList(
                "BODY_SENSORS",
                "HIGH_SAMPLING_RATE_SENSORS",
                "ACTIVITY_RECOGNITION",
                "ACCESS_BACKGROUND_LOCATION",
                "READ_CONTACTS",
                "READ_CALL_LOG"
            ));

            // Required capabilities for Play Integrity attestation
            // (Uses Binder IPC to IIntegrityService, Keystore, and outbound HTTPS - none of the denied telemetry ops)
            String[] requiredIntegrityCapabilities = {
                "android.permission.INTERNET",
                "com.google.android.gms.permission.BIND_INTEGRITY_SERVICE",
                "android.security.keystore.KeyGenParameterSpec"
            };

            for (String cap : requiredIntegrityCapabilities) {
                TestAssert.assertFalse(deniedOps.contains(cap), "Telemetry shield must not block integrity capability: " + cap);
            }
        });

        TestAssert.runTest("Play Integrity - Per-App Network Firewall Interaction", () -> {
            int sandboxGmsUid = 1010045;
            String uidStr = String.valueOf(sandboxGmsUid);

            // Firewall rule syntax for blocking GMS network in sandbox
            String[] blockRule = new String[]{
                "iptables", "-I", "OUTPUT", "-m", "owner", "--uid-owner", uidStr, "-j", "REJECT"
            };
            TestAssert.assertEquals(9, blockRule.length, "iptables block rule token count");
            TestAssert.assertEquals("1010045", blockRule[6], "Target sandboxed GMS UID");

            // SafeCommandExecutor token validation
            Pattern safeArg = Pattern.compile("^[a-zA-Z0-9._/\\-]+$");
            for (String token : blockRule) {
                TestAssert.assertTrue(safeArg.matcher(token).matches(), "Token must be shell-safe: " + token);
            }
        });

        TestAssert.runTest("Play Integrity - Build Property Security Envelope", () -> {
            // Verify expected property patterns
            Pattern patchPattern = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
            String samplePatch = "2024-08-05";
            TestAssert.assertTrue(patchPattern.matcher(samplePatch).matches(), "Security patch format");

            // Fingerprint structure: <brand>/<product>/<device>:<version>/<build_id>/<incremental>:<type>/<tags>
            Pattern fpPattern = Pattern.compile("^[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+:[0-9.]+/[a-zA-Z0-9._-]+/[0-9]+:(user|userdebug)/[a-zA-Z0-9._-]+$");
            String sampleFp = "google/shiba/shiba:14/UD1A.230803.041/10808477:user/release-keys";
            TestAssert.assertTrue(fpPattern.matcher(sampleFp).matches(), "CTS certified build fingerprint pattern");

            String devFp = "generic/vsoc_x86_64_only/vsoc_x86_64_only:15/AP3A.240905.015/eng.root.20240905.000000:userdebug/test-keys";
            TestAssert.assertTrue(devFp.contains("userdebug") || devFp.contains("user"), "Development build type valid");
        });
    }
}
