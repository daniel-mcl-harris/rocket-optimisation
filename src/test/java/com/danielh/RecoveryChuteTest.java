package com.danielh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.BeforeClass;
import org.junit.Test;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.logging.Warning;
import info.openrocket.core.rocketcomponent.DeploymentConfiguration;
import info.openrocket.core.rocketcomponent.Parachute;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.simulation.FlightDataBranch;
import info.openrocket.core.simulation.FlightDataType;
import info.openrocket.core.simulation.FlightEvent;
import info.openrocket.core.startup.OpenRocketCore;

public class RecoveryChuteTest {

    @BeforeClass
    public static void startOpenRocket() {
        OpenRocketCore.initialize();
    }

    @Test
    public void baseRocketDeploysAtApogeeAndDescendsInRange() throws Exception {
        OpenRocketDocument document = new GeneralRocketLoader(new File("rockets", "GA_base_rocket.ork")).load();
        Rocket rocket = document.getRocket();
        MotorCatalogue.mount(rocket, MotorCatalogue.select("I350R"));

        Parachute chute = RecoveryChute.ensure(rocket);
        assertSame(chute, RecoveryChute.ensure(rocket));
        assertEquals(1, parachuteCount(rocket));
        assertEquals(RecoveryChute.DIAMETER_METRES, chute.getDiameter(), 1e-9);
        assertEquals(RecoveryChute.DRAG_COEFFICIENT, chute.getCD(), 1e-9);
        assertFalse(chute.isCDAutomatic());
        assertEquals(RecoveryChute.PACKED_MASS_KILOGRAMS, chute.getMass(), 1e-6);

        DeploymentConfiguration deployment = chute.getDeploymentConfigurations().getDefault();
        assertEquals(DeploymentConfiguration.DeployEvent.APOGEE, deployment.getDeployEvent());
        assertEquals(RecoveryChute.DEPLOY_DELAY_SECONDS, deployment.getDeployDelay(), 1e-9);

        RocketAnalysis.MassProperties mass = RocketAnalysis.massProperties(rocket);
        assertTrue(mass.launchMass > 0.74 && mass.launchMass < 0.76);
        assertTrue(mass.stabilityCalibres > 2.5 && mass.stabilityCalibres < 4.5);
        assertTrue(String.join("\n", mass.componentBreakdown).contains(RecoveryChute.NAME));

        RocketAnalysis.Ensemble ensemble = RocketAnalysis.flyAllWinds(rocket);
        assertFalse(ensemble.rejectionReason, ensemble.rejected);
        assertTrue("descent " + ensemble.average.descentRate,
                ensemble.average.descentRate >= 5.0 && ensemble.average.descentRate <= 7.0);
        assertTrue("deployment " + ensemble.average.deploymentSpeed,
                ensemble.average.deploymentSpeed >= 5.0 && ensemble.average.deploymentSpeed <= 20.0);
        for (RocketAnalysis.FlightResult flight : ensemble.cases) {
            assertTrue(flight.descentRate >= 5.0 && flight.descentRate <= 7.0);
            assertTrue(flight.deploymentSpeed >= 5.0 && flight.deploymentSpeed <= 20.0);
        }

        Simulation simulation = new Simulation(rocket.copyWithOriginalID());
        RocketAnalysis.applyWind(simulation.getOptions(), WindCases.CASES.get(0));
        simulation.simulate();
        FlightData data = simulation.getSimulatedData();
        FlightDataBranch branch = data.getBranch(0);
        FlightEvent deployed = branch.getFirstEvent(FlightEvent.Type.RECOVERY_DEVICE_DEPLOYMENT);
        assertNotNull(deployed);
        assertTrue(deployed.getTime() > data.getTimeToApogee());
        assertTrue(deployed.getTime() < data.getTimeToApogee() + 2.0);
        assertNull(branch.getFirstEvent(FlightEvent.Type.TUMBLE));
        for (Warning warning : data.getWarningSet()) {
            assertFalse(warning.toString().toLowerCase().contains("no recovery device"));
        }
        assertFalse(tailFirstAfterDeployment(branch, deployed.getTime()));
    }

    private static boolean tailFirstAfterDeployment(FlightDataBranch branch, double deploymentTime) {
        java.util.List<Double> time = branch.get(FlightDataType.TYPE_TIME);
        java.util.List<Double> angle = branch.get(FlightDataType.TYPE_AOA);
        java.util.List<Double> velocity = branch.get(FlightDataType.TYPE_VELOCITY_TOTAL);
        if (time == null || angle == null || velocity == null) {
            return false;
        }
        int length = Math.min(time.size(), Math.min(angle.size(), velocity.size()));
        for (int i = 0; i < length; i++) {
            if (time.get(i) == null || time.get(i) < deploymentTime + 5.0) {
                continue;
            }
            Double aoa = angle.get(i);
            Double speed = velocity.get(i);
            if (aoa == null || speed == null || aoa.isNaN() || speed.isNaN()) {
                continue;
            }
            if (Math.toDegrees(aoa) > 90.0 && speed > 15.0) {
                return true;
            }
        }
        return false;
    }

    private static int parachuteCount(Rocket rocket) {
        int count = 0;
        for (RocketComponent component : rocket) {
            if (component instanceof Parachute) {
                count++;
            }
        }
        return count;
    }
}
