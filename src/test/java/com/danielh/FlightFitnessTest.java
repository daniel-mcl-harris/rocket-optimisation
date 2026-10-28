package com.danielh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class FlightFitnessTest {

    @Test
    public void verdictComparesMeanApogeeWithinOneMetre() {
        assertEquals("NO SIGNIFICANT CHANGE", FlightFitness.verdict(2488.77, 2488.77));
        assertEquals("NO SIGNIFICANT CHANGE", FlightFitness.verdict(1000.0, 1000.5));
        assertEquals("NO SIGNIFICANT CHANGE", FlightFitness.verdict(1000.0, 999.2));
        assertEquals("OPTIMISATION SUCCESSFUL", FlightFitness.verdict(1000.0, 1001.1));
        assertEquals("OPTIMISATION FAILED", FlightFitness.verdict(1021.77, 849.30));
    }

    @Test
    public void stabilityOutsideOneToSixCalibresIsAWarningOnly() {
        assertTrue(FlightFitness.stabilityWarnings(3.2, 2.13, 3.87).isEmpty());

        List<String> low = FlightFitness.stabilityWarnings(0.4, 0.2, 1.5);
        assertEquals(2, low.size());
        assertTrue(low.get(0).contains("Static margin"));
        assertTrue(low.get(0).contains("below"));
        assertTrue(low.get(1).contains("Worst in-flight"));

        List<String> high = FlightFitness.stabilityWarnings(6.4, 2.0, 9.6);
        assertEquals(2, high.size());
        assertTrue(high.get(0).contains("Static margin"));
        assertTrue(high.get(0).contains("above"));
        assertTrue(high.get(1).contains("9.60"));
    }
}
