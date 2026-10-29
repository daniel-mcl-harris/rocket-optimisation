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

    public static void main(String[] args) {
        OptimiseCommand.Args command;
        try {
            command = OptimiseCommand.parse(args);
        } catch (OptimiseCommand.UsageException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }

        OpenRocketCore.initialize();
        String rocketPath = new File("rockets", "GA_base_rocket.ork").getPath();

        MotorCatalogue.Selection motor;
        try {
            motor = MotorCatalogue.select(command.motor);
        } catch (MotorCatalogue.MotorLookupException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }
        System.out.println("Motor: " + motor.describe());

        runOptimisation(command.population, command.generations, rocketPath, motor);
    }
    
    private static void runOptimisation(int population, int generations, String rocketPath,
                                        MotorCatalogue.Selection motor) {
        try {
            OpenRocketDocument baselineDoc = loadRocket(new File(rocketPath));
            if (baselineDoc == null) {
                System.err.println("Failed to load baseline rocket: " + rocketPath);
                System.exit(1);
                return;
            }
            Rocket baselineRocket = baselineDoc.getRocket();
            MotorCatalogue.mount(baselineRocket, motor);
            
            double baselineNose = getComponentLength(baselineRocket, NoseCone.class);
            double baselineBody = getComponentLength(baselineRocket, BodyTube.class);
            double baselineRootChord = getFinParameter(baselineRocket, "getRootChord");
            double baselineTipChord = getFinParameter(baselineRocket, "getTipChord");
            double baselineHeight = getFinParameter(baselineRocket, "getHeight");
            double baselineSweep = getFinParameter(baselineRocket, "getSweep");
            
            RocketAnalysis.Ensemble baselineResult = runEnsemble(baselineRocket);
            
            OptimisationReport report = RocketOptimizer.optimise(population, generations, rocketPath,
                baselineNose, baselineBody, baselineRootChord, baselineTipChord, 
                baselineHeight, baselineSweep, motor);
            
            if (report == null) {
                System.err.println("Optimisation failed");
                System.exit(1);
                return;
            }
            
            report.baselineNoseCone = baselineNose;
            report.baselineBodyTube = baselineBody;
            report.baselineFinRootChord = baselineRootChord;
            report.baselineFinTipChord = baselineTipChord;
            report.baselineFinHeight = baselineHeight;
            report.baselineFinSweepLength = baselineSweep;
            report.motorDescription = motor.describe();
            RocketAnalysis.MassProperties baselineMass = RocketAnalysis.massProperties(baselineRocket);
            applyMassProperties(report, baselineMass, true);
            
            if (baselineResult != null) {
                applyEnsemble(report, baselineResult, true);
            }
            
            OpenRocketDocument optimisedDoc = loadRocket(new File(rocketPath));
            if (optimisedDoc == null) {
                System.err.println("Failed to load baseline rocket: " + rocketPath);
                System.exit(1);
                return;
            }
            Rocket optimisedRocket = optimisedDoc.getRocket();
            MotorCatalogue.mount(optimisedRocket, motor);
            setComponentLength(optimisedRocket, NoseCone.class, report.optimisedNoseCone);
            setComponentLength(optimisedRocket, BodyTube.class, report.optimisedBodyTube);
            setFinParameters(optimisedRocket, report.optimisedFinRootChord, 
                report.optimisedFinTipChord, report.optimisedFinHeight, report.optimisedFinSweepLength);
            
            RocketAnalysis.Ensemble optimisedResult = runEnsemble(optimisedRocket);
            if (optimisedResult != null) {
                applyEnsemble(report, optimisedResult, false);
            }
            RocketAnalysis.MassProperties optimisedMass = RocketAnalysis.massProperties(optimisedRocket);
            applyMassProperties(report, optimisedMass, false);
            
            saveOptimisedRocket(optimisedDoc);
            
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
    
    private static RocketAnalysis.Ensemble runEnsemble(Rocket rocket) {
        try {
            return RocketAnalysis.flyAllWinds(rocket);
        } catch (Exception e) {
            System.err.println("Simulation failed: " + e.getMessage());
            return null;
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

    private static void applyEnsemble(OptimisationReport report, RocketAnalysis.Ensemble ensemble,
                                      boolean baseline) {
        RocketAnalysis.FlightResult flight = ensemble.average;
        if (baseline) {
            report.baselineApogee = flight.apogee;
            report.baselineTimeToApogee = flight.timeToApogee;
            report.baselineMaxVelocity = flight.maxVelocity;
            report.baselineMaxAcceleration = flight.maxAcceleration;
            report.baselineLaunchRodVelocity = flight.launchRodVelocity;
            report.baselineFlightTime = flight.flightTime;
            report.baselineMinFlightStabilityCalibres = ensemble.minimumInFlightStability;
            report.baselineMaxFlightStabilityCalibres = ensemble.maximumInFlightStability;
            report.baselineWindCases = ensemble.caseLines;
        } else {
            report.optimisedApogee = flight.apogee;
            report.optimisedTimeToApogee = flight.timeToApogee;
            report.optimisedMaxVelocity = flight.maxVelocity;
            report.optimisedMaxAcceleration = flight.maxAcceleration;
            report.optimisedLaunchRodVelocity = flight.launchRodVelocity;
            report.optimisedFlightTime = flight.flightTime;
            report.optimisedMinFlightStabilityCalibres = ensemble.minimumInFlightStability;
            report.optimisedMaxFlightStabilityCalibres = ensemble.maximumInFlightStability;
            report.optimisedWindCases = ensemble.caseLines;
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
