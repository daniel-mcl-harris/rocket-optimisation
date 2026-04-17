package com.danielh;

import java.util.ArrayList;
import java.io.File;
import java.util.List;
import io.jenetics.*;
import info.openrocket.core.rocketcomponent.*;
import info.openrocket.core.startup.OpenRocketCore;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.file.GeneralRocketSaver;

public class App {
    public static void main(String[] args) {
        OpenRocketCore.initialize();

        String rocketPath = "libs\\test_rocket.ork";
        
        // Check command line arguments
        if (args.length > 0 && args[0].equalsIgnoreCase("optimize")) {
            // Run genetic algorithm optimization
            int population = 50;  // Default
            int generations = 100;  // Default
            
            // Parse optional custom parameters
            if (args.length >= 3) {
                try {
                    population = Integer.parseInt(args[1]);
                    generations = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid parameters. Usage: App optimize [population] [generations]");
                    System.err.println("Using defaults: population=50, generations=100");
                }
            }
            
            runOptimization(population, generations, rocketPath);
        } else {
            // Default: Run baseline simulation
            try {
                OpenRocketDocument document = loadRocketDocument(new File(rocketPath));
                if (document == null) return;
                
                Rocket rocket = document.getRocket();
                Simulation sim = new Simulation(rocket);
                sim.simulate();

                // Extract actual mass data from the rocket design
                System.out.println("\n=== ROCKET DATA ===");
                extractAndDisplayMassData(rocket);

                // Run a single simulation and print results
                SimulationResult result = runSimulationAndExtractData(rocket);
                if (result != null) {
                    result.print();
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Run genetic algorithm optimization with detailed reporting
     */
    private static void runOptimization(int population, int generations, String rocketPath) {
        try {
            System.out.println("Running optimization (this may take a while)...");
            System.out.println("Population: " + population + ", Generations: " + generations);
            System.out.println("");
            
            // Get baseline parameters and performance
            OpenRocketDocument baselineDoc = loadRocketDocument(new File(rocketPath));
            if (baselineDoc == null) {
                System.err.println("Failed to load baseline rocket");
                return;
            }
            Rocket baselineRocket = baselineDoc.getRocket();
            
            // Extract baseline parameters
            double baselineNoseCone = getComponentLength(baselineRocket, NoseCone.class);
            double baselineBodyTube = getComponentLength(baselineRocket, BodyTube.class);
            
            // Run baseline simulation
            SimulationResult baselineResult = runSimulationAndExtractData(baselineRocket);
            
            // Run genetic algorithm optimization
            OptimizationReport report = RocketOptimizer.optimize(population, generations, rocketPath);
            
            if (report == null) {
                System.err.println("Optimization failed");
                return;
            }
            
            // Store baseline results in report
            report.baselineNoseCone = baselineNoseCone;
            report.baselineBodyTube = baselineBodyTube;
            if (baselineResult != null) {
                report.baselineApogee = baselineResult.apogee;
                report.baselineTimeToApogee = baselineResult.timeToApogee;
                report.baselineMaxVelocity = baselineResult.maxVelocity;
                report.baselineMaxAcceleration = baselineResult.maxAcceleration;
            }
            
            // Run final simulation with optimized parameters
            OpenRocketDocument optimizedDoc = loadRocketDocument(new File(rocketPath));
            if (optimizedDoc != null) {
                Rocket optimizedRocket = optimizedDoc.getRocket();
                
                // Apply optimized parameters
                setNoseConeLength(optimizedRocket, report.optimizedNoseCone);
                setBodyTubeLength(optimizedRocket, report.optimizedBodyTube);
                
                // Run simulation
                SimulationResult optimizedResult = runSimulationAndExtractData(optimizedRocket);
                if (optimizedResult != null) {
                    report.optimizedApogee = optimizedResult.apogee;
                    report.optimizedTimeToApogee = optimizedResult.timeToApogee;
                    report.optimizedMaxVelocity = optimizedResult.maxVelocity;
                    report.optimizedMaxAcceleration = optimizedResult.maxAcceleration;
                }
            }
            
            // Generate and display detailed report
            System.out.flush();
            System.err.flush();
            report.generateReport();
            System.out.flush();
            
        } catch (Exception e) {
            System.err.println("Optimization failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Helper method to extract component length
     */
    private static double getComponentLength(Rocket rocket, Class<?> componentType) {
        try {
            for (RocketComponent child : rocket.getChildren()) {
                if (child instanceof RocketComponent) {
                    for (RocketComponent subchild : child.getChildren()) {
                        if (componentType.isInstance(subchild)) {
                            java.lang.reflect.Method getLength = subchild.getClass().getMethod("getLength");
                            Object lenObj = getLength.invoke(subchild);
                            if (lenObj instanceof Number) {
                                return ((Number) lenObj).doubleValue();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return 0.0;
    }
    
    private static OpenRocketDocument loadRocketDocument(File file) {
        java.io.PrintStream originalOut = System.out;
        java.io.PrintStream originalErr = System.err;
        System.setOut(new FilteringPrintStream(originalOut));
        System.setErr(new FilteringPrintStream(originalErr));
        try {
            GeneralRocketLoader loader = new GeneralRocketLoader(file);
            OpenRocketDocument document = loader.load();
            return document;
        } catch (Exception e) {
            System.err.println("Failed to load: " + e.getMessage());
            return null;
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }
    
    private static void saveRocketDocument(OpenRocketDocument document, File file) {
        try {
            GeneralRocketSaver saver = new GeneralRocketSaver();
            saver.save(file, document);
            System.out.println("Saved to: " + file.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Failed to save: " + e.getMessage());
        }
    }
    
    private static void extractAndDisplayMassData(Rocket rocket) {
        try {
            System.out.println("\nComponent Breakdown:");
            System.out.println("─────────────────────────────────────────");
            
            double[] totals = new double[1];  // [totalMass]
            totals[0] = 0.0;
            
            extractMassRecursive(rocket, 0, totals);
            
            System.out.println("─────────────────────────────────────────");
            System.out.println("TOTAL DRY MASS: " + String.format("%.4f", totals[0]) + " kg");
        } catch (Exception e) {
            System.err.println("Error extracting mass data: " + e.getMessage());
        }
    }

    // Overload with basePosition
    private static void extractMassRecursive(RocketComponent component, int depth, double[] totals) {
        try {
            // Get component mass
            double mass = 0.0;
            try {
                java.lang.reflect.Method getMass = component.getClass().getMethod("getMass");
                Object massObj = getMass.invoke(component);
                if (massObj instanceof Number) {
                    mass = ((Number) massObj).doubleValue();
                }
            } catch (Exception e) {
                // getMass failed
            }

            // Get the GC for this component
            Double cg = null;
            try {
                java.lang.reflect.Method getComponentCG = component.getClass().getMethod("getComponentCG");
                Object cgCoord = getComponentCG.invoke(component);
                if (cgCoord != null) {
                    java.lang.reflect.Field xField = cgCoord.getClass().getDeclaredField("x");
                    xField.setAccessible(true);
                    double localCG = xField.getDouble(cgCoord);
                    cg = localCG;
                }
            } catch (Exception e) {
                // No CG available
            }

            // Try to get the length of the component
            Double length = null;
            try {
                java.lang.reflect.Method getLength = component.getClass().getMethod("getLength");
                Object lenObj = getLength.invoke(component);
                if (lenObj instanceof Number) {
                    length = ((Number) lenObj).doubleValue();
                }
            } catch (Exception e) {
                // No length available
            }

            // Display this component if it has mass
            if (mass > 0.00001) {
                String indent = "  ".repeat(depth);
                String componentName = component.getName();
                String componentType = component.getClass().getSimpleName();
                
                if (cg != null) {
                    System.out.print(indent + "-" + componentName + " (" + componentType + "): " +
                        String.format("%.4f", mass) + " kg, CG = " + String.format("%.4f", cg) + " m");
                } else {
                    System.out.print(indent + "-" + componentName + " (" + componentType + "): " +
                        String.format("%.4f", mass) + " kg");
                }
                if (length != null) {
                    System.out.println(", Length = " + String.format("%.4f", length) + " m");
                } else {
                    System.out.println();
                }
                totals[0] += mass;
            }

            // Recurse to children
            if (component instanceof RocketComponent) {
                for (RocketComponent child : component.getChildren()) {
                    extractMassRecursive(child, depth + 1, totals);
                }
            }
        } catch (Exception e) {
            System.err.println("Error retrieving component mass: " + e.getMessage());
        }
    }
    
    private static void setNoseConeLength(Rocket rocket, double length) {
        try {
            for (RocketComponent child : rocket.getChildren()) {
                if (child instanceof RocketComponent) {
                    for (RocketComponent subchild : child.getChildren()) {
                        if (subchild instanceof NoseCone) {
                            NoseCone noseCone = (NoseCone) subchild;
                            noseCone.setLength(length);
                            System.out.println("  - Set nose cone length to: " + length + " m");
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error setting nose cone length: " + e.getMessage());
        }
    }
    
    private static void setBodyTubeLength(Rocket rocket, double length) {
        try {
            for (RocketComponent child : rocket.getChildren()) {
                if (child instanceof RocketComponent) {
                    for (RocketComponent subchild : child.getChildren()) {
                        if (subchild instanceof BodyTube) {
                            BodyTube bodyTube = (BodyTube) subchild;
                            bodyTube.setLength(length);
                            System.out.println("  - Set body tube length to: " + length + " m");
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error setting body tube length: " + e.getMessage());
        }
    }
    
    private static SimulationResult runSimulationAndExtractData(Rocket rocket) {
        try {
            Simulation simulation = new Simulation(rocket);
            simulation.simulate();
            
            java.lang.reflect.Method getSimulatedData = simulation.getClass().getMethod("getSimulatedData");
            Object simulatedData = getSimulatedData.invoke(simulation);
            
            if (simulatedData == null) {
                System.out.println("No simulation data available");
                return null;
            }
            
            double apogee = getDoubleFromMethod(simulatedData, "getMaxAltitude");
            double timeToApogee = getDoubleFromMethod(simulatedData, "getTimeToApogee");
            double maxVelocity = getDoubleFromMethod(simulatedData, "getMaxVelocity");
            double maxAcceleration = getDoubleFromMethod(simulatedData, "getMaxAcceleration");
            double maxMach = getDoubleFromMethod(simulatedData, "getMaxMachNumber");
            double outerDiameter = getRocketOuterDiameter(rocket);
            
            return new SimulationResult("Default", apogee, timeToApogee, 
                        maxVelocity, maxAcceleration, maxMach, outerDiameter);
            
        } catch (Exception e) {
            System.err.println("Simulation failed: " + e.getMessage());
            return null;
        }
    }
    
    private static double getDoubleFromMethod(Object obj, String methodName) {
        try {
            java.lang.reflect.Method method = obj.getClass().getMethod(methodName);
            Object result = method.invoke(obj);
            if (result instanceof Double) {
                return (Double) result;
            }
        } catch (Exception e) { }
        return 0.0;
    }
    
    private static double getRocketOuterDiameter(Rocket rocket) {
        try {
           // Get the outer radius of the body tube (second stage)
            for (RocketComponent child : rocket.getChildren()) {
                if (child instanceof AxialStage) {
                    for (RocketComponent component : child.getChildren()) {
                        if (component instanceof BodyTube) {
                            java.lang.reflect.Method getOuterRadius = component.getClass().getMethod("getOuterRadius");
                            Double radius = (Double) getOuterRadius.invoke(component);
                            if (radius != null) {
                                return radius * 2.0;  // Return diameter
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return 0.1;  // Default to 100mm if not found
    }
    
    private static double getBodyTubeDiameter(Rocket rocket) {
        try {
            for (RocketComponent child : rocket.getChildren()) {
                if (child instanceof RocketComponent) {
                    for (RocketComponent subchild : child.getChildren()) {
                        if (subchild instanceof BodyTube) {
                            java.lang.reflect.Method getOuterRadius = subchild.getClass().getMethod("getOuterRadius");
                            Double radius = (Double) getOuterRadius.invoke(subchild);
                            if (radius != null) {
                                return radius * 2.0;  // Return diameter
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return 0.0;
    }
    
    private static void setBodyTubeDiameter(Rocket rocket, double diameter) {
        try {
            for (RocketComponent child : rocket.getChildren()) {
                if (child instanceof RocketComponent) {
                    for (RocketComponent subchild : child.getChildren()) {
                        // Set body tube outer diameter
                        if (subchild instanceof BodyTube) {
                            BodyTube bodyTube = (BodyTube) subchild;
                            bodyTube.setOuterRadius(diameter / 2.0);
                        }
                        // Set nose cone base diameter to match body tube diameter
                        if (subchild instanceof NoseCone) {
                            NoseCone noseCone = (NoseCone) subchild;
                            noseCone.setBaseRadius(diameter / 2.0);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error setting body tube diameter: " + e.getMessage());
        }
    }
    
    static class SimulationResult {
        String motor;
        double apogee, timeToApogee, maxVelocity, maxAcceleration, maxMach;
        double centerOfMass, centerOfPressure, safetyMargin, outerDiameter;
        
        SimulationResult(String motor, double apogee, double timeToApogee, 
                        double maxVelocity, double maxAcceleration, double maxMach,
                        double outerDiameter) {
            this.motor = motor;
            this.apogee = apogee;
            this.timeToApogee = timeToApogee;
            this.maxVelocity = maxVelocity;
            this.maxAcceleration = maxAcceleration;
            this.maxMach = maxMach;
            this.outerDiameter = outerDiameter;
        }
        
        void print() {
            System.out.println("\n--- Results for " + motor + " ---");
            System.out.println("FLIGHT PERFORMANCE:");
            System.out.println("  Apogee: " + String.format("%.2f", apogee) + " m");
            System.out.println("  Time to Apogee: " + String.format("%.2f", timeToApogee) + " s");
            System.out.println("  Max Velocity: " + String.format("%.2f", maxVelocity) + " m/s");
            System.out.println("  Max Acceleration: " + String.format("%.2f", maxAcceleration) + " m/s²");
            System.out.println("  Max Mach: " + String.format("%.3f", maxMach));
        }
        
        @Override
        public String toString() {
            return String.format("%s: Apogee=%.1fm, SafetyMargin=%.3fm", 
                motor, apogee, safetyMargin);
        }
    }
}