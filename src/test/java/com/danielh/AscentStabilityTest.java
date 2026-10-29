package com.danielh;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import org.junit.BeforeClass;
import org.junit.Test;

import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.logging.SimulationAbort;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.simulation.FlightDataBranch;
import info.openrocket.core.simulation.FlightDataType;
import info.openrocket.core.simulation.FlightEvent;
import info.openrocket.core.startup.OpenRocketCore;

public class AscentStabilityTest {

    @BeforeClass
    public static void startOpenRocket() {
        OpenRocketCore.initialize();
    }

    @Test
    public void stableAscentPasses() {
        FlightData data = flight(0.08, 15.0, series(0.10, 12.0, 0.25, 120.0, 2.5, 2.0), null, 0.0);
        AscentStability.Result result = AscentStability.assess(data);
        assertFalse(result.reason, result.rejected);
    }

    @Test
    public void lowMarginWhileFastFails() {
        double[][] rows = join(
                series(0.10, 0.30, 0.02, 80.0, 0.2, 4.0),
                series(0.32, 12.0, 0.25, 120.0, 2.5, 2.0));
        AscentStability.Result result = AscentStability.assess(flight(0.08, 15.0, rows, null, 0.0));
        assertTrue(result.reason, result.rejected);
        assertTrue(result.reason, result.reason.contains("stability below 1.0 cal"));
    }

    @Test
    public void singleFastStepUnderTheDurationPasses() {
        double[][] rows = join(
                new double[][] { {0.10, 20.0, 80.0, 0.2, 4.0} },
                series(0.12, 12.0, 0.25, 120.0, 2.5, 2.0));
        AscentStability.Result result = AscentStability.assess(flight(0.08, 15.0, rows, null, 0.0));
        assertFalse(result.reason, result.rejected);
    }

    @Test
    public void marginHeldForTheLimitPassesAndJustOverFails() {
        AscentStability.Result atLimit = AscentStability.assess(flight(0.08, 15.0,
                marginSpan(5, 0.01), null, 0.0));
        assertFalse(atLimit.reason, atLimit.rejected);

        AscentStability.Result over = AscentStability.assess(flight(0.08, 15.0,
                marginSpan(6, 0.01), null, 0.0));
        assertTrue(over.reason, over.rejected);
    }

    @Test
    public void tumbleJustBeforeApogeeFailsEvenWhenSlow() {
        double[][] rows = join(
                series(0.10, 12.0, 0.25, 120.0, 2.5, 2.0),
                new double[][] {
                        {14.70, 2490.0, 4.0, -0.5, 14.0},
                        {14.85, 2500.0, 2.5, -28.0, 40.0}
                });
        AscentStability.Result result = AscentStability.assess(
                flight(0.08, 15.0, rows, FlightEvent.Type.TUMBLE, 14.80));
        assertTrue(result.reason, result.rejected);
        assertTrue(result.reason, result.reason.contains("tumble at 14.80 s"));
    }

    @Test
    public void nearApogeeLowSpeedDipIsIgnored() {
        double[][] rows = join(
                series(0.10, 12.0, 0.25, 120.0, 2.5, 2.0),
                new double[][] {
                        {14.50, 2480.0, 5.0, -10.0, 30.0},
                        {14.80, 2495.0, 2.0, -28.0, 50.0},
                        {14.95, 2500.0, 0.5, -28.0, 70.0}
                });
        AscentStability.Result result = AscentStability.assess(flight(0.08, 15.0, rows, null, 0.0));
        assertFalse(result.reason, result.rejected);
    }

    @Test
    public void highAngleOfAttackWhileFastFails() {
        double[][] rows = join(
                series(0.10, 0.40, 0.02, 90.0, 2.5, 25.0),
                series(0.42, 12.0, 0.25, 120.0, 2.5, 2.0));
        AscentStability.Result result = AscentStability.assess(flight(0.08, 15.0, rows, null, 0.0));
        assertTrue(result.reason, result.rejected);
        assertTrue(result.reason, result.reason.contains("angle of attack above 20 deg"));
    }

    @Test
    public void highAngleOfAttackNearApogeeIsIgnored() {
        double[][] rows = join(
                series(0.10, 12.0, 0.25, 120.0, 2.5, 2.0),
                new double[][] {
                        {14.60, 2485.0, 8.0, 2.0, 45.0},
                        {14.90, 2498.0, 3.0, 1.5, 60.0}
                });
        AscentStability.Result result = AscentStability.assess(flight(0.08, 15.0, rows, null, 0.0));
        assertFalse(result.reason, result.rejected);
    }

    @Test
    public void shortUnstableBoostIsNotBlankedByTheApogeeWindow() {
        AscentStability.Result result = AscentStability.assess(
                flight(0.08, 0.40, series(0.08, 0.30, 0.02, 100.0, -4.0, 2.0), null, 0.0));
        assertTrue(result.reason, result.rejected);
        assertTrue(result.reason, result.reason.contains("stability below 1.0 cal"));
    }

    @Test
    public void simulationAbortFails() {
        AscentStability.Result result = AscentStability.assess(flight(0.08, 15.0,
                series(0.10, 2.0, 0.1, 80.0, 2.5, 2.0),
                new FlightEvent(FlightEvent.Type.SIM_ABORT, 2.0, null,
                        new SimulationAbort(SimulationAbort.Cause.TUMBLE_UNDER_THRUST))));
        assertTrue(result.reason, result.rejected);
        assertTrue(result.reason, result.reason.contains("simulation aborted"));
    }

