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
    
    // Motor dictionary with motor specifications
    private static final java.util.Map<String, MotorSpec> MOTOR_DICTIONARY = initializeMotorDictionary();
    
    private static java.util.Map<String, MotorSpec> initializeMotorDictionary() {
        java.util.Map<String, MotorSpec> motors = new java.util.HashMap<>();
        motors.put("I350R-0", new MotorSpec("I350R-0", 0.356, 0.6135));
        return motors;
    }
    
    static class MotorSpec {
        String name;
        double length;
        double mass;
        
        MotorSpec(String name, double length, double mass) {
            this.name = name;
            this.length = length;
            this.mass = mass;
        }
    }
    
    public static void main(String[] args) {
        OpenRocketCore.initialize();
        String rocketPath = "rockets\\GA_base_rocket.ork";
        
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
            
            // Validate motor type
            if (!MOTOR_DICTIONARY.containsKey(motorType)) {
                System.err.println("Unknown motor type: " + motorType);
                System.err.println("Available motors: " + MOTOR_DICTIONARY.keySet());
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
            
            SimulationResult baselineResult = runSimulation(baselineRocket);
            
            OptimisationReport report = RocketOptimizer.optimise(population, generations, rocketPath,
                baselineNose, baselineBody, baselineRootChord, baselineTipChord, 
                baselineHeight, baselineSweep, motorType);
            
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
            // Extract CG for baseline rocket
            CGBreakdown baselineCG = getComponentCenterOfGravityWithBreakdown(baselineRocket, motorType);
            report.baselineCenterOfGravity = baselineCG.cgPosition;
            report.baselineComponentCG = baselineCG.componentDetails;
            report.baselineCenterOfPressure = getComponentCenterOfPressure(baselineRocket);
            
            // Calculate stability margin
            double baselineTotalLength = getTotalRocketLength(baselineRocket);
            report.baselineCenterOfPressure = 0.8 * baselineTotalLength; // CP = 80% of rocket length
            report.caliber = getBodyTubeDiameter(baselineRocket);
            double baseSM = report.baselineCenterOfPressure - report.baselineCenterOfGravity;
            report.baselineStabilityMarginCalibres = (report.caliber > 0) ? baseSM / report.caliber : 0;
            
            if (baselineResult != null) {
                report.baselineApogee = baselineResult.apogee;
                report.baselineTimeToApogee = baselineResult.timeToApogee;
                report.baselineMaxVelocity = baselineResult.maxVelocity;
                report.baselineMaxAcceleration = baselineResult.maxAcceleration;
            }
            
            OpenRocketDocument optimisedDoc = loadRocket(new File(rocketPath));
            if (optimisedDoc != null) {
                Rocket optimisedRocket = optimisedDoc.getRocket();
                setComponentLength(optimisedRocket, NoseCone.class, report.optimisedNoseCone);
                setComponentLength(optimisedRocket, BodyTube.class, report.optimisedBodyTube);
                setFinParameters(optimisedRocket, report.optimisedFinRootChord, 
                    report.optimisedFinTipChord, report.optimisedFinHeight, report.optimisedFinSweepLength);
                
                SimulationResult optimisedResult = runSimulation(optimisedRocket);
                if (optimisedResult != null) {
                    report.optimisedApogee = optimisedResult.apogee;
                    report.optimisedTimeToApogee = optimisedResult.timeToApogee;
                    report.optimisedMaxVelocity = optimisedResult.maxVelocity;
                    report.optimisedMaxAcceleration = optimisedResult.maxAcceleration;
                }
                // Extract CG for optimised rocket
                CGBreakdown optimisedCG = getComponentCenterOfGravityWithBreakdown(optimisedRocket, motorType);
                report.optimisedCenterOfGravity = optimisedCG.cgPosition;
                report.optimisedComponentCG = optimisedCG.componentDetails;
                report.optimisedCenterOfPressure = getComponentCenterOfPressure(optimisedRocket);
                
                // Calculate stability margin for optimised design
                double optimisedTotalLength = getTotalRocketLength(optimisedRocket);
                report.optimisedCenterOfPressure = 0.8 * optimisedTotalLength; // CP = 80% of rocket length
                double optSM = report.optimisedCenterOfPressure - report.optimisedCenterOfGravity;
                report.optimisedStabilityMarginCalibres = (report.caliber > 0) ? optSM / report.caliber : 0;
                
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
    
    private static class CGData {
        double totalMass = 0.0;
        double totalMoment = 0.0;
        java.util.List<String> componentBreakdown = new java.util.ArrayList<>();
    }
    
    public static double getComponentCenterOfGravityWithMotor(Rocket rocket, String motorType) {
        try {
            // Calculate weighted CG from all components including motor
            CGData data = new CGData();
            
            // Get nose cone and body tube lengths for fin set calculation
            double noseConeLength = getComponentLength(rocket, NoseCone.class);
            double bodyTubeLength = getComponentLength(rocket, BodyTube.class);
            
            // Recursively process all components
            for (RocketComponent child : rocket.getChildren()) {
                processComponentCG(child, data, rocket, noseConeLength, bodyTubeLength);
            }
            
            // Add motor mass to CG calculation
            MotorSpec motor = MOTOR_DICTIONARY.get(motorType);
            if (motor != null) {
                double motorCGFromTip = noseConeLength + bodyTubeLength - (motor.length / 2.0);
                data.totalMass += motor.mass;
                data.totalMoment += motor.mass * motorCGFromTip;
            }
            
            if (data.totalMass > 0.001) {
                double cgPosition = data.totalMoment / data.totalMass;
                return cgPosition;
            }
        } catch (Exception e) {
            System.err.println("DEBUG CG Exception: " + e.getMessage());
        }
        return 0.0;
    }
    
    public static double getBodyTubeDiameter(Rocket rocket) {
        try {
            for (RocketComponent child : rocket.getChildren()) {
                for (RocketComponent subchild : child.getChildren()) {
                    if (subchild instanceof BodyTube) {
                        Object diamObj = subchild.getClass().getMethod("getOuterRadius").invoke(subchild);
                        if (diamObj instanceof Number) {
                            // Diameter = 2 * radius
                            return ((Number) diamObj).doubleValue() * 2.0;
                        }
                    }
                }
            }
        } catch (Exception e) {
        }
        return 0.1; // Default caliber if not found
    }
    
    public static double getTotalRocketLength(Rocket rocket) {
        double noseLength = getComponentLength(rocket, NoseCone.class);
        double bodyLength = getComponentLength(rocket, BodyTube.class);
        return noseLength + bodyLength;
    }
    
    public static class CGBreakdown {
        public double cgPosition;
        public String[] componentDetails;
        
        public CGBreakdown(double cgPos, java.util.List<String> components) {
            this.cgPosition = cgPos;
            this.componentDetails = components.toArray(new String[0]);
        }
    }
    
    private static CGBreakdown getComponentCenterOfGravityWithBreakdown(Rocket rocket, String motorType) {
        try {
            // Calculate weighted CG from all components
            CGData data = new CGData();
            
            // Get nose cone and body tube lengths for fin set calculation
            double noseConeLength = getComponentLength(rocket, NoseCone.class);
            double bodyTubeLength = getComponentLength(rocket, BodyTube.class);
            
            // Recursively process all components
            for (RocketComponent child : rocket.getChildren()) {
                processComponentCG(child, data, rocket, noseConeLength, bodyTubeLength);
            }
            
            // Add motor mass to CG calculation
            MotorSpec motor = MOTOR_DICTIONARY.get(motorType);
            if (motor != null) {
                double motorCGFromTip = noseConeLength + bodyTubeLength - (motor.length / 2.0);
                
                String motorDetail = String.format("    Motor (%s): %.4f m (mass: %.4f kg)",
                    motorType, motorCGFromTip, motor.mass);
                data.componentBreakdown.add(motorDetail);
                
                data.totalMass += motor.mass;
                data.totalMoment += motor.mass * motorCGFromTip;
            }
            
            if (data.totalMass > 0.001) {
                double cgPosition = data.totalMoment / data.totalMass;
                return new CGBreakdown(cgPosition, data.componentBreakdown);
            }
        } catch (Exception e) {
            System.err.println("DEBUG CG Exception: " + e.getMessage());
        }
        return new CGBreakdown(0.0, java.util.Collections.emptyList());
    }
    
    private static void processComponentCG(RocketComponent component, CGData data, Rocket rocket, 
                                           double noseConeLength, double bodyTubeLength) {
        try {
            // Get component mass
            double mass = (double) component.getClass().getMethod("getMass").invoke(component);
            
            if (mass > 0.001) {
                // Special handling for FinSet
                if (component.getClass().getSimpleName().contains("FinSet")) {
                    // For fin sets: CG_finset = nose_cone_length + body_tube_length - fin_root_chord + fin_CG
                    try {
                        // Get fin root chord
                        Object rootChordObj = component.getClass().getMethod("getRootChord").invoke(component);
                        double rootChord = (rootChordObj instanceof Number) ? ((Number) rootChordObj).doubleValue() : 0.0;
                        
                        // Get the fin set's internal CG (relative to fin set start)
                        Object cgCoord = component.getClass().getMethod("getComponentCG").invoke(component);
                        if (cgCoord != null) {
                            Object xValue = cgCoord.getClass().getField("x").get(cgCoord);
                            if (xValue instanceof Number) {
                                double finCG = ((Number) xValue).doubleValue();
                                
                                // Calculate absolute CG: nose + body - root_chord + fin_CG
                                double absoluteCGX = noseConeLength + bodyTubeLength - rootChord + finCG;
                                
                                String detail = String.format("    %s: %.4f m (mass: %.4f kg)",
                                    component.getName(), absoluteCGX, mass);
                                data.componentBreakdown.add(detail);
                                
                                data.totalMass += mass;
                                data.totalMoment += mass * absoluteCGX;
                                return;
                            }
                        }
                    } catch (Exception e) {
                        // Fall through to standard calculation if special handling fails
                    }
                }
                
                // Standard component CG calculation (for nose cone, body tube, etc.)
                Object cgCoord = component.getClass().getMethod("getComponentCG").invoke(component);
                if (cgCoord != null) {
                    Object xValue = cgCoord.getClass().getField("x").get(cgCoord);
                    if (xValue instanceof Number) {
                        double componentCGX = ((Number) xValue).doubleValue();
                        
                        // Get component position in rocket (axial offset)
                        Object posCoord = component.getClass().getMethod("getPosition").invoke(component);
                        if (posCoord != null) {
                            Object posXValue = posCoord.getClass().getField("x").get(posCoord);
                            if (posXValue instanceof Number) {
                                double componentPosition = ((Number) posXValue).doubleValue();
                                
                                // Calculate absolute position of component CG
                                double absoluteCGX = componentPosition + componentCGX;
                                
                                String detail = String.format("    %s: %.4f m (mass: %.4f kg)",
                                    component.getName(), absoluteCGX, mass);
                                data.componentBreakdown.add(detail);
                                
                                data.totalMass += mass;
                                data.totalMoment += mass * absoluteCGX;
                            }
                        }
                    }
                }
            }
            
            // Process child components
            for (RocketComponent child : component.getChildren()) {
                processComponentCG(child, data, rocket, noseConeLength, bodyTubeLength);
            }
        } catch (Exception e) {
            // Silently skip components that fail
        }
    }
    
    private static double getComponentCenterOfPressure(Rocket rocket) {
        try {
            // Center of Pressure needs to be calculated using FlightConditions
            // For now, return 0 as it requires more complex calculation
            // This would need: rocket.getAerodynamicCalculator().getCP(flightConditions)
        } catch (Exception e) {
            // Silently fail
        }
        return 0.0;
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
