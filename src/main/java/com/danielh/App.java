package com.danielh;

import java.io.File;
import info.openrocket.core.rocketcomponent.*;
import info.openrocket.core.startup.OpenRocketCore;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.file.GeneralRocketSaver;

public class App {
    private static final java.io.PrintStream ORIGINAL_OUT = System.out;
    private static final java.io.PrintStream ORIGINAL_ERR = System.err;
    
    public static void main(String[] args) {
        OpenRocketCore.initialize();
        String rocketPath = "rockets\\GA_base_rocket.ork";
        
        if (args.length > 0 && args[0].equalsIgnoreCase("optimize")) {
            int population = 50;
            int generations = 100;
            
            if (args.length >= 3) {
                try {
                    population = Integer.parseInt(args[1]);
                    generations = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid parameters. Usage: App optimize [population] [generations]");
                }
            }
            
            runOptimization(population, generations, rocketPath);
        }
    }
    
    private static void runOptimization(int population, int generations, String rocketPath) {
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
            
            SimulationResult baselineResult = runSimulation(baselineRocket);
            
            OptimizationReport report = RocketOptimizer.optimize(population, generations, rocketPath,
                baselineNose, baselineBody, baselineRootChord, baselineTipChord, 
                baselineHeight, baselineSweep);
            
            if (report == null) {
                System.err.println("Optimization failed");
                return;
            }
            
            report.baselineNoseCone = baselineNose;
            report.baselineBodyTube = baselineBody;
            report.baselineFinRootChord = baselineRootChord;
            report.baselineFinTipChord = baselineTipChord;
            report.baselineFinHeight = baselineHeight;
            report.baselineFinSweepLength = baselineSweep;
            if (baselineResult != null) {
                report.baselineApogee = baselineResult.apogee;
                report.baselineTimeToApogee = baselineResult.timeToApogee;
                report.baselineMaxVelocity = baselineResult.maxVelocity;
                report.baselineMaxAcceleration = baselineResult.maxAcceleration;
            }
            
            OpenRocketDocument optimizedDoc = loadRocket(new File(rocketPath));
            if (optimizedDoc != null) {
                Rocket optimizedRocket = optimizedDoc.getRocket();
                setComponentLength(optimizedRocket, NoseCone.class, report.optimizedNoseCone);
                setComponentLength(optimizedRocket, BodyTube.class, report.optimizedBodyTube);
                setFinParameters(optimizedRocket, report.optimizedFinRootChord, 
                    report.optimizedFinTipChord, report.optimizedFinHeight, report.optimizedFinSweepLength);
                
                SimulationResult optimizedResult = runSimulation(optimizedRocket);
                if (optimizedResult != null) {
                    report.optimizedApogee = optimizedResult.apogee;
                    report.optimizedTimeToApogee = optimizedResult.timeToApogee;
                    report.optimizedMaxVelocity = optimizedResult.maxVelocity;
                    report.optimizedMaxAcceleration = optimizedResult.maxAcceleration;
                }
                
                saveOptimizedRocket(optimizedDoc);
            }
            
            report.generateReport();
            System.exit(0);
        } catch (Exception e) {
            System.err.println("Optimization failed: " + e.getMessage());
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
                        System.out.println("  - Set " + subchild.getClass().getSimpleName().toLowerCase() + " length to: " + length + " m");
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
    
    private static SimulationResult runSimulation(Rocket rocket) {
        try {
            Simulation simulation = new Simulation(rocket);
            simulation.simulate();
            
            java.lang.reflect.Method getSimulatedData = simulation.getClass().getMethod("getSimulatedData");
            Object simulatedData = getSimulatedData.invoke(simulation);
            if (simulatedData == null) return null;
            
            double apogee = getDoubleFromMethod(simulatedData, "getMaxAltitude");
            double timeToApogee = getDoubleFromMethod(simulatedData, "getTimeToApogee");
            double maxVelocity = getDoubleFromMethod(simulatedData, "getMaxVelocity");
            double maxAcceleration = getDoubleFromMethod(simulatedData, "getMaxAcceleration");
            
            return new SimulationResult(apogee, timeToApogee, maxVelocity, maxAcceleration);
        } catch (Exception e) {
            System.err.println("Simulation failed: " + e.getMessage());
            return null;
        }
    }
    
    private static double getDoubleFromMethod(Object obj, String methodName) {
        try {
            Object result = obj.getClass().getMethod(methodName).invoke(obj);
            if (result instanceof Double) return (Double) result;
        } catch (Exception e) {
        }
        return 0.0;
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
    
    private static void saveOptimizedRocket(OpenRocketDocument doc) {
        try {
            File rocketsDir = new File("rockets");
            if (!rocketsDir.exists()) {
                rocketsDir.mkdirs();
            }
            
            int version = getNextVersionNumber();
            File outputFile = new File(rocketsDir, "GA_rocket_v" + version + ".ork");
            
            GeneralRocketSaver saver = new GeneralRocketSaver();
            saver.save(outputFile, doc);
            
            System.out.println("\nOptimized rocket saved to: " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Failed to save optimized rocket: " + e.getMessage());
        }
    }
    
    static class SimulationResult {
        double apogee, timeToApogee, maxVelocity, maxAcceleration;
        
        SimulationResult(double apogee, double timeToApogee, double maxVelocity, double maxAcceleration) {
            this.apogee = apogee;
            this.timeToApogee = timeToApogee;
            this.maxVelocity = maxVelocity;
            this.maxAcceleration = maxAcceleration;
        }
    }
}