    @Test
    public void sampleGapIsCapped() {
        double[][] rows = new double[][] {
                {1.0, 100.0, 80.0, 0.0, 2.0},
                {3.0, 200.0, 80.0, 2.5, 2.0},
                {12.0, 1000.0, 80.0, 2.5, 2.0}
        };
        AscentStability.Result result = AscentStability.assess(flight(0.08, 15.0, rows, null, 0.0));
        assertTrue(result.reason, result.rejected);
        assertTrue(result.reason, result.reason.contains("0.50 s"));
    }

    @Test
    public void baseRocketPassesAndTinyFinsFail() throws Exception {
        MotorCatalogue.Selection motor = MotorCatalogue.select("I350R");
        Rocket base = new GeneralRocketLoader(new File("rockets", "GA_base_rocket.ork")).load().getRocket();
        MotorCatalogue.mount(base, motor);
        RocketAnalysis.Ensemble baseFlight = RocketAnalysis.flyAllWinds(base);
        assertFalse(baseFlight.rejectionReason, baseFlight.rejected);

        Rocket tiny = new GeneralRocketLoader(new File("rockets", "GA_base_rocket.ork")).load().getRocket();
        MotorCatalogue.mount(tiny, motor);
        setFins(tiny, 0.04, 0.01, 0.01, 0.0);
        RocketAnalysis.Ensemble tinyFlight = RocketAnalysis.flyAllWinds(tiny);
        assertTrue(tinyFlight.rejectionReason, tinyFlight.rejected);
        assertTrue(tinyFlight.rejectionReason, tinyFlight.rejectionReason.contains("stability below 1.0 cal"));
        for (WindCases.Case wind : WindCases.CASES) {
            assertTrue(tinyFlight.rejectionReason, tinyFlight.rejectionReason.contains(wind.name));
        }
    }

    /** {@code badSteps} samples of low margin, each {@code step} seconds apart, then a stable climb. */
    private static double[][] marginSpan(int badSteps, double step) {
        double[][] bad = new double[badSteps][];
        for (int i = 0; i < badSteps; i++) {
            double t = 0.10 + i * step;
            bad[i] = new double[] {t, 50.0 + t, 80.0, 0.2, 3.0};
        }
        return join(bad, series(0.10 + badSteps * step, 12.0, 0.25, 120.0, 2.5, 2.0));
    }

    private static double[][] join(double[][] first, double[][] second) {
        double[][] rows = new double[first.length + second.length][];
        System.arraycopy(first, 0, rows, 0, first.length);
        System.arraycopy(second, 0, rows, first.length, second.length);
        return rows;
    }

    private static double[][] series(double start, double end, double step, double velocity, double stability, double aoaDegrees) {
        int count = 0;
        for (double t = start; t <= end + 1e-9; t += step) {
            count++;
        }
        double[][] rows = new double[count][];
        int index = 0;
        for (double t = start; t <= end + 1e-9; t += step) {
            rows[index++] = new double[] {t, 100.0 + 40.0 * t, velocity, stability, aoaDegrees};
        }
        return rows;
    }

    private static FlightData flight(double rodTime, double apogeeTime, double[][] samples,
                                     FlightEvent.Type extra, double extraTime) {
        FlightEvent extraEvent = extra == null ? null : new FlightEvent(extra, extraTime);
        return flight(rodTime, apogeeTime, samples, extraEvent);
    }

    private static FlightData flight(double rodTime, double apogeeTime, double[][] samples, FlightEvent extra) {
        FlightDataBranch branch = new FlightDataBranch(
                "Sustainer",
                FlightDataType.TYPE_TIME,
                FlightDataType.TYPE_ALTITUDE,
                FlightDataType.TYPE_VELOCITY_TOTAL,
                FlightDataType.TYPE_STABILITY,
                FlightDataType.TYPE_AOA);
        branch.addEvent(new FlightEvent(FlightEvent.Type.LAUNCHROD, rodTime));
        branch.addEvent(new FlightEvent(FlightEvent.Type.APOGEE, apogeeTime));
        if (extra != null) {
            branch.addEvent(extra);
        }
        for (double[] sample : samples) {
            branch.addPoint();
            branch.setValue(FlightDataType.TYPE_TIME, sample[0]);
            branch.setValue(FlightDataType.TYPE_ALTITUDE, sample[1]);
            branch.setValue(FlightDataType.TYPE_VELOCITY_TOTAL, sample[2]);
            branch.setValue(FlightDataType.TYPE_STABILITY, sample[3]);
            branch.setValue(FlightDataType.TYPE_AOA, Math.toRadians(sample[4]));
        }
        return new FlightData(branch);
    }

    private static void setFins(RocketComponent component, double root, double tip, double height, double sweep) {
        for (RocketComponent child : component.getChildren()) {
            if (child.getClass().getSimpleName().contains("Fin")) {
                try {
                    child.getClass().getMethod("setRootChord", double.class).invoke(child, root);
                    child.getClass().getMethod("setTipChord", double.class).invoke(child, tip);
                    child.getClass().getMethod("setHeight", double.class).invoke(child, height);
                    child.getClass().getMethod("setSweep", double.class).invoke(child, sweep);
                } catch (ReflectiveOperationException failure) {
                    throw new AssertionError(failure);
                }
            }
            setFins(child, root, tip, height, sweep);
        }
    }
}
