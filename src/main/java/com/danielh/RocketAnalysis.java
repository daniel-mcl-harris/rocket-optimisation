package com.danielh;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import info.openrocket.core.aerodynamics.BarrowmanCalculator;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.masscalc.CMAnalysisEntry;
import info.openrocket.core.masscalc.MassCalculator;
import info.openrocket.core.masscalc.RigidBody;
import info.openrocket.core.models.wind.PinkNoiseWindModel;
import info.openrocket.core.models.wind.WindModelType;
import info.openrocket.core.motor.Motor;
import info.openrocket.core.motor.MotorConfiguration;
import info.openrocket.core.rocketcomponent.ComponentAssembly;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.simulation.FlightDataBranch;
import info.openrocket.core.simulation.FlightDataType;
import info.openrocket.core.simulation.SimulationOptions;
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

    private static final ExecutorService WIND_POOL = Executors.newFixedThreadPool(WindCases.CASES.size(), runnable -> {
        Thread thread = new Thread(runnable, "wind-case");
        thread.setDaemon(true);
        return thread;
    });

    private RocketAnalysis() {
    }

    public static final class Ensemble {
        public final FlightResult average;
        /** Lowest in-flight stability sample across every wind case. */
        public final double minimumInFlightStability;
        /** Highest in-flight stability sample across every wind case. */
        public final double maximumInFlightStability;
        public final FlightResult[] cases;
        public final String[] caseLines;

        Ensemble(FlightResult average, double minimumInFlightStability, double maximumInFlightStability,
                 FlightResult[] cases, String[] caseLines) {
            this.average = average;
            this.minimumInFlightStability = minimumInFlightStability;
            this.maximumInFlightStability = maximumInFlightStability;
            this.cases = cases;
            this.caseLines = caseLines;
        }
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
        public final double maxMach;
        public final double minStabilityCalibres;
        public final double maxStabilityCalibres;

        FlightResult(double apogee, double timeToApogee, double maxVelocity, double maxAcceleration,
                     double launchRodVelocity, double flightTime, double maxMach,
                     double minStabilityCalibres, double maxStabilityCalibres) {
            this.apogee = apogee;
            this.timeToApogee = timeToApogee;
            this.maxVelocity = maxVelocity;
            this.maxAcceleration = maxAcceleration;
            this.launchRodVelocity = launchRodVelocity;
            this.flightTime = flightTime;
            this.maxMach = maxMach;
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

    /**
     * Fly independent copies of the rocket in every {@link WindCases} entry and
     * average the scalar flight metrics. The copies run in parallel. Each case
     * uses that case's fixed pink-noise seed, so repeating the same rocket
     * produces the same numbers.
     */
    public static Ensemble flyAllWinds(Rocket rocket) throws SimulationException {
        List<WindCases.Case> cases = WindCases.CASES;
        List<Future<FlightResult>> futures = new ArrayList<>();
        for (WindCases.Case wind : cases) {
            Rocket copy = rocket.copyWithOriginalID();
            futures.add(WIND_POOL.submit(() -> {
                try {
                    return simulate(copy, wind);
                } catch (SimulationException failure) {
                    throw new RuntimeException(failure);
                }
            }));
        }

        FlightResult[] results = new FlightResult[cases.size()];
        for (int i = 0; i < futures.size(); i++) {
            try {
                results[i] = futures.get(i).get();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new SimulationException("Wind case interrupted", interrupted);
            } catch (ExecutionException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof RuntimeException && cause.getCause() instanceof SimulationException simulationFailure) {
                    throw simulationFailure;
                }
                throw new SimulationException("Wind case failed", cause == null ? failure : cause);
            }
        }
        return combine(cases, results);
    }

    public static FlightResult simulate(Rocket rocket, WindCases.Case wind) throws SimulationException {
        Simulation simulation = new Simulation(rocket);
        applyWind(simulation.getOptions(), wind);
        simulation.simulate();
        FlightData data = simulation.getSimulatedData();
        if (data == null) {
            throw new SimulationException("Simulation produced no flight data for " + wind.name);
        }
        return flightResult(data);
    }

    /**
     * {@code SimulationOptions.setRandomSeed} does not rebuild the pink-noise
     * model, and that model's seed is fixed at construction. Install a model
     * built with the case seed so the gust sequence is reproducible.
     */
    static void applyWind(SimulationOptions options, WindCases.Case wind) throws SimulationException {
        options.setLaunchIntoWind(true);
        options.setWindModelType(WindModelType.AVERAGE);
        options.setRandomSeed(wind.seed);
        PinkNoiseWindModel model = new PinkNoiseWindModel(wind.seed);
        model.setAverage(wind.averageMetresPerSecond);
        model.setTurbulenceIntensity(wind.turbulenceIntensity);
        try {
            Field field = SimulationOptions.class.getDeclaredField("averageWindModel");
            field.setAccessible(true);
            field.set(options, model);
        } catch (ReflectiveOperationException failure) {
            throw new SimulationException("Could not install the wind model", failure);
        }
    }

    private static Ensemble combine(List<WindCases.Case> cases, FlightResult[] results) {
        double minStability = Double.NaN;
        double maxStability = Double.NaN;
        String[] lines = new String[results.length];
        for (int i = 0; i < results.length; i++) {
            WindCases.Case wind = cases.get(i);
            FlightResult flight = results[i];
            minStability = lesser(minStability, flight.minStabilityCalibres);
            maxStability = greater(maxStability, flight.maxStabilityCalibres);
            lines[i] = String.format(
                    "    %s (%.0f m/s, turbulence %.2f): apogee %.1f m, rod %.1f m/s, min stability %.2f cal",
                    wind.name, wind.averageMetresPerSecond, wind.turbulenceIntensity,
                    flight.apogee, flight.launchRodVelocity, flight.minStabilityCalibres);
        }
        FlightResult average = new FlightResult(
                mean(results, flight -> flight.apogee),
                mean(results, flight -> flight.timeToApogee),
                mean(results, flight -> flight.maxVelocity),
                mean(results, flight -> flight.maxAcceleration),
                mean(results, flight -> flight.launchRodVelocity),
                mean(results, flight -> flight.flightTime),
                mean(results, flight -> flight.maxMach),
                minStability,
                maxStability);
        return new Ensemble(average, minStability, maxStability, results, lines);
    }

    private static double mean(FlightResult[] results, java.util.function.ToDoubleFunction<FlightResult> value) {
        double sum = 0.0;
        int count = 0;
        for (FlightResult flight : results) {
            double sample = value.applyAsDouble(flight);
            if (!Double.isNaN(sample)) {
                sum += sample;
                count++;
            }
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    private static double lesser(double current, double sample) {
        if (Double.isNaN(sample)) {
            return current;
        }
        if (Double.isNaN(current) || sample < current) {
            return sample;
        }
        return current;
    }

    private static double greater(double current, double sample) {
        if (Double.isNaN(sample)) {
            return current;
        }
        if (Double.isNaN(current) || sample > current) {
            return sample;
        }
        return current;
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
                data.getMaxMachNumber(),
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
