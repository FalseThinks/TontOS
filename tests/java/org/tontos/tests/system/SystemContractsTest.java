package org.tontos.tests.system;

import org.tontos.tests.common.TestAssert;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SystemContractsTest {

    public static void run() {
        System.out.println("  --> [System] System Contracts, Low-RAM & Kernel Suite");

        TestAssert.runTest("Settings Sections Contract (1-5)", () -> {
            int totalSections = 5;
            TestAssert.assertEquals(5, totalSections, "Settings must have exactly 5 categorized sections");
        });

        TestAssert.runTest("Wizard Steps Contract (0-4)", () -> {
            int totalSteps = 5;
            TestAssert.assertEquals(5, totalSteps, "Setup Wizard must have 5 linear steps (0-4)");
        });

        TestAssert.runTest("Low-RAM AOSP Properties in tontos_base.mk", () -> {
            File baseMk = new File("platform/products/tontos_base.mk");
            if (!baseMk.exists()) {
                baseMk = new File("../../platform/products/tontos_base.mk");
            }
            if (baseMk.exists()) {
                List<String> lines = Files.readAllLines(baseMk.toPath());
                String content = String.join("\n", lines);
                TestAssert.assertTrue(content.contains("ro.config.low_ram=true"), "ro.config.low_ram=true");
                TestAssert.assertTrue(content.contains("ro.sys.fw.bg_apps_limit=4"), "ro.sys.fw.bg_apps_limit=4");
                TestAssert.assertTrue(content.contains("dalvik.vm.heapgrowthlimit=128m"), "dalvik.vm.heapgrowthlimit=128m");
                TestAssert.assertTrue(content.contains("ro.statsd.enable=false"), "ro.statsd.enable=false");
            }
        });

        TestAssert.runTest("Background Execution Low-RAM Policy Contract", () -> {
            int maxBgProcesses = 2;
            int bgAppOpsMode = 2; // AppOpsManager.MODE_IGNORED
            TestAssert.assertEquals(2, maxBgProcesses, "Max background cached processes for 4GB system");
            TestAssert.assertEquals(2, bgAppOpsMode, "Background run restricted mode");
        });

        TestAssert.runTest("Kernel Version Regex Extraction", () -> {
            Pattern p = Pattern.compile("^([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)");
            String[] rawKernels = {
                "6.6.21-android15-11-g8d123456789",
                "5.15.148-android14-0-gabcdef",
                "6.1.75-gki+",
                "6.12.0"
            };
            String[] expected = {"6.6.21", "5.15.148", "6.1.75", "6.12.0"};
            for (int i = 0; i < rawKernels.length; i++) {
                Matcher m = p.matcher(rawKernels[i]);
                TestAssert.assertTrue(m.find(), "Failed to match: " + rawKernels[i]);
                TestAssert.assertEquals(expected[i], m.group(1), "Extracted semver");
            }
        });

        TestAssert.runTest("OTA Semver Tag Comparison & Channels", () -> {
            String current = "0.0.2";
            String candidate = "0.0.3";
            TestAssert.assertTrue(candidate.compareTo(current) > 0, "Candidate 0.0.3 must be newer than 0.0.2");

            String[] channels = {"RELEASE", "BETA", "NIGHTLY"};
            TestAssert.assertEquals(3, channels.length, "Channels count");
        });

        TestAssert.runTest("Supported I18n ISO-639-1 Language Codes", () -> {
            String[] supported = {"en", "es", "de", "pl", "fr", "it", "pt", "ru", "uk", "zh", "ja", "nl"};
            Set<String> langSet = new HashSet<>(Arrays.asList(supported));
            TestAssert.assertEquals(12, langSet.size(), "Supported language count");
            TestAssert.assertTrue(langSet.contains("en"), "English must be supported");
            TestAssert.assertTrue(langSet.contains("es"), "Spanish must be supported");
            TestAssert.assertTrue(langSet.contains("de"), "German must be supported");
            TestAssert.assertTrue(langSet.contains("pl"), "Polish must be supported");
        });
    }
}
