package com.danielh;

import io.jenetics.DoubleGene;
import io.jenetics.Phenotype;

public class OptimizationReport {
    public int populationSize;
    public int generations;
    
    public double baselineNoseCone;
    public double baselineBodyTube;
    public double baselineApogee;
    public double baselineTimeToApogee;
    public double baselineMaxVelocity;
    public double baselineMaxAcceleration;
    
    public double optimizedNoseCone;
    public double optimizedBodyTube;
    public double optimizedApogee;
    public double optimizedTimeToApogee;
    public double optimizedMaxVelocity;
    public double optimizedMaxAcceleration;
    
    public long executionTimeMs;
    
    // Store the best phenotype for potential use
    public Phenotype<DoubleGene, Double> bestPhenotype;
    
    public OptimizationReport(int popSize, int gens) {
        this.populationSize = popSize;
        this.generations = gens;
    }
    
    public void generateReport() {
        System.out.println("\n" + "═".repeat(70));
        System.out.println("                    ROCKET OPTIMIZATION REPORT");
        System.out.println("═".repeat(70));
        
        System.out.println("\n[OPTIMIZATION CONFIGURATION]");
        System.out.println("  Population Size:        " + populationSize);
        System.out.println("  Generations:            " + generations);
        System.out.println("  Execution Time:         " + executionTimeMs + " ms");
        System.out.println("  Parameters Optimized:   Nose Cone Length, Body Tube Length");
        System.out.println("  Objective:              Maximize Apogee");
        
        System.out.println("\n[BASELINE DESIGN (Original Parameters)]");
        System.out.println(String.format("  Nose Cone Length:       %.4f m", baselineNoseCone));
        System.out.println(String.format("  Body Tube Length:       %.4f m", baselineBodyTube));
        System.out.println("\n  Flight Performance:");
        System.out.println(String.format("    • Apogee:              %.2f m", baselineApogee));
        System.out.println(String.format("    • Time to Apogee:      %.2f s", baselineTimeToApogee));
        System.out.println(String.format("    • Max Velocity:        %.2f m/s", baselineMaxVelocity));
        System.out.println(String.format("    • Max Acceleration:    %.2f m/s²", baselineMaxAcceleration));
        
        System.out.println("\n[OPTIMIZED DESIGN (GA Result)]");
        System.out.println(String.format("  Nose Cone Length:       %.4f m", optimizedNoseCone));
        System.out.println(String.format("  Body Tube Length:       %.4f m", optimizedBodyTube));
        System.out.println("\n  Flight Performance:");
        System.out.println(String.format("    • Apogee:              %.2f m", optimizedApogee));
        System.out.println(String.format("    • Time to Apogee:      %.2f s", optimizedTimeToApogee));
        System.out.println(String.format("    • Max Velocity:        %.2f m/s", optimizedMaxVelocity));
        System.out.println(String.format("    • Max Acceleration:    %.2f m/s²", optimizedMaxAcceleration));
        
        // Calculate improvements
        double apogeeImprovement = optimizedApogee - baselineApogee;
        double apogeeImprovementPercent = (apogeeImprovement / baselineApogee) * 100.0;
        double velocityImprovement = optimizedMaxVelocity - baselineMaxVelocity;
        double velocityImprovementPercent = (velocityImprovement / baselineMaxVelocity) * 100.0;
        
        System.out.println("\n[PERFORMANCE IMPROVEMENTS]");
        System.out.println(String.format("  Apogee:                 %+.2f m (%+.1f%%)", 
            apogeeImprovement, apogeeImprovementPercent));
        System.out.println(String.format("  Max Velocity:           %+.2f m/s (%+.1f%%)", 
            velocityImprovement, velocityImprovementPercent));
        
        // Design parameter changes
        double noseDiff = optimizedNoseCone - baselineNoseCone;
        double bodyDiff = optimizedBodyTube - baselineBodyTube;
        System.out.println("\n[DESIGN PARAMETER CHANGES]");
        System.out.println(String.format("  Nose Cone Length:       %+.4f m (%+.1f%%)", 
            noseDiff, (noseDiff / baselineNoseCone) * 100.0));
        System.out.println(String.format("  Body Tube Length:       %+.4f m (%+.1f%%)", 
            bodyDiff, (bodyDiff / baselineBodyTube) * 100.0));
        
        System.out.println("\n" + "═".repeat(70));
        
        // Verdict
        if (apogeeImprovement > 0) {
            System.out.println("OPTIMIZATION SUCCESSFUL: Improved apogee by " + 
                String.format("%.1f%%", apogeeImprovementPercent));
        } else if (apogeeImprovement < 0) {
            System.out.println("OPTIMIZATION FAILED: Apogee decreased by " + 
                String.format("%.1f%%", Math.abs(apogeeImprovementPercent)));
        } else {
            System.out.println("OPTIMIZATION FAILED: No improvement found");
        }
        System.out.println("═".repeat(70) + "\n");
    }
}
