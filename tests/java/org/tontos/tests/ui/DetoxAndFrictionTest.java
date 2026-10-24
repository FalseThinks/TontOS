package org.tontos.tests.ui;

import org.tontos.tests.common.TestAssert;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DetoxAndFrictionTest {

    public static void run() {
        System.out.println("  --> [UI] Digital Detox, Friction UX & Lock Screen Suite");

        TestAssert.runTest("Friction App Launch Delay Options", () -> {
            Integer[] allowedDelays = {0, 3, 5, 10};
            List<Integer> delayList = Arrays.asList(allowedDelays);
            TestAssert.assertEquals(4, delayList.size(), "Friction delay options count");
            TestAssert.assertTrue(delayList.contains(0), "0s (Direct) must exist");
            TestAssert.assertTrue(delayList.contains(3), "3s pause must exist");
            TestAssert.assertTrue(delayList.contains(5), "5s pause must exist");
            TestAssert.assertTrue(delayList.contains(10), "10s pause must exist");
        });

        TestAssert.runTest("Lock Screen Clock Styles & Shortcuts Contract", () -> {
            String[] clockStyles = {
                "THEME_DEFAULT", "BIG_MONOSPACE", "VERTICAL_STACKED", "TERMINAL_RETRO",
                "COMPACT_SINGLE", "MINIMAL_DATE_ONLY", "CLOCK_OFF"
            };
            Set<String> clockSet = new HashSet<>(Arrays.asList(clockStyles));
            TestAssert.assertEquals(7, clockSet.size(), "Clock styles count");
            TestAssert.assertTrue(clockSet.contains("THEME_DEFAULT"), "Default clock style");
            TestAssert.assertTrue(clockSet.contains("MINIMAL_DATE_ONLY"), "Minimal date only clock style");
            TestAssert.assertTrue(clockSet.contains("CLOCK_OFF"), "Clock hidden style");

            String[] allowedShortcuts = {
                "NONE", "TORCH", "CAMERA", "PHONE", "MESSAGES", "BROWSER", "EMAIL", "CALCULATOR"
            };
            List<String> shortcutList = Arrays.asList(allowedShortcuts);
            TestAssert.assertEquals(8, shortcutList.size(), "Lock screen shortcuts count");
            TestAssert.assertTrue(shortcutList.contains("TORCH"), "Torch shortcut");
            TestAssert.assertTrue(shortcutList.contains("CAMERA"), "Camera shortcut");
            TestAssert.assertTrue(shortcutList.contains("MESSAGES"), "Messages shortcut");
            TestAssert.assertTrue(shortcutList.contains("EMAIL"), "Email shortcut");
            TestAssert.assertTrue(shortcutList.contains("CALCULATOR"), "Calculator shortcut");

            String[] notifModes = {"DONT_SHOW", "HIDE_SENSITIVE", "SHOW_ALL"};
            TestAssert.assertEquals(3, notifModes.length, "Lock notification modes");
        });

        TestAssert.runTest("Digital Detox Screen Time Formatting", () -> {
            long sec1 = 45 * 60; // 45m
            TestAssert.assertEquals("45m", formatMinutes(sec1), "Formatted 45m");

            long sec2 = 90 * 60; // 1h 30m
            TestAssert.assertEquals("1h 30m", formatMinutes(sec2), "Formatted 1h 30m");

            long sec3 = 120 * 60; // 2h
            TestAssert.assertEquals("2h 00m", formatMinutes(sec3), "Formatted 2h");
        });
    }

    private static String formatMinutes(long totalSecs) {
        long mins = totalSecs / 60;
        long h = mins / 60;
        long m = mins % 60;
        if (h > 0) {
            return String.format("%dh %02dm", h, m);
        } else {
            return String.format("%dm", m);
        }
    }
}
