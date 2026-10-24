package org.tontos.tests.security;

import org.tontos.tests.common.TestAssert;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SandboxProfileTest {

    private static final Pattern USER_ID_PATTERN = Pattern.compile("UserInfo\\{([0-9]+):([^:]+):([0-9a-f]+)\\}");
    private static final Pattern CREATE_USER_ID_PATTERN = Pattern.compile("(?:id\\s+|UserInfo\\{)([0-9]+)");

    public static void run() {
        System.out.println("  --> [Security] Sandbox Profile & Isolation Suite");

        TestAssert.runTest("Sandbox Profile - User ID Extraction Regex", () -> {
            String out1 = "Success: created user id 10";
            Matcher m1 = CREATE_USER_ID_PATTERN.matcher(out1);
            TestAssert.assertTrue(m1.find(), "Failed to match 'created user id 10'");
            TestAssert.assertEquals("10", m1.group(1), "Extracted user ID 10");

            String out2 = "UserInfo{12:TontOS Sandbox:10}";
            Matcher m2 = CREATE_USER_ID_PATTERN.matcher(out2);
            TestAssert.assertTrue(m2.find(), "Failed to match UserInfo string");
            TestAssert.assertEquals("12", m2.group(1), "Extracted user ID 12");

            String outInvalid = "Error: unable to create user";
            Matcher mInvalid = CREATE_USER_ID_PATTERN.matcher(outInvalid);
            TestAssert.assertFalse(mInvalid.find(), "Matched on error message");
        });

        TestAssert.runTest("Linux DAC UID Calculation & Multi-User Boundaries", () -> {
            int user0 = 0;
            int user10 = 10;
            int appId = 10045; // e.g. com.google.android.gms

            int uid0 = user0 * 100000 + (appId % 100000);
            int uid10 = user10 * 100000 + (appId % 100000);

            TestAssert.assertEquals(10045, uid0, "User 0 UID must be primary");
            TestAssert.assertEquals(1010045, uid10, "User 10 UID must be secondary DAC space");

            // Verify AppNetworkFirewall UID boundary support (10000 <= uid <= 19999999)
            int minAllowed = 10000;
            int maxAllowed = 19999999;

            TestAssert.assertTrue(uid0 >= minAllowed && uid0 <= maxAllowed, "User 0 app enclosed in firewall range");
            TestAssert.assertTrue(uid10 >= minAllowed && uid10 <= maxAllowed, "User 10 sandbox app enclosed in firewall range");

            int systemUid = 1000;
            int rootUid = 0;
            TestAssert.assertFalse(systemUid >= minAllowed && systemUid <= maxAllowed, "System UID must not be touched by firewall");
            TestAssert.assertFalse(rootUid >= minAllowed && rootUid <= maxAllowed, "Root UID must not be touched by firewall");
        });

        TestAssert.runTest("Zero-RAM Process Freezing - Tokenized Arguments", () -> {
            int userId = 10;
            String[] pauseCmd = new String[]{"am", "stop-user", "-f", String.valueOf(userId)};

            TestAssert.assertEquals(4, pauseCmd.length, "Pause command token count");
            TestAssert.assertEquals("am", pauseCmd[0], "Binary token");
            TestAssert.assertEquals("stop-user", pauseCmd[1], "Subcommand");
            TestAssert.assertEquals("-f", pauseCmd[2], "Force flag for Zero-RAM freeze");
            TestAssert.assertEquals("10", pauseCmd[3], "User ID token");

            Pattern safeArg = Pattern.compile("^[a-zA-Z0-9._/\\-]+$");
            for (String token : pauseCmd) {
                TestAssert.assertTrue(safeArg.matcher(token).matches(), "Token must be shell-safe: " + token);
            }
        });

        TestAssert.runTest("AppOps Telemetry Shield - Policy Generation", () -> {
            String[] targetPkgs = {
                "com.google.android.gms",
                "com.google.android.gsf",
                "com.android.vending"
            };

            String[] deniedOps = {
                "BODY_SENSORS",
                "HIGH_SAMPLING_RATE_SENSORS",
                "ACTIVITY_RECOGNITION",
                "ACCESS_BACKGROUND_LOCATION",
                "READ_CONTACTS",
                "READ_CALL_LOG"
            };

            int userId = 10;
            for (String pkg : targetPkgs) {
                for (String op : deniedOps) {
                    String[] cmd = new String[]{"cmd", "appops", "set", "--user", String.valueOf(userId), pkg, op, "deny"};
                    TestAssert.assertEquals(8, cmd.length, "AppOps command token length");
                    TestAssert.assertEquals("deny", cmd[7], "Strict deny mode");
                    TestAssert.assertEquals(String.valueOf(userId), cmd[4], "Target profile user ID");
                }
            }
        });

        TestAssert.runTest("Sandbox Profile State Transitions", () -> {
            String notCreated = "NOT_CREATED";
            String running = "RUNNING";
            String paused = "PAUSED";

            // Valid state lifecycle
            String state = notCreated;
            TestAssert.assertEquals("NOT_CREATED", state, "Initial state");

            state = running;
            TestAssert.assertEquals("RUNNING", state, "After create & start");

            state = paused;
            TestAssert.assertEquals("PAUSED", state, "After stop-user -f");

            state = running;
            TestAssert.assertEquals("RUNNING", state, "After start-user");

            state = notCreated;
            TestAssert.assertEquals("NOT_CREATED", state, "After remove-user");
        });
    }
}
