package com.danielh;

import io.jenetics.*;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionResult;
import io.jenetics.util.DoubleRange;
import info.openrocket.core.rocketcomponent.*;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RocketOptimizer {
    
    // Gene bounds (in meters)
    private static final DoubleRange NOSE_CONE_RANGE = DoubleRange.of(0.1, 1.0);
    private static final DoubleRange BODY_TUBE_RANGE = DoubleRange.of(0.4, 1.0);
    
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
        
        // Define genotype: 2 continuous genes (nose cone length, body tube length)
        final Genotype<DoubleGene> genotype = Genotype.of(
            DoubleChromosome.of(NOSE_CONE_RANGE),
            DoubleChromosome.of(BODY_TUBE_RANGE)
        );
        
        // Build the GA engine with elitism to preserve best solutions
        Engine<DoubleGene, Double> engine = Engine
            .builder(
                f -> fitnessFunction(f),  // Fitness function
                genotype                   // Genotype template
            )
            .populationSize(populationSize)
            .selector(new TournamentSelector<>(3))  // Balanced selection pressure (was 50)
            .alterers(
                new Mutator<>(0.5)  // 50% mutation rate for exploration
            )
            .survivorsSelector(new EliteSelector<>())  // Always keep best individuals
            .offspringSelector(new TournamentSelector<>(3))  // Select best for reproduction
            .build();
        
        // Run the GA
        System.out.println("\n=== ROCKET DESIGN OPTIMIZATION (Jenetics) ===");
        System.out.println("Population: " + populationSize + ", Generations: " + generations);
        System.out.println("Optimizing: Nose Cone Length [0.1-1.0 m], Body Tube Length [0.4-1.0 m]");
        System.out.println("Objective: Maximize Apogee");
        System.out.println("─────────────────────────────────────────");
        
        // Progress tracking
        final Phenotype<DoubleGene, Double>[] bestIndividual = new Phenotype[1];
        final int updateInterval = Math.max(1, generations / 20);  // Show progress ~20 times
        
        engine.stream()
            .limit(generations)
            .forEach(result -> {
                bestIndividual[0] = result.population().stream()
                    .min((p1, p2) -> Double.compare(p1.fitness(), p2.fitness()))
                    .orElse(null);

                // Print progress every updateInterval generations
                int gen = (int) result.generation();
                if (gen % updateInterval == 0 || gen == 1 || gen == generations) {
                    int barWidth = 30;
                    double progress = (double) gen / generations;
                    int filled = (int) (barWidth * progress);
                    
                    StringBuilder progressBar = new StringBuilder("[");
                    for (int i = 0; i < barWidth; i++) {
                        if (i < filled) progressBar.append("█");
                        else progressBar.append("░");
                    }
                    progressBar.append("] ");
                    progressBar.append(String.format("%.1f%%", progress * 100));
                    progressBar.append(" | Gen ");
                    progressBar.append(gen).append("/").append(generations);
                    
                    System.out.println(progressBar.toString());
                }
            });
        
        Phenotype<DoubleGene, Double> best = bestIndividual[0];
        if (best == null) {
            throw new RuntimeException("Optimization failed: No solutions found");
        }
        
        System.out.println("─────────────────────────────────────────");
        
        // Build and return report
        OptimizationReport report = new OptimizationReport(populationSize, generations);
        report.optimizedNoseCone = best.genotype().get(0).get(0).allele();
        report.optimizedBodyTube = best.genotype().get(1).get(0).allele();
        report.bestPhenotype = best;
        report.executionTimeMs = System.currentTimeMillis() - startTime;
        
        return report;
    }
    
    private static Double fitnessFunction(Genotype<DoubleGene> genotype) {
        try {
            // Get gene values from the two chromosomes
            double noseConeLength = genotype.get(0).get(0).allele();  // Chromosome 0, Gene 0
            double bodyTubeLength = genotype.get(1).get(0).allele();  // Chromosome 1, Gene 0
            
            // Clone and modify rocket
            Rocket rocket = cloneRocket(baseRocket);
            if (rocket == null) {
                return 0.0;  // Penalize if cloning fails
            }
            
            setNoseConeLength(rocket, noseConeLength);
            setBodyTubeLength(rocket, bodyTubeLength);
            
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
