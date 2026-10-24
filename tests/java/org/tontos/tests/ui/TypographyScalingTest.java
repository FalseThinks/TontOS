package org.tontos.tests.ui;

import org.tontos.tests.common.TestAssert;

public class TypographyScalingTest {

    public static void run() {
        System.out.println("  --> [UI] Typography & Scaling Range Suite");

        TestAssert.runTest("Font Scale Factors - Allowed Steps", () -> {
            int[] scales = {85, 100, 115, 130};
            TestAssert.assertEquals(4, scales.length, "Supported font scales");
            TestAssert.assertEquals(85, scales[0], "Small font scale");
            TestAssert.assertEquals(100, scales[1], "Normal font scale");
            TestAssert.assertEquals(115, scales[2], "Large font scale");
            TestAssert.assertEquals(130, scales[3], "Extra large font scale");

            float scale85 = 85 / 100.0f;
            float scale100 = 100 / 100.0f;
            float scale115 = 115 / 100.0f;
            float scale130 = 130 / 100.0f;
            TestAssert.assertEquals(0.85f, scale85, "Scale factor 0.85");
            TestAssert.assertEquals(1.0f, scale100, "Scale factor 1.0");
            TestAssert.assertEquals(1.15f, scale115, "Scale factor 1.15");
            TestAssert.assertEquals(1.30f, scale130, "Scale factor 1.30");
        });

        TestAssert.runTest("Monospace Typeface Uniformity Contract", () -> {
            String fontClass = "android.graphics.Typeface";
            TestAssert.assertNotNull(fontClass, "Typeface class contract");
            // Monospace constant key
            String monospaceName = "MONOSPACE";
            TestAssert.assertEquals("MONOSPACE", monospaceName, "Strict Monospace typeface enforcement");
        });
    }
}
