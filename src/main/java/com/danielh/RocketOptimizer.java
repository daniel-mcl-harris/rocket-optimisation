package com.danielh;

import io.jenetics.*;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionResult;
import io.jenetics.util.DoubleRange;
import info.openrocket.core.rocketcomponent.*;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import me.tongfei.progressbar.ProgressBar;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RocketOptimizer {
    
    // Gene bounds (in meters)
    private static final DoubleRange NOSE_CONE_RANGE = DoubleRange.of(0.1, 1.0);
    private static final DoubleRange BODY_TUBE_RANGE = DoubleRange.of(0.4, 1.0);
    private static final DoubleRange BODY_TUBE_DIAMETER_RANGE = DoubleRange.of(0.03, 0.08);  // 3cm to 8cm
    
    // Shared base rocket for cloning
    private static Rocket baseRocket;
    private static String rocketFilePath;
    
    
    /**
     * Run genetic algorithm optimization for rocket design parameters
     * @param populationSize GA population size
     * @param generations GA generation count
     * @param rocketPath Path to the .ork rocket file
     * @return OptimizationReport with best parameters and results
     */
    public static OptimizationReport optimize(int populationSize, 
                                               int generations, 
                                               String rocketPath) {
        long startTime = System.currentTimeMillis();
        rocketFilePath = rocketPath;
        
        // Load base rocket once
        try {
            OpenRocketDocument document = loadRocketDocument(new File(rocketPath));
            if (document == null) {
                System.err.println("Failed to load rocket document");
                return null;
            }
            baseRocket = document.getRocket();
        } catch (Exception e) {
            System.err.println("Error loading rocket: " + e.getMessage());
            return null;
        }
        
        // Define genotype: 3 continuous genes (nose cone length, body tube length, body tube diameter)
        final Genotype<DoubleGene> genotype = Genotype.of(
            DoubleChromosome.of(NOSE_CONE_RANGE),
            DoubleChromosome.of(BODY_TUBE_RANGE),
            DoubleChromosome.of(BODY_TUBE_DIAMETER_RANGE)
        );
        
        // Build the GA engine with elitism and increased population for diversity
        Engine<DoubleGene, Double> engine = Engine
            .builder(
                f -> fitnessFunction(f),
                genotype
            )
            .populationSize(populationSize)
            .selector(new TournamentSelector<>(5))  
            .alterers(
                new Mutator<>(0.25)  
            )
            .survivorsSelector(new EliteSelector<>(20))
            .offspringSelector(new TournamentSelector<>(5))
            .minimizing()  
            .build();
        
        // Run the GA
        System.out.println("\n=== ROCKET DESIGN OPTIMIZATION (Jenetics) ===");
        System.out.println("Population: " + populationSize + ", Generations: " + generations);
        System.out.println("Optimizing: Nose Cone Length [0.1-1.0 m], Body Tube Length [0.4-1.0 m], Body Tube Diameter [0.03-0.08 m]");
        System.out.println("Objective: Maximize Apogee");
        System.out.println("─────────────────────────────────────────");
        
        // Progress tracking with ProgressBar library
        final Phenotype<DoubleGene, Double>[] bestIndividual = new Phenotype[1];
        
        try (ProgressBar pb = new ProgressBar("Optimization", generations)) {
            engine.stream()
                .limit(generations)
                .forEach(result -> {
                    bestIndividual[0] = result.population().stream()
                        .min((p1, p2) -> Double.compare(p1.fitness(), p2.fitness()))
                        .orElse(null);
                    
                    pb.step();
                });
        }
        
        Phenotype<DoubleGene, Double> best = bestIndividual[0];
        if (best == null) {
            throw new RuntimeException("Optimization failed: No solutions found");
        }
        
        System.out.println("─────────────────────────────────────────");
        
        // Build and return report
        OptimizationReport report = new OptimizationReport(populationSize, generations);
        report.optimizedNoseCone = best.genotype().get(0).get(0).allele();
        report.optimizedBodyTube = best.genotype().get(1).get(0).allele();
        report.optimizedBodyTubeDiameter = best.genotype().get(2).get(0).allele();
        report.bestPhenotype = best;
        report.executionTimeMs = System.currentTimeMillis() - startTime;
        
        return report;
    }
    
    private static Double fitnessFunction(Genotype<DoubleGene> genotype) {
        try {
            // Get gene values from the three chromosomes
            double noseConeLength = genotype.get(0).get(0).allele();  // Chromosome 0, Gene 0
            double bodyTubeLength = genotype.get(1).get(0).allele();  // Chromosome 1, Gene 0
            double bodyTubeDiameter = genotype.get(2).get(0).allele();  // Chromosome 2, Gene 0
            
            // Clone and modify rocket
            Rocket rocket = cloneRocket(baseRocket);
            if (rocket == null) {
                return 0.0;  // Penalize if cloning fails
            }
            
            setNoseConeLength(rocket, noseConeLength);
            setBodyTubeLength(rocket, bodyTubeLength);
            setBodyTubeDiameter(rocket, bodyTubeDiameter);
            
            // Run simulation (adapted from App.java)
            Simulation simulation = new Simulation(rocket);
            simulation.simulate();
            
            // Extract apogee
            java.lang.reflect.Method getSimulatedData = simulation.getClass().getMethod("getSimulatedData");
            Object simulatedData = getSimulatedData.invoke(simulation);
            
            if (simulatedData == null) {
                return 0.0;
            }
            
            double apogee = getDoubleFromMethod(simulatedData, "getMaxAltitude");
            
            // Return negated apogee (minimize = maximize apogee)
            return -apogee;
            
        } catch (Exception e) {
            // Return worst fitness if simulation fails
            return 0.0;
        }
    }
    
    /**
     * Clone a rocket for independent simulation
     */
    private static Rocket cloneRocket(Rocket original) {
        try {
            // Attempt Java clone if available
            Rocket clone = (Rocket) original.clone();
            return clone;
        } catch (Exception e) {
            // Fallback: reload from file for each iteration
            try {
                OpenRocketDocument document = loadRocketDocument(new File(rocketFilePath));
                if (document != null) {
                    return document.getRocket();
                }
            } catch (Exception reloadE) {
                // Both methods failed
            }
            return null;
        }
    }
    
    /**
     * Set nose cone length on all nose cone components
     */
    private static void setNoseConeLength(Rocket rocket, double length) {
        try {
            for (RocketComponent child : rocket.getChildren()) {
                if (child instanceof RocketComponent) {
                    for (RocketComponent subchild : child.getChildren()) {
                        if (subchild instanceof NoseCone) {
                            NoseCone noseCone = (NoseCone) subchild;
                            noseCone.setLength(length);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }
    
    /**
     * Set body tube length on all body tube components
     */
    private static void setBodyTubeLength(Rocket rocket, double length) {
        try {
            for (RocketComponent child : rocket.getChildren()) {
                if (child instanceof RocketComponent) {
                    for (RocketComponent subchild : child.getChildren()) {
                        if (subchild instanceof BodyTube) {
                            BodyTube bodyTube = (BodyTube) subchild;
                            bodyTube.setLength(length);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }
    
    /**
     * Set body tube outer diameter and nose cone base diameter on all components
     * Nose cone base diameter is kept consistent with body tube outer diameter
     */
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
            // Ignore
        }
    }
    
    /**
     * Extract double value from object method via reflection
     */
    private static double getDoubleFromMethod(Object obj, String methodName) {
        try {
            java.lang.reflect.Method method = obj.getClass().getMethod(methodName);
            Object result = method.invoke(obj);
            if (result instanceof Double) {
                return (Double) result;
            }
        } catch (Exception e) {
            // Ignore
        }
        return 0.0;
    }
    
    /**
     * Load rocket document from file
     */
    private static OpenRocketDocument loadRocketDocument(File file) {
        try {
            GeneralRocketLoader loader = new GeneralRocketLoader(file);
            return loader.load();
        } catch (Exception e) {
            System.err.println("Failed to load: " + e.getMessage());
            return null;
        }
    }
}
