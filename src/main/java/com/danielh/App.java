package com.danielh;

import java.io.File;
import info.openrocket.core.rocketcomponent.*;
import info.openrocket.core.startup.OpenRocketCore;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.file.GeneralRocketSaver;

public class App {
    private static final java.io.PrintStream ORIGINAL_OUT = System.out;
    private static final java.io.PrintStream ORIGINAL_ERR = System.err;

    // Names accepted on the command line. Mass and CG come from the motor
    // mounted in the .ork, not from this list.
    private static final java.util.Set<String> MOTORS = java.util.Set.of("I350R-0");

    public static void main(String[] args) {
        OpenRocketCore.initialize();
        String rocketPath = new File("rockets", "GA_base_rocket.ork").getPath();
        
        if (args.length > 0 && args[0].equalsIgnoreCase("optimise")) {
            String motorType = "I350R-0";
            int population = 50;
            int generations = 100;
            
            if (args.length >= 2) {
                motorType = args[1];
            }
            if (args.length >= 4) {
                try {
                    population = Integer.parseInt(args[2]);
                    generations = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid parameters. Usage: App optimise <motorType> [population] [generations]");
                }
            }
            
            if (!MOTORS.contains(motorType)) {
                System.err.println("Unknown motor type: " + motorType);
                System.err.println("Available motors: " + MOTORS);
                System.exit(1);
            }
            
            runOptimisation(population, generations, rocketPath, motorType);
        }
    }
    
