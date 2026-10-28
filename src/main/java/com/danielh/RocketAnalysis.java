package com.danielh;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import info.openrocket.core.aerodynamics.BarrowmanCalculator;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.masscalc.CMAnalysisEntry;
import info.openrocket.core.masscalc.MassCalculator;
import info.openrocket.core.masscalc.RigidBody;
import info.openrocket.core.motor.Motor;
import info.openrocket.core.motor.MotorConfiguration;
import info.openrocket.core.rocketcomponent.ComponentAssembly;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.simulation.FlightDataBranch;
import info.openrocket.core.simulation.FlightDataType;
import info.openrocket.core.simulation.exception.SimulationException;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.Coordinate;
import info.openrocket.core.util.MathUtil;

/**
 * Headless OpenRocket queries used by the optimiser.
 *
 * <p>Static CP, CG and stability match the rocket-figure readout in OpenRocket
 * 24.12 ({@code RocketPanel.updateExtras}): Barrowman CP via
 * {@link BarrowmanCalculator#getWorstCP} at the default Mach and zero angle of
 * attack, launch CG from {@link MassCalculator#calculateLaunch}, and stability
 * in calibres as {@code (CP.x - CG.x) / FlightConfiguration.getReferenceLength()}.
 * {@link BarrowmanCalculator#getCP} at wind angle zero is recorded as well.
 */
public final class RocketAnalysis {

    private RocketAnalysis() {
    }

    public static final class MassProperties {
        /** Most-forward Barrowman CP, metres from the nose. This is the design-figure CP. */
        public final double centerOfPressure;
        /** CNa weight of {@link #centerOfPressure}. Zero means the CP is undefined. */
        public final double centerOfPressureCNa;
        /** Barrowman CP at wind angle zero, metres from the nose. */
        public final double centerOfPressureZeroAngle;
        public final double launchCenterOfGravity;
        public final double launchMass;
        public final double burnoutCenterOfGravity;
        public final double burnoutMass;
        public final double dryMass;
        /** Reference length used to convert metres of margin into calibres. */
        public final double referenceLength;
        /** {@code (centerOfPressure - launchCenterOfGravity) / referenceLength}. */
        public final double stabilityCalibres;
        /** Same margin using {@link #centerOfPressureZeroAngle}. */
        public final double stabilityCalibresZeroAngle;
        public final String motorDesignation;
        public final String[] componentBreakdown;

        MassProperties(double centerOfPressure, double centerOfPressureCNa, double centerOfPressureZeroAngle,
                       double launchCenterOfGravity, double launchMass, double burnoutCenterOfGravity,
                       double burnoutMass, double dryMass, double referenceLength, double stabilityCalibres,
                       double stabilityCalibresZeroAngle, String motorDesignation, String[] componentBreakdown) {
            this.centerOfPressure = centerOfPressure;
            this.centerOfPressureCNa = centerOfPressureCNa;
            this.centerOfPressureZeroAngle = centerOfPressureZeroAngle;
            this.launchCenterOfGravity = launchCenterOfGravity;
            this.launchMass = launchMass;
            this.burnoutCenterOfGravity = burnoutCenterOfGravity;
            this.burnoutMass = burnoutMass;
            this.dryMass = dryMass;
            this.referenceLength = referenceLength;
            this.stabilityCalibres = stabilityCalibres;
            this.stabilityCalibresZeroAngle = stabilityCalibresZeroAngle;
            this.motorDesignation = motorDesignation;
            this.componentBreakdown = componentBreakdown;
        }
    }

    public static final class FlightResult {
        public final double apogee;
        public final double timeToApogee;
        public final double maxVelocity;
        public final double maxAcceleration;
        public final double launchRodVelocity;
        public final double flightTime;
        public final double minStabilityCalibres;
        public final double maxStabilityCalibres;

        FlightResult(double apogee, double timeToApogee, double maxVelocity, double maxAcceleration,
                     double launchRodVelocity, double flightTime, double minStabilityCalibres,
                     double maxStabilityCalibres) {
            this.apogee = apogee;
            this.timeToApogee = timeToApogee;
            this.maxVelocity = maxVelocity;
            this.maxAcceleration = maxAcceleration;
            this.launchRodVelocity = launchRodVelocity;
            this.flightTime = flightTime;
            this.minStabilityCalibres = minStabilityCalibres;
            this.maxStabilityCalibres = maxStabilityCalibres;
        }
    }

