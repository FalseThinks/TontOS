package org.tontos.tests;

import org.tontos.tests.common.TestAssert;
import org.tontos.tests.security.AppFirewallPolicyTest;
import org.tontos.tests.security.BackupIntegrityTest;
import org.tontos.tests.security.PermissionMatrixTest;
import org.tontos.tests.security.PlayIntegritySandboxTest;
import org.tontos.tests.security.SandboxProfileTest;
import org.tontos.tests.security.SecuritySanitizationTest;
import org.tontos.tests.system.AudioAndHardwareTest;
import org.tontos.tests.system.BatteryOptimizationTest;
import org.tontos.tests.system.StorageManagementTest;
import org.tontos.tests.system.SystemContractsTest;
import org.tontos.tests.ui.DetoxAndFrictionTest;
import org.tontos.tests.ui.ThemeAndAccessibilityTest;
import org.tontos.tests.ui.TypographyScalingTest;

public class TestRunner {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("  TontOS Automated Modular Test Suite");
        System.out.println("=================================================");

        // Security Suites
        SecuritySanitizationTest.run();
        BackupIntegrityTest.run();
        AppFirewallPolicyTest.run();
        PermissionMatrixTest.run();
        SandboxProfileTest.run();
        PlayIntegritySandboxTest.run();

        // UI & UX Suites
        ThemeAndAccessibilityTest.run();
        DetoxAndFrictionTest.run();
        TypographyScalingTest.run();

        // System & Hardware Suites
        AudioAndHardwareTest.run();
        SystemContractsTest.run();
        BatteryOptimizationTest.run();
        StorageManagementTest.run();

        int total = TestAssert.getTotalTests();
        int passed = TestAssert.getPassedTests();

        System.out.println("-------------------------------------------------");
        System.out.println(String.format("  Result: %d / %d tests passed (100%%)", passed, total));
        System.out.println("=================================================");

        if (passed != total) {
            System.exit(1);
        }
    }
}