    private static void runOptimisation(int population, int generations, String rocketPath, String motorType) {
        try {
            OpenRocketDocument baselineDoc = loadRocket(new File(rocketPath));
            if (baselineDoc == null) {
                System.err.println("Failed to load baseline rocket");
                return;
            }
            Rocket baselineRocket = baselineDoc.getRocket();
            
            double baselineNose = getComponentLength(baselineRocket, NoseCone.class);
            double baselineBody = getComponentLength(baselineRocket, BodyTube.class);
            double baselineRootChord = getFinParameter(baselineRocket, "getRootChord");
            double baselineTipChord = getFinParameter(baselineRocket, "getTipChord");
            double baselineHeight = getFinParameter(baselineRocket, "getHeight");
            double baselineSweep = getFinParameter(baselineRocket, "getSweep");
            
            RocketAnalysis.FlightResult baselineResult = runSimulation(baselineRocket);
            
            OptimisationReport report = RocketOptimizer.optimise(population, generations, rocketPath,
                baselineNose, baselineBody, baselineRootChord, baselineTipChord, 
                baselineHeight, baselineSweep);
            
            if (report == null) {
                System.err.println("Optimisation failed");
                return;
            }
            
            report.baselineNoseCone = baselineNose;
            report.baselineBodyTube = baselineBody;
            report.baselineFinRootChord = baselineRootChord;
            report.baselineFinTipChord = baselineTipChord;
            report.baselineFinHeight = baselineHeight;
            report.baselineFinSweepLength = baselineSweep;
            RocketAnalysis.MassProperties baselineMass = RocketAnalysis.massProperties(baselineRocket);
            warnIfMotorDiffers(motorType, baselineMass.motorDesignation);
            applyMassProperties(report, baselineMass, true);
            
            if (baselineResult != null) {
                applyFlightResult(report, baselineResult, true);
            }
            
            OpenRocketDocument optimisedDoc = loadRocket(new File(rocketPath));
            if (optimisedDoc != null) {
                Rocket optimisedRocket = optimisedDoc.getRocket();
                setComponentLength(optimisedRocket, NoseCone.class, report.optimisedNoseCone);
                setComponentLength(optimisedRocket, BodyTube.class, report.optimisedBodyTube);
                setFinParameters(optimisedRocket, report.optimisedFinRootChord, 
                    report.optimisedFinTipChord, report.optimisedFinHeight, report.optimisedFinSweepLength);
                
                RocketAnalysis.FlightResult optimisedResult = runSimulation(optimisedRocket);
                if (optimisedResult != null) {
                    applyFlightResult(report, optimisedResult, false);
                }
                RocketAnalysis.MassProperties optimisedMass = RocketAnalysis.massProperties(optimisedRocket);
                applyMassProperties(report, optimisedMass, false);
                
                saveOptimisedRocket(optimisedDoc);
            }
            
            report.generateReport();
            System.exit(0);
        } catch (Exception e) {
            System.err.println("Optimisation failed: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static OpenRocketDocument loadRocket(File file) {
        java.io.PrintStream originalOut = System.out;
        java.io.PrintStream originalErr = System.err;
        System.setOut(new FilteringPrintStream(originalOut));
        System.setErr(new FilteringPrintStream(originalErr));
        
        try {
            return new GeneralRocketLoader(file).load();
        } catch (Exception e) {
            System.err.println("Failed to load: " + e.getMessage());
            return null;
        } finally {
            System.setOut(ORIGINAL_OUT);
            System.setErr(ORIGINAL_ERR);
        }
    }
    
    private static double getComponentLength(Rocket rocket, Class<?> componentType) {
        for (RocketComponent child : rocket.getChildren()) {
            for (RocketComponent subchild : child.getChildren()) {
                if (componentType.isInstance(subchild)) {
                    try {
                        Object lenObj = subchild.getClass().getMethod("getLength").invoke(subchild);
                        if (lenObj instanceof Number) return ((Number) lenObj).doubleValue();
                    } catch (Exception e) {
                    }
                }
            }
        }
        return 0.0;
    }
    
    private static void setComponentLength(Rocket rocket, Class<?> componentType, double length) {
        for (RocketComponent child : rocket.getChildren()) {
            for (RocketComponent subchild : child.getChildren()) {
                if (componentType.isInstance(subchild)) {
                    try {
                        subchild.getClass().getMethod("setLength", double.class).invoke(subchild, length);
                    } catch (Exception e) {
                    }
                }
            }
        }
    }
    
    private static double getFinParameter(Rocket rocket, String methodName) {
        Double result = getFinParameterRecursive(rocket, methodName);
        return result != null ? result : 0.0;
    }
    
    private static Double getFinParameterRecursive(RocketComponent component, String methodName) {
        for (RocketComponent child : component.getChildren()) {
            if (child.getClass().getSimpleName().contains("Fin")) {
                try {
                    Object result = child.getClass().getMethod(methodName).invoke(child);
                    if (result instanceof Number) return ((Number) result).doubleValue();
                } catch (Exception e) {
                }
            }
            Double result = getFinParameterRecursive(child, methodName);
            if (result != null) return result;
        }
        return null;
    }
    
    private static void setFinParameters(Rocket rocket, double rootChord, double tipChord,
                                        double height, double sweepLength) {
        setFinParametersRecursive(rocket, rootChord, tipChord, height, sweepLength);
    }
    
    private static void setFinParametersRecursive(RocketComponent component, double rootChord, 
                                                  double tipChord, double height, double sweepLength) {
        for (RocketComponent child : component.getChildren()) {
            if (child.getClass().getSimpleName().contains("Fin")) {
                tryInvoke(child, "setRootChord", rootChord);
                tryInvoke(child, "setTipChord", tipChord);
                tryInvoke(child, "setHeight", height);
                tryInvoke(child, "setSweep", sweepLength);
            }
            setFinParametersRecursive(child, rootChord, tipChord, height, sweepLength);
        }
    }
    
    private static void tryInvoke(Object obj, String methodName, double value) {
        try {
            obj.getClass().getMethod(methodName, double.class).invoke(obj, value);
        } catch (Exception e) {
        }
    }
    
    private static RocketAnalysis.FlightResult runSimulation(Rocket rocket) {
        try {
            return RocketAnalysis.simulate(rocket);
        } catch (Exception e) {
            System.err.println("Simulation failed: " + e.getMessage());
            return null;
        }
    }

    private static void warnIfMotorDiffers(String requestedMotor, String mountedDesignation) {
        if (mountedDesignation == null) {
            System.err.println("Warning: the .ork flight configuration has no resolved motor. "
                    + "CG and the simulation will not include propellant.");
            return;
        }
        if (!requestedMotor.startsWith(mountedDesignation)) {
            System.err.println("Warning: requested motor " + requestedMotor
                    + " does not match the motor mounted in the .ork (" + mountedDesignation
                    + "). OpenRocket will simulate the mounted motor.");
        }
    }

    private static void applyMassProperties(OptimisationReport report, RocketAnalysis.MassProperties mass,
                                            boolean baseline) {
        if (baseline) {
            report.baselineCenterOfGravity = mass.launchCenterOfGravity;
            report.baselineBurnoutCenterOfGravity = mass.burnoutCenterOfGravity;
            report.baselineLaunchMass = mass.launchMass;
            report.baselineBurnoutMass = mass.burnoutMass;
            report.baselineDryMass = mass.dryMass;
            report.baselineCenterOfPressure = mass.centerOfPressure;
            report.baselineCenterOfPressureZeroAngle = mass.centerOfPressureZeroAngle;
            report.baselineComponentCG = mass.componentBreakdown;
            report.caliber = mass.referenceLength;
            report.baselineStabilityMarginCalibres = mass.stabilityCalibres;
        } else {
            report.optimisedCenterOfGravity = mass.launchCenterOfGravity;
            report.optimisedBurnoutCenterOfGravity = mass.burnoutCenterOfGravity;
            report.optimisedLaunchMass = mass.launchMass;
            report.optimisedBurnoutMass = mass.burnoutMass;
            report.optimisedDryMass = mass.dryMass;
            report.optimisedCenterOfPressure = mass.centerOfPressure;
            report.optimisedCenterOfPressureZeroAngle = mass.centerOfPressureZeroAngle;
            report.optimisedComponentCG = mass.componentBreakdown;
            report.optimisedStabilityMarginCalibres = mass.stabilityCalibres;
        }
    }

    private static void applyFlightResult(OptimisationReport report, RocketAnalysis.FlightResult flight,
                                          boolean baseline) {
        if (baseline) {
            report.baselineApogee = flight.apogee;
            report.baselineTimeToApogee = flight.timeToApogee;
            report.baselineMaxVelocity = flight.maxVelocity;
            report.baselineMaxAcceleration = flight.maxAcceleration;
            report.baselineLaunchRodVelocity = flight.launchRodVelocity;
            report.baselineFlightTime = flight.flightTime;
            report.baselineMinFlightStabilityCalibres = flight.minStabilityCalibres;
            report.baselineMaxFlightStabilityCalibres = flight.maxStabilityCalibres;
        } else {
            report.optimisedApogee = flight.apogee;
            report.optimisedTimeToApogee = flight.timeToApogee;
            report.optimisedMaxVelocity = flight.maxVelocity;
            report.optimisedMaxAcceleration = flight.maxAcceleration;
            report.optimisedLaunchRodVelocity = flight.launchRodVelocity;
            report.optimisedFlightTime = flight.flightTime;
            report.optimisedMinFlightStabilityCalibres = flight.minStabilityCalibres;
            report.optimisedMaxFlightStabilityCalibres = flight.maxStabilityCalibres;
        }
    }
    
    private static int getNextVersionNumber() {
        File rocketsDir = new File("rockets");
        if (!rocketsDir.exists()) {
            return 1;
        }
        
        int maxVersion = 0;
        File[] files = rocketsDir.listFiles((dir, name) -> name.matches("GA_rocket_v\\d+\\.ork"));
        
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                String versionStr = name.replaceAll("GA_rocket_v(\\d+)\\.ork", "$1");
                try {
                    int version = Integer.parseInt(versionStr);
                    if (version > maxVersion) {
                        maxVersion = version;
                    }
                } catch (NumberFormatException e) {
                }
            }
        }
        
        return maxVersion + 1;
    }
    
    private static void saveOptimisedRocket(OpenRocketDocument doc) {
        try {
            File rocketsDir = new File("rockets");
            if (!rocketsDir.exists()) {
                rocketsDir.mkdirs();
            }
            
            int version = getNextVersionNumber();
            File outputFile = new File(rocketsDir, "GA_rocket_v" + version + ".ork");
            
            GeneralRocketSaver saver = new GeneralRocketSaver();
            saver.save(outputFile, doc);
            
            System.out.println("\nOptimised rocket saved to: " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Failed to save optimised rocket: " + e.getMessage());
        }
    }
    
}
