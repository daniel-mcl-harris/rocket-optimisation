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
    
    // Save original streams at class load time
    private static final java.io.PrintStream ORIGINAL_OUT = System.out;
    private static final java.io.PrintStream ORIGINAL_ERR = System.err;
    
    // Gene bounds (in meters)
    private static final DoubleRange NOSE_CONE_RANGE = DoubleRange.of(0.1, 1.0);
    private static final DoubleRange BODY_TUBE_RANGE = DoubleRange.of(0.4, 1.0);
    
    // Trapezoidal fin parameters (in meters)
    private static final DoubleRange FIN_ROOT_CHORD_RANGE = DoubleRange.of(0.03, 0.15);
    private static final DoubleRange FIN_TIP_CHORD_RANGE = DoubleRange.of(0.01, 0.08);
    private static final DoubleRange FIN_HEIGHT_RANGE = DoubleRange.of(0.03, 0.15);
    private static final DoubleRange FIN_SWEEP_LENGTH_RANGE = DoubleRange.of(0.0, 0.1);
    
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
                                               String rocketPath,
                                               double baselineNose,
                                               double baselineBody,
                                               double baselineRootChord,
                                               double baselineTipChord,
                                               double baselineHeight,
                                               double baselineSweepLength) {
        long startTime = System.currentTimeMillis();
        rocketFilePath = rocketPath;
        
        // Load base rocket once (output will be suppressed during fitness evaluations)
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
        
        // Define genotype: 6 continuous genes (nose cone length, body tube length, and 4 fin parameters)
        // Define genotype template: 6 continuous genes with random ranges
        final Genotype<DoubleGene> genotype = Genotype.of(
            DoubleChromosome.of(NOSE_CONE_RANGE),
            DoubleChromosome.of(BODY_TUBE_RANGE),
            DoubleChromosome.of(FIN_ROOT_CHORD_RANGE),
            DoubleChromosome.of(FIN_TIP_CHORD_RANGE),
            DoubleChromosome.of(FIN_HEIGHT_RANGE),
            DoubleChromosome.of(FIN_SWEEP_LENGTH_RANGE)
        );
        
        // Build the GA engine with random population
        Engine<DoubleGene, Double> engine = Engine
            .builder(
                f -> fitnessFunction(f),
                genotype
            )
            .populationSize(populationSize)
            .selector(new TournamentSelector<>(3))  
            .alterers(
                new SinglePointCrossover<>(0.5),    // 50% crossover chance
                new Mutator<>(0.8)                   // 80% mutation
            )
            .survivorsSelector(new EliteSelector<>(Math.max(populationSize / 10, 5)))  // Elite 10% or 5, whichever is larger
            .offspringSelector(new TournamentSelector<>(3))
            .minimizing()  
            .build();
        
        // Run the GA
        System.out.println("\n=== ROCKET DESIGN OPTIMIZATION (Jenetics) ===");
        System.out.println("Population: " + populationSize + ", Generations: " + generations);
        System.out.println("Optimizing: Nose Cone Length [0.1-1.0 m], Body Tube Length [0.4-1.0 m]");
        System.out.println("            Fin Root Chord [0.03-0.15 m], Fin Tip Chord [0.01-0.08 m]");
        System.out.println("            Fin Height [0.03-0.15 m], Fin Sweep Length [0.0-0.1 m]");
        System.out.println("Objective: Maximize Apogee");
        System.out.println("─────────────────────────────────────────");
        
        // Progress tracking with ProgressBar library
        // Evaluate baseline upfront and use it as initial best
        // This ensures the initial population effectively includes the baseline
        final Genotype<DoubleGene> baselineGenotype = Genotype.of(
            DoubleChromosome.of(DoubleGene.of(baselineNose, NOSE_CONE_RANGE.min(), NOSE_CONE_RANGE.max())),
            DoubleChromosome.of(DoubleGene.of(baselineBody, BODY_TUBE_RANGE.min(), BODY_TUBE_RANGE.max())),
            DoubleChromosome.of(DoubleGene.of(baselineRootChord, FIN_ROOT_CHORD_RANGE.min(), FIN_ROOT_CHORD_RANGE.max())),
            DoubleChromosome.of(DoubleGene.of(baselineTipChord, FIN_TIP_CHORD_RANGE.min(), FIN_TIP_CHORD_RANGE.max())),
            DoubleChromosome.of(DoubleGene.of(baselineHeight, FIN_HEIGHT_RANGE.min(), FIN_HEIGHT_RANGE.max())),
            DoubleChromosome.of(DoubleGene.of(baselineSweepLength, FIN_SWEEP_LENGTH_RANGE.min(), FIN_SWEEP_LENGTH_RANGE.max()))
        );
        double baselineFitness = fitnessFunction(baselineGenotype);
        final Phenotype<DoubleGene, Double> baselineIndividual = Phenotype.of(baselineGenotype, 1, baselineFitness);
        
        // Track the BEST individual across ALL generations, starting with baseline
        final Phenotype<DoubleGene, Double>[] globalBest = new Phenotype[1];
        globalBest[0] = baselineIndividual;
        final double[] generationCounter = {0};
        
        try (ProgressBar pb = new ProgressBar("Optimization", generations)) {
            engine.stream()
                .limit(generations)
                .forEach(result -> {
                    generationCounter[0]++;
                    // Find the best in the current population
                    Phenotype<DoubleGene, Double> generationBest = result.population().stream()
                        .min((p1, p2) -> Double.compare(p1.fitness(), p2.fitness()))
                        .orElse(null);
                    
                    // Update global best if this generation is better than baseline
                    if (generationBest != null) {
                        if (generationBest.fitness() < globalBest[0].fitness()) {
                            globalBest[0] = generationBest;
                        }
                    }
                    
                    pb.step();
                });
        }
        
        // Ensure System.out is restored before returning report
        System.setOut(ORIGINAL_OUT);
        System.setErr(ORIGINAL_ERR);
        
        System.out.println("─────────────────────────────────────────");
        
        // Build and return report - use baseline parameters if GA result is worse
        OptimizationReport report = new OptimizationReport(populationSize, generations);
        
        // Use baseline if GA result is worse (remember: more negative = better for minimization)
        if (globalBest[0].fitness() > baselineFitness) {
            System.out.println("GA result worse than baseline. Using baseline parameters as final result.");
            report.optimizedNoseCone = baselineNose;
            report.optimizedBodyTube = baselineBody;
            report.optimizedFinRootChord = baselineRootChord;
            report.optimizedFinTipChord = baselineTipChord;
            report.optimizedFinHeight = baselineHeight;
            report.optimizedFinSweepLength = baselineSweepLength;
        } else {
            report.optimizedNoseCone = globalBest[0].genotype().get(0).get(0).allele();
            report.optimizedBodyTube = globalBest[0].genotype().get(1).get(0).allele();
            report.optimizedFinRootChord = globalBest[0].genotype().get(2).get(0).allele();
            report.optimizedFinTipChord = globalBest[0].genotype().get(3).get(0).allele();
            report.optimizedFinHeight = globalBest[0].genotype().get(4).get(0).allele();
            report.optimizedFinSweepLength = globalBest[0].genotype().get(5).get(0).allele();
        }
        
        report.bestPhenotype = globalBest[0];
        report.executionTimeMs = System.currentTimeMillis() - startTime;
        
        return report;
    }
    
    private static Double fitnessFunction(Genotype<DoubleGene> genotype) {
        java.io.PrintStream originalOut = System.out;
        java.io.PrintStream originalErr = System.err;
        System.setOut(new FilteringPrintStream(originalOut));
        System.setErr(new FilteringPrintStream(originalErr));
        try {
            double noseConeLength = genotype.get(0).get(0).allele();
            double bodyTubeLength = genotype.get(1).get(0).allele();
            double finRootChord = genotype.get(2).get(0).allele();
            double finTipChord = genotype.get(3).get(0).allele();
            double finHeight = genotype.get(4).get(0).allele();
            double finSweepLength = genotype.get(5).get(0).allele();
            
            Rocket rocket = cloneRocket(baseRocket);
            if (rocket == null) {
                return 1000.0;
            }
            
            setNoseConeLength(rocket, noseConeLength);
            setBodyTubeLength(rocket, bodyTubeLength);
            setFinParameters(rocket, finRootChord, finTipChord, finHeight, finSweepLength);
            
            Simulation simulation = new Simulation(rocket);
            simulation.simulate();
            
            java.lang.reflect.Method getSimulatedData = simulation.getClass().getMethod("getSimulatedData");
            Object simulatedData = getSimulatedData.invoke(simulation);
            
            if (simulatedData == null) {
                return 1000.0;
            }
            
            double apogee = getDoubleFromMethod(simulatedData, "getMaxAltitude");
            return -apogee;
            
        } catch (Exception e) {
            return 1000.0;
        } finally {
            System.setOut(ORIGINAL_OUT);
            System.setErr(ORIGINAL_ERR);
        }
    }
    
    /**
     * Clone a rocket by reloading from file with FilteringPrintStream to suppress "Loading" messages
     */
    private static Rocket cloneRocket(Rocket original) {
        java.io.PrintStream originalOut = System.out;
        java.io.PrintStream originalErr = System.err;
        System.setOut(new FilteringPrintStream(originalOut));
        System.setErr(new FilteringPrintStream(originalErr));
        try {
            try {
                OpenRocketDocument document = new GeneralRocketLoader(new File(rocketFilePath)).load();
                if (document != null) {
                    return document.getRocket();
                }
            } catch (Exception e) {
                // Silently fail
            }
            return null;
        } finally {
            System.setOut(ORIGINAL_OUT);
            System.setErr(ORIGINAL_ERR);
        }
    }
    
    /**
     * Load rocket document from byte array without file I/O spam
     */
    private static OpenRocketDocument loadRocketDocumentFromBytes(byte[] data) {
        // No longer used
        return null;
    }

    /**
     * Load rocket document while filtering out OpenRocket's "Loading" messages
     */
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
            System.setOut(ORIGINAL_OUT);
            System.setErr(ORIGINAL_ERR);
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
     * Set trapezoidal fin parameters on all fin sets (recursive search)
     */
    private static void setFinParameters(Rocket rocket, double rootChord, double tipChord, 
                                        double height, double sweepLength) {
        setFinParametersRecursive(rocket, rootChord, tipChord, height, sweepLength);
    }
    
    /**
     * Recursively search and update fin parameters
     */
    private static void setFinParametersRecursive(RocketComponent component, double rootChord,
                                                  double tipChord, double height, double sweepLength) {
        for (RocketComponent child : component.getChildren()) {
            String className = child.getClass().getSimpleName();
            
            if (className.contains("Fin")) {
                try {
                    // Try to set root chord
                    try {
                        java.lang.reflect.Method setRootChord = child.getClass()
                            .getMethod("setRootChord", double.class);
                        setRootChord.invoke(child, rootChord);
                    } catch (Exception e1) {
                        // Ignore if method doesn't exist
                    }
                    
                    // Try to set tip chord
                    try {
                        java.lang.reflect.Method setTipChord = child.getClass()
                            .getMethod("setTipChord", double.class);
                        setTipChord.invoke(child, tipChord);
                    } catch (Exception e1) {
                        // Ignore if method doesn't exist
                    }
                    
                    // Try to set height
                    try {
                        java.lang.reflect.Method setHeight = child.getClass()
                            .getMethod("setHeight", double.class);
                        setHeight.invoke(child, height);
                    } catch (Exception e1) {
                        // Ignore if method doesn't exist
                    }
                    
                    // Try to set sweep length
                    try {
                        java.lang.reflect.Method setSweep = child.getClass()
                            .getMethod("setSweep", double.class);
                        setSweep.invoke(child, sweepLength);
                    } catch (Exception e1) {
                        // Ignore if method doesn't exist
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }
            
            // Recursively search children
            setFinParametersRecursive(child, rootChord, tipChord, height, sweepLength);
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
    
}

