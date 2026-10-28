package com.danielh;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FlightFitnessTest {

    @Test
    public void penaltyRemovesApogeeBelowTwoCalibres() {
        assertEquals(0.0, FlightFitness.penalty(2.0), 1e-9);
        assertEquals(0.0, FlightFitness.penalty(3.2), 1e-9);
        assertEquals(1.0, FlightFitness.penalty(0.0), 1e-9);
        assertEquals(1.0, FlightFitness.penalty(-0.4), 1e-9);

        double partial = 1.0 - Math.exp(-(2.0 - 1.23));
        assertEquals(partial, FlightFitness.penalty(1.23), 1e-9);
        assertEquals(1000.0 * (1.0 - partial), FlightFitness.score(1000.0, 1.23), 1e-6);
    }

    @Test
    public void governingMarginUsesTheWorseOfStaticAndFlight() {
        assertEquals(1.23, FlightFitness.governingMargin(2.03, 1.23), 0.0);
        assertEquals(2.13, FlightFitness.governingMargin(3.20, 2.13), 0.0);
        assertEquals(3.20, FlightFitness.governingMargin(3.20, Double.NaN), 0.0);
    }

    @Test
    public void verdictFollowsFitnessNotRawApogee() {
        double basePenalty = FlightFitness.penalty(1.23);
        double baseScore = FlightFitness.score(1021.77, 1.23);
        double optimisedScore = FlightFitness.score(849.30, 2.16);

        assertEquals(0.0, FlightFitness.penalty(2.16), 0.0);
        assertEquals("OPTIMISATION SUCCESSFUL", FlightFitness.verdict(baseScore, optimisedScore));
        assertEquals(1021.77 * (1.0 - basePenalty), baseScore, 1e-6);

        assertEquals("NO SIGNIFICANT CHANGE", FlightFitness.verdict(2488.77, 2488.77));
        assertEquals("NO SIGNIFICANT CHANGE", FlightFitness.verdict(1000.0, 1000.5));
        assertEquals("OPTIMISATION FAILED", FlightFitness.verdict(1000.0, 998.0));
    }
}
