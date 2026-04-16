package com.danielh;

import java.util.ArrayList;
import java.io.File;
import java.util.List;
import info.openrocket.core.rocketcomponent.*;
import info.openrocket.core.startup.OpenRocketCore;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.file.GeneralRocketSaver;
import info.openrocket.core.motor.*;
import info.openrocket.core.database.motor.MotorDatabase;
import info.openrocket.core.startup.Application;

public class App {
        // Global list to store mass and CG data for each component
        static List<ComponentMassCG> massCGList = new ArrayList<>();

        static class ComponentMassCG {
            String name;
            String type;
            double mass;
            Double cg;
            Double length;
            ComponentMassCG(String name, String type, double mass, Double cg, Double length) {
                this.name = name;
                this.type = type;
                this.mass = mass;
                this.cg = cg;
                this.length = length;
            }
        }
    public static void main(String[] args) {
        OpenRocketCore.initialize();

        try {
            String rocketPath = "C:\\Users\\Daniel\\rocket-app\\libs\\test_rocket.ork";
            
            OpenRocketDocument document = loadRocketDocument(new File(rocketPath));
            if (document == null) return;
            


            Rocket rocket = document.getRocket();

            // Force recalculation of rocket metadata to ensure CG/CP are up to date
            try {
                // Try fireComponentChangeEvent
                java.lang.reflect.Method fireComponentChange = rocket.getClass().getMethod("fireComponentChangeEvent", java.lang.Object.class);
                fireComponentChange.invoke(rocket, new Object[] { null });
            } catch (Exception e1) {
                try {
                    java.lang.reflect.Method resetMetadata = rocket.getClass().getMethod("resetMetadata");
                    resetMetadata.invoke(rocket);
                } catch (Exception e2) {
                    // Ignore
                }
            }

            // Run a simulation to extract CG and CP from simulation results
            try {
                System.out.println("\n--- Extracting CG and CP from simulation results ---");
                Simulation sim = new Simulation(rocket);
                sim.simulate();
                java.lang.reflect.Method getSimulatedData = sim.getClass().getMethod("getSimulatedData");
                Object simulatedData = getSimulatedData.invoke(sim);
                if (simulatedData != null) {
                    double cg = 0.0;
                    double cp = 0.0;
                    try {
                        java.lang.reflect.Method getCG = simulatedData.getClass().getMethod("getCG");
                        Object cgObj = getCG.invoke(simulatedData);
                        if (cgObj instanceof Double) {
                            cg = (Double) cgObj;
                        }
                    } catch (Exception e) {
                        System.out.println("  Could not extract CG from simulation: " + e);
                    }
                    try {
                        java.lang.reflect.Method getCP = simulatedData.getClass().getMethod("getCP");
                        Object cpObj = getCP.invoke(simulatedData);
                        if (cpObj instanceof Double) {
                            cp = (Double) cpObj;
                        }
                    } catch (Exception e) {
                        System.out.println("  Could not extract CP from simulation: " + e);
                    }
                    System.out.println("Simulated Center of Mass (CG): " + cg + " m");
                    System.out.println("Simulated Center of Pressure (CP): " + cp + " m");
                } else {
                    System.out.println("Simulation did not return data for CG/CP extraction.");
                }
            } catch (Exception e) {
                System.err.println("Error extracting CG/CP from simulation: " + e);
            }

            // Extract actual mass data from the rocket design
            System.out.println("\n=== ROCKET MASS DATA ===");
            extractAndDisplayMassData(rocket);

            // Test different nose cone lengths
            double[] noseConeLengths = {0.20};

            System.out.println("\n=== Testing Different Nose Cone Lengths ===");
            for (double noseLength : noseConeLengths) {
                System.out.println("\n--- Testing with Nose Cone Length: " + noseLength + " m ---");
                setNoseConeLength(rocket, noseLength);

                SimulationResult result = runSimulationAndExtractData(rocket, 
                    "NoseCone=" + String.format("%.2f", noseLength) + "m");
                if (result != null) {
                    result.print();
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static OpenRocketDocument loadRocketDocument(File file) {
        try {
            GeneralRocketLoader loader = new GeneralRocketLoader(file);
            OpenRocketDocument document = loader.load();
            System.out.println("Document loaded: " + document.getRocket().getName());
            return document;
        } catch (Exception e) {
            System.err.println("Failed to load: " + e.getMessage());
            return null;
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
            System.out.println("\nComponent Mass Breakdown:");
            System.out.println("─────────────────────────────────────────");
            
            double[] totals = new double[1];  // [totalMass]
            totals[0] = 0.0;
            double motorMass = 0.359;
            
            extractMassRecursive(rocket, 0, totals);
            
            System.out.println("─────────────────────────────────────────");
            System.out.println("TOTAL DRY MASS: " + String.format("%.4f", totals[0]) + " kg");
            System.out.println("TOTAL MOTOR MASS: " + String.format("%.4f", motorMass) + " kg");
            System.out.println("TOTAL WET MASS: " + String.format("%.4f", totals[0] + motorMass) + " kg");
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
                // Store in global list
                massCGList.add(new ComponentMassCG(componentName, componentType, mass, cg, length));
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
    
    private static void setBodyTubeDiameter(Rocket rocket, double diameter) {
        try {
            for (RocketComponent child : rocket.getChildren()) {
                if (child instanceof RocketComponent) {
                    for (RocketComponent subchild : child.getChildren()) {
                        if (subchild instanceof BodyTube) {
                            BodyTube bodyTube = (BodyTube) subchild;
                            bodyTube.setOuterRadius(diameter / 2.0);
                            System.out.println("  - Set body tube diameter to: " + diameter + " m");
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error setting body tube diameter: " + e.getMessage());
        }
    }
    
    private static SimulationResult runSimulationAndExtractData(Rocket rocket, String motorDesignation) {
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
            
            // Extract rocket stability parameters
            double centerOfMass = extractCenterOfMass(rocket);
            double centerOfPressure = extractCenterOfPressure(rocket);
            double safetyMargin = centerOfPressure - centerOfMass;
            double outerDiameter = getRocketOuterDiameter(rocket);
            
            return new SimulationResult(motorDesignation, apogee, timeToApogee, 
                        maxVelocity, maxAcceleration, maxMach, centerOfMass, centerOfPressure, safetyMargin, outerDiameter);
            
        } catch (Exception e) {
            System.err.println("Simulation failed: " + e.getMessage());
            return null;
        }
    }
    
    private static double extractCenterOfMass(Rocket rocket) {
        // Calculate CG using the global massCGList (weighted average)
        double totalMass = 0.0;
        double weightedSum = 0.0;
        for (ComponentMassCG entry : massCGList) {
            if (entry.mass > 0.00001 && entry.cg != null) {
                totalMass += entry.mass;
                weightedSum += entry.mass * entry.cg;
            }
        }
        if (totalMass > 0.0) {
            return weightedSum / totalMass;
        } else {
            return 0.0;
        }
    }
    
    
    private static double extractCenterOfPressure(Rocket rocket) {
        try {
            // Try direct method if it exists
            try {
                java.lang.reflect.Method getCP = rocket.getClass().getMethod("getNormalForceLocation");
                Object cpCoord = getCP.invoke(rocket);
                if (cpCoord != null) {
                    java.lang.reflect.Field xField = cpCoord.getClass().getDeclaredField("x");
                    xField.setAccessible(true);
                    return xField.getDouble(cpCoord);
                }
                System.out.print("this works!");
            } catch (NoSuchMethodException e) {
                // Method doesn't exist, try alternative
            }
            
            // Try alternative method names
            String[] methodNames = {"getAerodynamicCenter", "getCenterOfPressure", "getCP"};
            for (String methodName : methodNames) {
                try {
                    java.lang.reflect.Method method = rocket.getClass().getMethod(methodName);
                    Object cpCoord = method.invoke(rocket);
                    if (cpCoord != null && cpCoord.getClass().getName().contains("Coordinate")) {
                        java.lang.reflect.Field xField = cpCoord.getClass().getDeclaredField("x");
                        xField.setAccessible(true);
                        return xField.getDouble(cpCoord);
                    }
                    System.out.print("this works!");
                } catch (Exception e) {
                    // Try next method
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error extracting center of pressure: " + e.getMessage());
        }
        return 0.0;
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
    
    static class SimulationResult {
        String motor;
        double apogee, timeToApogee, maxVelocity, maxAcceleration, maxMach;
        double centerOfMass, centerOfPressure, safetyMargin, outerDiameter;
        
        SimulationResult(String motor, double apogee, double timeToApogee, 
                        double maxVelocity, double maxAcceleration, double maxMach,
                        double centerOfMass, double centerOfPressure, double safetyMargin,
                        double outerDiameter) {
            this.motor = motor;
            this.apogee = apogee;
            this.timeToApogee = timeToApogee;
            this.maxVelocity = maxVelocity;
            this.maxAcceleration = maxAcceleration;
            this.maxMach = maxMach;
            this.centerOfMass = centerOfMass;
            this.centerOfPressure = centerOfPressure;
            this.safetyMargin = safetyMargin;
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
            
            System.out.println("STABILITY:");
            System.out.println("  Center of Mass: " + String.format("%.3f", centerOfMass) + " m");
            System.out.println("  Center of Pressure: " + String.format("%.3f", centerOfPressure) + " m");
            double marginInCalibers = safetyMargin / outerDiameter;
            System.out.println("  Safety Margin: " + String.format("%.3f", safetyMargin) + " m (" + String.format("%.2f", marginInCalibers) + " calibers)");
            System.out.println("  Status: " + (marginInCalibers > 1.0 ? "STABLE" : "UNSTABLE"));
        }
        
        @Override
        public String toString() {
            return String.format("%s: Apogee=%.1fm, SafetyMargin=%.3fm", 
                motor, apogee, safetyMargin);
        }
    }
}