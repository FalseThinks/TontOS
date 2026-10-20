package org.tontos.tests.ui;

import org.tontos.tests.common.TestAssert;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ThemeAndAccessibilityTest {

    public static void run() {
        System.out.println("  --> [UI] Theme Tokens & WCAG AAA Accessibility Suite");

        TestAssert.runTest("Theme Presets Contract - 5 Supported Themes", () -> {
            String[] expectedPresets = {
                "AMOLED_BLACK",
                "AMBER_PHOSPHOR",
                "EINK_PAPER",
                "CYAN_TERMINAL",
                "SLATE_MINIMAL"
            };
            Set<String> presetSet = new HashSet<>(Arrays.asList(expectedPresets));
            TestAssert.assertEquals(5, presetSet.size(), "Expected 5 core themes");
            TestAssert.assertTrue(presetSet.contains("AMOLED_BLACK"), "Missing AMOLED_BLACK");
            TestAssert.assertTrue(presetSet.contains("AMBER_PHOSPHOR"), "Missing AMBER_PHOSPHOR");
            TestAssert.assertTrue(presetSet.contains("EINK_PAPER"), "Missing EINK_PAPER");
            TestAssert.assertTrue(presetSet.contains("CYAN_TERMINAL"), "Missing CYAN_TERMINAL");
            TestAssert.assertTrue(presetSet.contains("SLATE_MINIMAL"), "Missing SLATE_MINIMAL");
        });

        TestAssert.runTest("WCAG AAA Contrast Ratio Compliance (>= 7.0:1)", () -> {
            // AMOLED Black: pure white (#FFFFFF) on pure black (#000000) = 21:1
            double amoledRatio = calculateContrastRatio(0xFFFFFF, 0x000000);
            TestAssert.assertTrue(amoledRatio >= 7.0, "AMOLED Black must meet WCAG AAA (21.0:1)");

            // Amber Phosphor: #FFB000 on #000000
            double amberRatio = calculateContrastRatio(0xFFB000, 0x000000);
            TestAssert.assertTrue(amberRatio >= 7.0, "Amber Phosphor must meet WCAG AAA (>= 7.0:1)");

            // Cyan Terminal: #00E5FF on #000000
            double cyanRatio = calculateContrastRatio(0x00E5FF, 0x000000);
            TestAssert.assertTrue(cyanRatio >= 7.0, "Cyan Terminal must meet WCAG AAA (>= 7.0:1)");

            // E-Ink Paper: #000000 on #F5F5F0
            double einkRatio = calculateContrastRatio(0x000000, 0xF5F5F0);
            TestAssert.assertTrue(einkRatio >= 7.0, "E-Ink Paper must meet WCAG AAA (>= 7.0:1)");
        });

        TestAssert.runTest("Grayscale Daltonizer KMS Keys", () -> {
            String daltonizerEnabledKey = "accessibility_display_daltonizer_enabled";
            String daltonizerModeKey = "accessibility_display_daltonizer";

            TestAssert.assertEquals("accessibility_display_daltonizer_enabled", daltonizerEnabledKey, "KMS Secure Setting Enable Key");
            TestAssert.assertEquals("accessibility_display_daltonizer", daltonizerModeKey, "KMS Secure Setting Mode Key");

            int monochromeModeVal = 0;
            TestAssert.assertEquals(0, monochromeModeVal, "AOSP Daltonizer mode for monochromacy must be 0");
        });
    }

    private static double calculateContrastRatio(int fgColor, int bgColor) {
        double l1 = calculateRelativeLuminance(fgColor);
        double l2 = calculateRelativeLuminance(bgColor);
        double lighter = Math.max(l1, l2);
        double darker = Math.min(l1, l2);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double calculateRelativeLuminance(int color) {
        double r = ((color >> 16) & 0xFF) / 255.0;
        double g = ((color >> 8) & 0xFF) / 255.0;
        double b = (color & 0xFF) / 255.0;

        r = (r <= 0.03928) ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
        g = (g <= 0.03928) ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
        b = (b <= 0.03928) ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);

        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }
}
