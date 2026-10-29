package com.danielh;

import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.DeploymentConfiguration;
import info.openrocket.core.rocketcomponent.FlightConfigurationId;
import info.openrocket.core.rocketcomponent.FlightConfigurableParameterSet;
import info.openrocket.core.rocketcomponent.Parachute;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.position.AxialMethod;

/**
 * One recovery parachute on every rocket the search flies.
 *
 * <p>The chute is attached in code, not stored in {@code GA_base_rocket.ork},
 * so the file's saved OpenRocket 23.09 flight log stays intact and every
 * candidate gets the same chute. It sits in the body tube, 4 cm behind the
 * nose, and it is identical for the baseline and every design. The packed
 * mass shifts the launch centre of gravity by a few millimetres on every
 * rocket the same way.
 *
 * <p>Diameter is chosen for about 6 m/s vertical speed at the burnout mass
 * (about 0.40 kg once this chute is included). With {@code Cd = 0.8}, sea-level
 * density 1.225 kg/m³ and {@code v = 6} m/s,
 * {@code A = 2 m g / (Cd ρ v²)} is about 0.22 m², so the diameter is 0.55 m.
 * A simulated base flight touches down at about 5.8 m/s vertically.
 */
public final class RecoveryChute {

    public static final String NAME = "Recovery parachute";

    /** Round-canopy drag coefficient. OpenRocket's parachute default is the same value. */
    public static final double DRAG_COEFFICIENT = 0.8;

    /** Nominal diameter, metres. See the class note for the sizing. */
    public static final double DIAMETER_METRES = 0.55;

    /**
     * Packed mass of a nylon round chute of this size, including lines and a
     * small swivel. Applied as a mass override so shroud-line material is not
     * counted a second time.
     */
    public static final double PACKED_MASS_KILOGRAMS = 0.025;

    /** How long after the apogee event the canopy opens. */
    public static final double DEPLOY_DELAY_SECONDS = 1.0;

    /** Distance from the front of the body tube back to the packed chute. */
    public static final double POSITION_FROM_BODY_FRONT_METRES = 0.04;

    public static final int LINE_COUNT = 8;
    public static final double LINE_LENGTH_METRES = DIAMETER_METRES;

    private RecoveryChute() {
    }

    /**
     * Add the recovery chute, or refresh the one already attached, and return it.
     */
    public static Parachute ensure(Rocket rocket) {
        Parachute existing = find(rocket);
        if (existing != null) {
            configure(rocket, existing);
            return existing;
        }
        BodyTube body = findBody(rocket);
        if (body == null) {
            throw new IllegalStateException("The rocket has no body tube for the recovery parachute");
        }
        Parachute chute = new Parachute();
        chute.setName(NAME);
        body.addChild(chute);
        configure(rocket, chute);
        return chute;
    }

    public static Parachute find(Rocket rocket) {
        for (RocketComponent component : rocket) {
            if (component instanceof Parachute parachute && NAME.equals(parachute.getName())) {
                return parachute;
            }
        }
        return null;
    }

    private static void configure(Rocket rocket, Parachute chute) {
        chute.setName(NAME);
        chute.setDiameter(DIAMETER_METRES);
        chute.setCDAutomatic(false);
        chute.setCD(DRAG_COEFFICIENT);
        chute.setLineCount(LINE_COUNT);
        chute.setLineLength(LINE_LENGTH_METRES);
        chute.setOverrideMass(PACKED_MASS_KILOGRAMS);
        chute.setMassOverridden(true);
        chute.setAxialMethod(AxialMethod.TOP);
        chute.setAxialOffset(POSITION_FROM_BODY_FRONT_METRES);
        applyDeployment(chute.getDeploymentConfigurations().getDefault());
        FlightConfigurableParameterSet<DeploymentConfiguration> configurations = chute.getDeploymentConfigurations();
        for (FlightConfigurationId id : configurations.getIds()) {
            applyDeployment(configurations.get(id));
        }
        FlightConfigurationId selected = rocket.getSelectedConfiguration().getFlightConfigurationID();
        if (selected != null) {
            applyDeployment(configurations.get(selected));
        }
    }

    private static void applyDeployment(DeploymentConfiguration configuration) {
        if (configuration == null) {
            return;
        }
        configuration.setDeployEvent(DeploymentConfiguration.DeployEvent.APOGEE);
        configuration.setDeployDelay(DEPLOY_DELAY_SECONDS);
    }

    private static BodyTube findBody(Rocket rocket) {
        for (RocketComponent component : rocket) {
            if (component instanceof BodyTube body) {
                return body;
            }
        }
        return null;
    }
}
