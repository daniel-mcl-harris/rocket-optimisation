package com.danielh;

import io.jenetics.DoubleGene;
import io.jenetics.Phenotype;

public class OptimisationReport {
    public int populationSize;
    public int generations;
    
    public double baselineNoseCone;
    public double baselineBodyTube;
    public double baselineFinRootChord;
    public double baselineFinTipChord;
    public double baselineFinHeight;
    public double baselineFinSweepLength;
    public double baselineApogee;
    public double baselineTimeToApogee;
    public double baselineMaxVelocity;
    public double baselineMaxAcceleration;
    
    public double optimisedNoseCone;
    public double optimisedBodyTube;
    public double optimisedFinRootChord;
    public double optimisedFinTipChord;
    public double optimisedFinHeight;
    public double optimisedFinSweepLength;
    public double optimisedApogee;
    public double optimisedTimeToApogee;
    public double optimisedMaxVelocity;
    public double optimisedMaxAcceleration;
    
    public long executionTimeMs;
    
    // Store the best phenotype for potential use
    public Phenotype<DoubleGene, Double> bestPhenotype;
    
    public OptimisationReport(int popSize, int gens) {
        this.populationSize = popSize;
        this.generations = gens;
    }
    
    public void generateReport() {
        System.out.println("\n" + "═".repeat(70));
        System.out.println("                    ROCKET OPTIMISATION REPORT");
        System.out.println("═".repeat(70));
        
        System.out.println("\n[OPTIMISATION CONFIGURATION]");
        System.out.println("  Population Size:        " + populationSize);
        System.out.println("  Generations:            " + generations);
        long totalSeconds = executionTimeMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        System.out.println(String.format("  Execution Time:         %02d:%02d:%02d", hours, minutes, seconds));
        
        System.out.println("\n[BASE DESIGN]");
        System.out.println(String.format("  Nose Cone Length:       %.4f m", baselineNoseCone));
        System.out.println(String.format("  Body Tube Length:       %.4f m", baselineBodyTube));
        System.out.println(String.format("  Fin Root Chord:         %.4f m", baselineFinRootChord));
        System.out.println(String.format("  Fin Tip Chord:          %.4f m", baselineFinTipChord));
        System.out.println(String.format("  Fin Height:             %.4f m", baselineFinHeight));
        System.out.println(String.format("  Fin Sweep Length:       %.4f m", baselineFinSweepLength));
        System.out.println("\n  Flight Performance:");
        System.out.println(String.format("  Apogee:                 %.2f m", baselineApogee));
        System.out.println(String.format("  Time to Apogee:         %.2f s", baselineTimeToApogee));
        System.out.println(String.format("  Max Velocity:           %.2f m/s", baselineMaxVelocity));
        System.out.println(String.format("  Max Acceleration:       %.2f m/s²", baselineMaxAcceleration));
        
        System.out.println("\n[OPTIMISED DESIGN]");
        System.out.println(String.format("  Nose Cone Length:       %.4f m", optimisedNoseCone));
        System.out.println(String.format("  Body Tube Length:       %.4f m", optimisedBodyTube));
        System.out.println(String.format("  Fin Root Chord:         %.4f m", optimisedFinRootChord));
        System.out.println(String.format("  Fin Tip Chord:          %.4f m", optimisedFinTipChord));
        System.out.println(String.format("  Fin Height:             %.4f m", optimisedFinHeight));
        System.out.println(String.format("  Fin Sweep Length:       %.4f m", optimisedFinSweepLength));
        System.out.println("\n  Flight Performance:");
        System.out.println(String.format("  Apogee:                 %.2f m", optimisedApogee));
        System.out.println(String.format("  Time to Apogee:         %.2f s", optimisedTimeToApogee));
        System.out.println(String.format("  Max Velocity:           %.2f m/s", optimisedMaxVelocity));
        System.out.println(String.format("  Max Acceleration:       %.2f m/s²", optimisedMaxAcceleration));
        
        // Calculate improvements
        double apogeeImprovement = optimisedApogee - baselineApogee;
        double apogeeImprovementPercent = (apogeeImprovement / baselineApogee) * 100.0;
        double velocityImprovement = optimisedMaxVelocity - baselineMaxVelocity;
        double velocityImprovementPercent = (velocityImprovement / baselineMaxVelocity) * 100.0;
        
        System.out.println("\n[PERFORMANCE IMPROVEMENTS]");
        System.out.println(String.format("  Apogee:                 %+.2f m (%+.1f%%)", 
            apogeeImprovement, apogeeImprovementPercent));
        System.out.println(String.format("  Max Velocity:           %+.2f m/s (%+.1f%%)", 
            velocityImprovement, velocityImprovementPercent));
        
        System.out.println("\n" + "═".repeat(70));
        
        // Verdict
        if (apogeeImprovement > 0) {
            System.out.print("                      OPTIMISATION SUCCESSFUL\n");
        } else {
            System.out.println("                        OPTIMISATION FAILED\n");
        }
        System.out.println("═".repeat(70) + "\n");
    }
}
