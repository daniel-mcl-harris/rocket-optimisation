package com.danielh;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.Test;

public class OptimisationReportTest {

    @Test
    public void reportIsOneBeforeAndAfterTable() {
        OptimisationReport report = new OptimisationReport(3, 1);
        report.motorDescription = "AeroTech I350R-P";
        report.executionTimeMs = 2000;
        report.baselineNoseCone = 0.1760;
        report.optimisedNoseCone = 0.4981;
        report.baselineBodyTube = 0.4389;
        report.optimisedBodyTube = 0.6123;
        report.baselineFinRootChord = 0.1000;
        report.optimisedFinRootChord = 0.1305;
        report.baselineFinTipChord = 0.1100;
        report.optimisedFinTipChord = 0.0709;
        report.baselineFinHeight = 0.1100;
        report.optimisedFinHeight = 0.0781;
        report.baselineFinSweepLength = 0.0711;
        report.optimisedFinSweepLength = 0.0141;
        report.baselineCenterOfPressure = 0.5551;
        report.optimisedCenterOfPressure = 0.5817;
        report.baselineCenterOfGravity = 0.4305;
        report.optimisedCenterOfGravity = 0.5123;
        report.baselineLaunchMass = 0.7236;
        report.optimisedLaunchMass = 0.8100;
        report.baselineStabilityMarginCalibres = 3.20;
        report.optimisedStabilityMarginCalibres = 1.78;
        report.baselineApogee = 2488.77;
        report.optimisedApogee = 2553.90;
        report.baselineTimeToApogee = 14.79;
        report.optimisedTimeToApogee = 15.10;
        report.baselineFlightTime = 49.78;
        report.optimisedFlightTime = 50.20;
        report.baselineMaxVelocity = 807.95;
        report.optimisedMaxVelocity = 781.59;
        report.baselineMaxAcceleration = 698.05;
        report.optimisedMaxAcceleration = 640.00;
        report.baselineMaxMach = 2.374;
        report.optimisedMaxMach = 2.300;
        report.baselineLaunchRodVelocity = 33.09;
        report.optimisedLaunchRodVelocity = 31.50;
        report.baselineMinFlightStabilityCalibres = 2.13;
        report.optimisedMinFlightStabilityCalibres = -21.70;
        report.baselineMaxFlightStabilityCalibres = 3.87;
        report.optimisedMaxFlightStabilityCalibres = 3.29;

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream previous = System.out;
        System.setOut(new PrintStream(buffer));
        try {
            report.generateReport();
        } finally {
            System.setOut(previous);
        }
        String text = buffer.toString();
        assertTrue(text.contains("Parameter"));
        assertTrue(text.contains("Base"));
        assertTrue(text.contains("Optimised"));
        assertTrue(text.contains("Change"));
        assertTrue(text.contains("Nose cone length (m)"));
        assertTrue(text.contains("0.1760"));
        assertTrue(text.contains("0.4981"));
        assertTrue(text.contains("+0.3221"));
        assertTrue(text.contains("Mean apogee (m)"));
        assertTrue(text.contains("+65.13"));
        assertTrue(text.contains("Max Mach"));
        assertTrue(text.contains("OPTIMISATION SUCCESSFUL"));
        assertTrue(text.contains("WARNING: stability is outside 1 to 6 calibres"));
        assertTrue(text.contains("Worst in-flight margin is -21.70 cal"));
        assertFalse(text.contains("[BASE DESIGN]"));
        assertFalse(text.contains("[OPTIMISED DESIGN]"));
        assertFalse(text.contains("Component Breakdown"));
    }
}