    public static MassProperties massProperties(Rocket rocket) {
        FlightConfiguration config = rocket.getSelectedConfiguration();
        FlightConditions conditions = new FlightConditions(config);
        conditions.setMach(defaultMach());
        conditions.setAOA(0);
        conditions.setRollRate(0);

        WarningSet warnings = new WarningSet();
        BarrowmanCalculator calculator = new BarrowmanCalculator();
        Coordinate cpZero = calculator.getCP(config, conditions, warnings);
        Coordinate cpWorst = calculator.getWorstCP(config, conditions, warnings);

        RigidBody launch = MassCalculator.calculateLaunch(config);
        RigidBody burnout = MassCalculator.calculateBurnout(config);
        RigidBody structure = MassCalculator.calculateStructure(config);

        double referenceLength = config.getReferenceLength();
        double launchCg = launch.getCM().x;
        double stability = stabilityCalibres(cpWorst, launchCg, referenceLength);
        double stabilityZero = stabilityCalibres(cpZero, launchCg, referenceLength);

        return new MassProperties(
                cpWorst.x,
                cpWorst.weight,
                cpZero.x,
                launchCg,
                launch.getMass(),
                burnout.getCM().x,
                burnout.getMass(),
                structure.getMass(),
                referenceLength,
                stability,
                stabilityZero,
                mountedMotor(config),
                componentBreakdown(config));
    }

    /**
     * Launch stability in calibres, using the same CP and CG as {@link #massProperties}.
     * Skips the per-component breakdown so the GA loop does not repeat that walk.
     */
    public static double launchStabilityCalibres(Rocket rocket) {
        FlightConfiguration config = rocket.getSelectedConfiguration();
        FlightConditions conditions = new FlightConditions(config);
        conditions.setMach(defaultMach());
        conditions.setAOA(0);
        conditions.setRollRate(0);

        WarningSet warnings = new WarningSet();
        Coordinate cp = new BarrowmanCalculator().getWorstCP(config, conditions, warnings);
        RigidBody launch = MassCalculator.calculateLaunch(config);
        return stabilityCalibres(cp, launch.getCM().x, config.getReferenceLength());
    }

    public static FlightResult simulate(Rocket rocket) throws SimulationException {
        Simulation simulation = new Simulation(rocket);
        simulation.simulate();
        return flightResult(simulation.getSimulatedData());
    }

    public static FlightResult flightResult(FlightData data) {
        if (data == null) {
            return null;
        }
        double minStability = Double.NaN;
        double maxStability = Double.NaN;
        if (data.getBranchCount() > 0) {
            FlightDataBranch branch = data.getBranch(0);
            List<Double> stability = branch.get(FlightDataType.TYPE_STABILITY);
            minStability = finiteExtreme(stability, true);
            maxStability = finiteExtreme(stability, false);
        }
        return new FlightResult(
                data.getMaxAltitude(),
                data.getTimeToApogee(),
                data.getMaxVelocity(),
                data.getMaxAcceleration(),
                data.getLaunchRodVelocity(),
                data.getFlightTime(),
                minStability,
                maxStability);
    }

    private static double defaultMach() {
        try {
            return Application.getPreferences().getDefaultMach();
        } catch (RuntimeException e) {
            return 0.3;
        }
    }

    private static double stabilityCalibres(Coordinate cp, double cg, double referenceLength) {
        if (cp == null || referenceLength <= 0 || cp.weight <= MathUtil.EPSILON
                || Double.isNaN(cp.x) || Double.isNaN(cg)) {
            return Double.NaN;
        }
        return (cp.x - cg) / referenceLength;
    }

    private static String mountedMotor(FlightConfiguration config) {
        for (MotorConfiguration mount : config.getActiveMotors()) {
            Motor motor = mount.getMotor();
            if (motor != null && motor.getDesignation() != null) {
                return motor.getDesignation();
            }
        }
        return null;
    }

    private static String[] componentBreakdown(FlightConfiguration config) {
        Map<Integer, CMAnalysisEntry> analysis = MassCalculator.getCMAnalysis(config);
        List<CMAnalysisEntry> rows = new ArrayList<>();
        for (CMAnalysisEntry entry : analysis.values()) {
            // Stage and assembly rows from getCMAnalysis are not reliable absolute CGs
            // (the sustainer comes back at x = 0). List only parts and motors.
            if (entry == null || entry.source instanceof Rocket || entry.source instanceof ComponentAssembly
                    || entry.name == null) {
                continue;
            }
            if (Double.isNaN(entry.eachMass) || entry.eachMass <= 0.001 || entry.totalCM == null
                    || entry.totalCM.isNaN()) {
                continue;
            }
            rows.add(entry);
        }
        rows.sort(Comparator.comparingDouble(entry -> entry.totalCM.x));
        String[] lines = new String[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            CMAnalysisEntry entry = rows.get(i);
            lines[i] = String.format("    %s: %.4f m (mass: %.4f kg)",
                    entry.name, entry.totalCM.x, entry.eachMass);
        }
        return lines;
    }

    private static double finiteExtreme(List<Double> values, boolean minimum) {
        double extreme = Double.NaN;
        if (values == null) {
            return extreme;
        }
        for (Double value : values) {
            if (value == null || value.isNaN()) {
                continue;
            }
            if (Double.isNaN(extreme) || (minimum ? value < extreme : value > extreme)) {
                extreme = value;
            }
        }
        return extreme;
    }
}
