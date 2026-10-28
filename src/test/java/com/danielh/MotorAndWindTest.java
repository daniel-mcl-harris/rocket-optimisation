package com.danielh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;

import org.junit.BeforeClass;
import org.junit.Test;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.file.GeneralRocketSaver;
import info.openrocket.core.motor.Motor;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.startup.OpenRocketCore;

public class MotorAndWindTest {

    @BeforeClass
    public static void startOpenRocket() {
        OpenRocketCore.initialize();
    }

    @Test
    public void selectsI350RWithZeroDelay() {
        MotorCatalogue.Selection selection = MotorCatalogue.select("I350R-0");
        assertEquals("I350R", selection.motor.getDesignation());
        assertEquals("AeroTech", selection.motor.getManufacturer().getSimpleName());
        assertEquals(0.0, selection.ejectionDelaySeconds, 0.0);
        assertTrue(selection.describe().contains("I350R-0"));

        MotorCatalogue.Selection prefixed = MotorCatalogue.select("aerotech/i350r-0");
        assertEquals(selection.motor.getDigest(), prefixed.motor.getDigest());
    }

    @Test
    public void omittedDelayUsesTheShortestListedDelay() {
        MotorCatalogue.Selection selection = MotorCatalogue.select("I350R");
        assertEquals("I350R", selection.motor.getDesignation());
        assertEquals(Motor.PLUGGED_DELAY, selection.ejectionDelaySeconds, 0.0);
    }

    @Test
    public void unknownMotorNamesTheRequestAndOffersMatches() {
        try {
            MotorCatalogue.select("ZZZ999");
            fail("expected a lookup error");
        } catch (MotorCatalogue.MotorLookupException e) {
            assertTrue(e.getMessage().contains("ZZZ999"));
            assertTrue(e.getMessage().contains("No motor matches"));
            assertTrue(e.getMessage().contains("Close matches:"));
        }
    }

    @Test
    public void ambiguousMotorListsManufacturers() {
        try {
            MotorCatalogue.select("G80");
            fail("expected an ambiguous motor");
        } catch (MotorCatalogue.MotorLookupException e) {
            assertTrue(e.getMessage().toLowerCase().contains("more than one"));
            assertTrue(e.getMessage().contains("AeroTech") || e.getMessage().contains("Cesaroni")
                    || e.getMessage().chars().filter(ch -> ch == '\n').count() >= 2);
        }
    }

    @Test
    public void mountedMotorChangesMassAndIsSaved() throws Exception {
        OpenRocketDocument document = new GeneralRocketLoader(new File("rockets", "GA_base_rocket.ork")).load();
        Rocket original = document.getRocket();
        MotorCatalogue.Selection heavy = MotorCatalogue.select("I350R-0");
        MotorCatalogue.mount(original, heavy);
        RocketAnalysis.MassProperties heavyMass = RocketAnalysis.massProperties(original);
        assertEquals("I350R", heavyMass.motorDesignation);
        assertEquals(0.7236, heavyMass.launchMass, 0.02);

        Rocket copy = original.copyWithOriginalID();
        assertEquals(heavyMass.launchMass, RocketAnalysis.massProperties(copy).launchMass, 0.001);

        MotorCatalogue.Selection light = MotorCatalogue.select("H128W-6");
        MotorCatalogue.mount(original, light);
        RocketAnalysis.MassProperties lightMass = RocketAnalysis.massProperties(original);
        assertEquals("H128W", lightMass.motorDesignation);
        assertTrue("lighter motor should drop the launch mass", lightMass.launchMass < heavyMass.launchMass - 0.2);
        assertNotEquals(heavyMass.launchCenterOfGravity, lightMass.launchCenterOfGravity, 0.005);

        File saved = new File("target", "motor-swap-test.ork");
        saved.getParentFile().mkdirs();
        new GeneralRocketSaver().save(saved, document);
        Rocket reloaded = new GeneralRocketLoader(saved).load().getRocket();
        assertEquals("H128W", RocketAnalysis.massProperties(reloaded).motorDesignation);
        assertEquals(lightMass.launchMass, RocketAnalysis.massProperties(reloaded).launchMass, 0.001);
    }

    @Test
    public void windEnsembleIsRepeatableAndReportsTheWorstCase() throws Exception {
        Rocket rocket = loadBase();
        MotorCatalogue.mount(rocket, MotorCatalogue.select("I350R-0"));

        RocketAnalysis.Ensemble first = RocketAnalysis.flyAllWinds(rocket);
        RocketAnalysis.Ensemble second = RocketAnalysis.flyAllWinds(rocket);
        assertEquals(WindCases.CASES.size(), first.cases.length);
        assertEquals(first.average.apogee, second.average.apogee, 1e-6);
        assertEquals(first.minimumInFlightStability, second.minimumInFlightStability, 1e-6);

        double mean = 0.0;
        double worst = Double.POSITIVE_INFINITY;
        for (RocketAnalysis.FlightResult flight : first.cases) {
            assertTrue(flight.apogee > 500.0);
            mean += flight.apogee;
            worst = Math.min(worst, flight.minStabilityCalibres);
        }
        assertEquals(mean / first.cases.length, first.average.apogee, 1e-6);
        assertEquals(worst, first.minimumInFlightStability, 1e-9);
        assertTrue(first.caseLines[0].startsWith("    calm"));
        assertTrue(first.caseLines[2].startsWith("    breezy"));

        System.out.printf("wind mean apogee %.2f m, min stability %.2f cal%n",
                first.average.apogee, first.minimumInFlightStability);
        for (String line : first.caseLines) {
            System.out.println(line);
        }
    }

    private static Rocket loadBase() throws Exception {
        return new GeneralRocketLoader(new File("rockets", "GA_base_rocket.ork")).load().getRocket();
    }
}
