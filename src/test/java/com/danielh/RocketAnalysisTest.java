package com.danielh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.BeforeClass;
import org.junit.Test;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.startup.OpenRocketCore;

/**
 * Checks the base rocket against the flight data stored in the .ork by OpenRocket 23.09,
 * then re-simulates that same simulation with OpenRocket 24.12.
 */
public class RocketAnalysisTest {

    @BeforeClass
    public static void startOpenRocket() {
        OpenRocketCore.initialize();
    }

    @Test
    public void baseRocketMassAndFlightMatchOpenRocket() throws Exception {
        File rocketFile = new File("rockets", "GA_base_rocket.ork");
        assertTrue(rocketFile.isFile());

        OpenRocketDocument document = new GeneralRocketLoader(rocketFile).load();
        RocketAnalysis.MassProperties mass = RocketAnalysis.massProperties(document.getRocket());

        System.out.printf("launch mass %.4f kg, dry %.4f kg, burnout %.4f kg%n",
                mass.launchMass, mass.dryMass, mass.burnoutMass);
        System.out.printf("launch CG %.4f m, burnout CG %.4f m, ref %.4f m%n",
                mass.launchCenterOfGravity, mass.burnoutCenterOfGravity, mass.referenceLength);
        System.out.printf("CP worst %.4f m (CNa %.3f), CP theta0 %.4f m, stability %.3f cal (theta0 %.3f)%n",
                mass.centerOfPressure, mass.centerOfPressureCNa, mass.centerOfPressureZeroAngle,
                mass.stabilityCalibres, mass.stabilityCalibresZeroAngle);
        System.out.println("motor " + mass.motorDesignation);
        for (String line : mass.componentBreakdown) {
            System.out.println(line);
        }

        // First stored sample in the .ork (OpenRocket 23.09): mass 0.723 kg, CG 0.43 m.
        assertEquals("I350R", mass.motorDesignation);
        assertEquals(0.723, mass.launchMass, 0.03);
        assertEquals(0.430, mass.launchCenterOfGravity, 0.02);
        assertEquals(0.039, mass.referenceLength, 0.001);
        assertTrue(mass.dryMass > 0.02 && mass.dryMass < mass.launchMass);
        assertTrue(mass.burnoutMass > mass.dryMass && mass.burnoutMass < mass.launchMass);
        assertTrue("CP should be aft of the launch CG", mass.centerOfPressure > mass.launchCenterOfGravity);
        assertTrue(mass.stabilityCalibres > 1.5 && mass.stabilityCalibres < 6.0);
        assertEquals(mass.centerOfPressure, mass.centerOfPressureZeroAngle, 0.005);
        assertEquals(4, mass.componentBreakdown.length);
        String breakdown = String.join("\n", mass.componentBreakdown);
        assertTrue(breakdown.contains("Nose Cone"));
        assertTrue(breakdown.contains("Body Tube"));
        assertTrue(breakdown.contains("Trapezoidal Fin Set"));
        assertTrue(breakdown.contains("I350R"));
        assertFalse(breakdown.contains("Sustainer"));

        long cpStart = System.nanoTime();
        RocketAnalysis.launchStabilityCalibres(document.getRocket());
        double cpMillis = (System.nanoTime() - cpStart) / 1.0e6;
        System.out.printf("launchStabilityCalibres %.1f ms%n", cpMillis);

        Simulation simulation = document.getSimulation(0);
        assertNotNull(simulation);
        FlightData stored = simulation.getSimulatedData();
        if (stored != null) {
            System.out.printf("stored 23.09 data: apogee %.2f m, Vmax %.2f m/s, rod %.2f m/s%n",
                    stored.getMaxAltitude(), stored.getMaxVelocity(), stored.getLaunchRodVelocity());
        }

        simulation.simulate();
        FlightData resimulated = simulation.getSimulatedData();
        RocketAnalysis.FlightResult flight = RocketAnalysis.flightResult(resimulated);
        assertNotNull(flight);
        System.out.printf("24.12 resim: apogee %.2f m, tAp %.2f s, Vmax %.2f m/s, Amax %.2f m/s2, rod %.2f m/s, "
                        + "stab %.2f..%.2f cal%n",
                flight.apogee, flight.timeToApogee, flight.maxVelocity, flight.maxAcceleration,
                flight.launchRodVelocity, flight.minStabilityCalibres, flight.maxStabilityCalibres);

        // Saved OpenRocket 23.09 summary: apogee 2505.418 m, Vmax 807.961 m/s, rod 33.65 m/s.
        assertEquals(2505.418, flight.apogee, 150.0);
        assertEquals(807.961, flight.maxVelocity, 40.0);
        assertEquals(33.65, flight.launchRodVelocity, 5.0);
        assertTrue(flight.maxAcceleration > 100.0);
        assertTrue(flight.minStabilityCalibres > 0.5);
        assertTrue(flight.maxStabilityCalibres >= flight.minStabilityCalibres);
    }
}
