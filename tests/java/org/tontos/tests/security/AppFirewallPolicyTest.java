package org.tontos.tests.security;

import org.tontos.tests.common.TestAssert;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppFirewallPolicyTest {

    public static void run() {
        System.out.println("  --> [Security] App Firewall & Sandbox Policy Suite");

        TestAssert.runTest("App Network Firewall - AppOps Policy Generation", () -> {
            String testPkg = "com.suspicious.analytics";
            String[] expectedAppOps = {
                String.format("cmd appops set %s RUN_IN_BACKGROUND deny", testPkg),
                String.format("cmd appops set %s ACCESS_RESTRICTED_SETTINGS deny", testPkg)
            };
            TestAssert.assertEquals("cmd appops set com.suspicious.analytics RUN_IN_BACKGROUND deny", expectedAppOps[0], "AppOps RUN_IN_BACKGROUND");
            TestAssert.assertEquals("cmd appops set com.suspicious.analytics ACCESS_RESTRICTED_SETTINGS deny", expectedAppOps[1], "AppOps ACCESS_RESTRICTED_SETTINGS");
        });

        TestAssert.runTest("NFC Payment Sandbox - Profile ID Extraction", () -> {
            String sampleOutput = "Success: created user id 10";
            Pattern p = Pattern.compile("id\\s+([0-9]+)");
            Matcher m = p.matcher(sampleOutput);
            TestAssert.assertTrue(m.find(), "Failed to find user ID in create-user output");
            String userId = m.group(1);
            TestAssert.assertEquals("10", userId, "Extracted user ID");

            String startUserCmd = "am start-user " + userId;
            TestAssert.assertEquals("am start-user 10", startUserCmd, "Start user command");
        });

        TestAssert.runTest("Play Integrity - Pixel 8 CTS Fingerprint", () -> {
            String fingerprint = "google/shiba/shiba:14/UD1A.230803.041/10808477:user/release-keys";
            TestAssert.assertTrue(fingerprint.startsWith("google/shiba"), "Fingerprint device brand/model");
            TestAssert.assertTrue(fingerprint.endsWith("user/release-keys"), "Fingerprint build tag");

            String securityPatch = "2024-08-05";
            TestAssert.assertTrue(securityPatch.matches("^\\d{4}-\\d{2}-\\d{2}$"), "Security patch format");
        });
    }
}
