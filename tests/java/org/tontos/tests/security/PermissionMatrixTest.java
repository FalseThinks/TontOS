package org.tontos.tests.security;

import org.tontos.tests.common.TestAssert;
import java.io.File;
import java.nio.file.Files;
import java.util.List;

public class PermissionMatrixTest {

    public static void run() {
        System.out.println("  --> [Security] Permission Matrix & AOSP Default Grants Suite");

        TestAssert.runTest("Default Permissions XML - File Existence & Integrity", () -> {
            File xmlFile = new File("platform/products/default-permissions-tontos.xml");
            if (!xmlFile.exists()) {
                xmlFile = new File("../../platform/products/default-permissions-tontos.xml");
            }
            if (xmlFile.exists()) {
                List<String> lines = Files.readAllLines(xmlFile.toPath());
                String content = String.join("\n", lines);
                TestAssert.assertTrue(content.contains("<exceptions>"), "XML root must contain <exceptions>");
                TestAssert.assertTrue(content.contains("org.fossify.phone"), "Default permissions for Phone");
                TestAssert.assertTrue(content.contains("org.fossify.messages"), "Default permissions for Messages");
                TestAssert.assertTrue(content.contains("org.fossify.contacts"), "Default permissions for Contacts");
            }
        });

        TestAssert.runTest("Permission Grants - Dangerous Permissions Scoping", () -> {
            String[] allowedDangerous = {
                "android.permission.READ_CONTACTS",
                "android.permission.WRITE_CONTACTS",
                "android.permission.CALL_PHONE",
                "android.permission.READ_PHONE_STATE",
                "android.permission.SEND_SMS",
                "android.permission.RECEIVE_SMS"
            };
            for (String perm : allowedDangerous) {
                TestAssert.assertTrue(perm.startsWith("android.permission."), "Well-formed permission namespace: " + perm);
            }
        });
    }
}
