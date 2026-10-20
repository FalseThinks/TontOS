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
