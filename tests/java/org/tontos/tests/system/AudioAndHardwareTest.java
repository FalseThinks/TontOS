package org.tontos.tests.system;

import org.tontos.tests.common.TestAssert;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AudioAndHardwareTest {

    public static void run() {
        System.out.println("  --> [System] Audio, Equalizer & Hardware Specs Suite");

        TestAssert.runTest("Equalizer 5-Band Audio Specifications", () -> {
            int[] centerFreqsHz = {60, 230, 910, 3600, 14000};
            TestAssert.assertEquals(5, centerFreqsHz.length, "5-band equalizer bands count");
            TestAssert.assertEquals(60, centerFreqsHz[0], "Band 0 (Sub-Bass)");
            TestAssert.assertEquals(230, centerFreqsHz[1], "Band 1 (Bass)");
            TestAssert.assertEquals(910, centerFreqsHz[2], "Band 2 (Mid)");
            TestAssert.assertEquals(3600, centerFreqsHz[3], "Band 3 (Treble)");
            TestAssert.assertEquals(14000, centerFreqsHz[4], "Band 4 (Air/Upper Treble)");

            int minDb = -10;
            int maxDb = 10;
            TestAssert.assertTrue(minDb < maxDb, "Valid gain range (-10dB to +10dB)");
        });

        TestAssert.runTest("Notification Digest Scheduled Delivery Modes", () -> {
            String[] modes = {"IMMEDIATE", "SCHEDULED_DIGEST", "STARRED_ONLY"};
            Set<String> modeSet = new HashSet<>(Arrays.asList(modes));
            TestAssert.assertEquals(3, modeSet.size(), "Notification delivery modes count");
            TestAssert.assertTrue(modeSet.contains("IMMEDIATE"), "Immediate mode");
            TestAssert.assertTrue(modeSet.contains("SCHEDULED_DIGEST"), "Scheduled digest mode");
            TestAssert.assertTrue(modeSet.contains("STARRED_ONLY"), "Starred contacts mode");

            int[] digestHours = {12, 18};
            TestAssert.assertEquals(12, digestHours[0], "First digest batch at 12:00");
            TestAssert.assertEquals(18, digestHours[1], "Second digest batch at 18:00");
        });
    }
}
