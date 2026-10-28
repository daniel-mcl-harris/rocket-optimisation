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
    public double baselineLaunchRodVelocity;
    public double baselineFlightTime;
    public double baselineMinFlightStabilityCalibres;
    public double baselineMaxFlightStabilityCalibres;
    public double baselineLaunchMass;
    public double baselineBurnoutMass;
    public double baselineDryMass;
    public double baselineBurnoutCenterOfGravity;
    public double baselineCenterOfPressureZeroAngle;
    
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
    public double optimisedLaunchRodVelocity;
    public double optimisedFlightTime;
    public double optimisedMinFlightStabilityCalibres;
    public double optimisedMaxFlightStabilityCalibres;
    public double optimisedLaunchMass;
    public double optimisedBurnoutMass;
    public double optimisedDryMass;
    public double optimisedBurnoutCenterOfGravity;
    public double optimisedCenterOfPressureZeroAngle;
    
    // Center of Pressure and Center of Gravity
    public double baselineCenterOfGravity;
    public double baselineCenterOfPressure;
    public double optimisedCenterOfGravity;
    public double optimisedCenterOfPressure;
    
    // Stability margins (in calibres)
    public double baselineStabilityMarginCalibres;
    public double optimisedStabilityMarginCalibres;
    
    // Caliber (body tube diameter)
    public double caliber;
    
    // Component CG breakdowns
    public String[] baselineComponentCG;
    public String[] optimisedComponentCG;
    
    public long executionTimeMs;
    
    // Store the best phenotype for potential use
    public Phenotype<DoubleGene, Double> bestPhenotype;
    
    public OptimisationReport(int popSize, int gens) {
        this.populationSize = popSize;
        this.generations = gens;
    }
    
    public void generateReport() {
        String border = "=".repeat(70);
        System.out.println("\n" + border);
        System.out.println("                    ROCKET OPTIMISATION REPORT");
        System.out.println(border);
        
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
        System.out.println(String.format("  Center of Gravity:      %.4f m  (launch, with propellant)", baselineCenterOfGravity));
        System.out.println(String.format("  Launch Mass:            %.4f kg", baselineLaunchMass));
        System.out.println(String.format("  Burnout CG:             %.4f m", baselineBurnoutCenterOfGravity));
        System.out.println(String.format("  Burnout Mass:           %.4f kg", baselineBurnoutMass));
        System.out.println(String.format("  Dry Mass:               %.4f kg", baselineDryMass));
        if (baselineComponentCG != null && baselineComponentCG.length > 0) {
            System.out.println("    Component Breakdown:");
            for (String component : baselineComponentCG) {
                System.out.println(component);
            }
        }
        System.out.println(String.format("  Center of Pressure:     %.4f m  (Barrowman, most forward)", baselineCenterOfPressure));
        System.out.println(String.format("  CP at 0 deg wind:       %.4f m", baselineCenterOfPressureZeroAngle));
        double baseSMAbsolute = baselineCenterOfPressure - baselineCenterOfGravity;
        System.out.println(String.format("  Stability Margin:       %.4f m (%.2f calibres)", 
            baseSMAbsolute, baselineStabilityMarginCalibres));
        System.out.println(String.format("  In-flight Stability:    %.2f to %.2f calibres",
            baselineMinFlightStabilityCalibres, baselineMaxFlightStabilityCalibres));
        System.out.println("\n  Flight Performance:");
        System.out.println(String.format("  Apogee:                 %.2f m", baselineApogee));
        System.out.println(String.format("  Time to Apogee:         %.2f s", baselineTimeToApogee));
        System.out.println(String.format("  Flight Time:            %.2f s", baselineFlightTime));
        System.out.println(String.format("  Max Velocity:           %.2f m/s", baselineMaxVelocity));
        System.out.println(String.format("  Max Acceleration:       %.2f m/s^2", baselineMaxAcceleration));
        System.out.println(String.format("  Velocity off Rod:       %.2f m/s", baselineLaunchRodVelocity));
        
        System.out.println("\n[OPTIMISED DESIGN]");
        System.out.println(String.format("  Nose Cone Length:       %.4f m", optimisedNoseCone));
        System.out.println(String.format("  Body Tube Length:       %.4f m", optimisedBodyTube));
        System.out.println(String.format("  Fin Root Chord:         %.4f m", optimisedFinRootChord));
        System.out.println(String.format("  Fin Tip Chord:          %.4f m", optimisedFinTipChord));
        System.out.println(String.format("  Fin Height:             %.4f m", optimisedFinHeight));
        System.out.println(String.format("  Fin Sweep Length:       %.4f m", optimisedFinSweepLength));
        System.out.println(String.format("  Center of Gravity:      %.4f m  (launch, with propellant)", optimisedCenterOfGravity));
        System.out.println(String.format("  Launch Mass:            %.4f kg", optimisedLaunchMass));
        System.out.println(String.format("  Burnout CG:             %.4f m", optimisedBurnoutCenterOfGravity));
        System.out.println(String.format("  Burnout Mass:           %.4f kg", optimisedBurnoutMass));
        System.out.println(String.format("  Dry Mass:               %.4f kg", optimisedDryMass));
        if (optimisedComponentCG != null && optimisedComponentCG.length > 0) {
            System.out.println("    Component Breakdown:");
            for (String component : optimisedComponentCG) {
                System.out.println(component);
            }
        }
        System.out.println(String.format("  Center of Pressure:     %.4f m  (Barrowman, most forward)", optimisedCenterOfPressure));
        System.out.println(String.format("  CP at 0 deg wind:       %.4f m", optimisedCenterOfPressureZeroAngle));
        double optSMAbsolute = optimisedCenterOfPressure - optimisedCenterOfGravity;
        System.out.println(String.format("  Stability Margin:       %.4f m (%.2f calibres)", 
            optSMAbsolute, optimisedStabilityMarginCalibres));
        System.out.println(String.format("  In-flight Stability:    %.2f to %.2f calibres",
            optimisedMinFlightStabilityCalibres, optimisedMaxFlightStabilityCalibres));
        System.out.println("\n  Flight Performance:");
        System.out.println(String.format("  Apogee:                 %.2f m", optimisedApogee));
        System.out.println(String.format("  Time to Apogee:         %.2f s", optimisedTimeToApogee));
        System.out.println(String.format("  Flight Time:            %.2f s", optimisedFlightTime));
        System.out.println(String.format("  Max Velocity:           %.2f m/s", optimisedMaxVelocity));
        System.out.println(String.format("  Max Acceleration:       %.2f m/s^2", optimisedMaxAcceleration));
        System.out.println(String.format("  Velocity off Rod:       %.2f m/s", optimisedLaunchRodVelocity));
        
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
        
        System.out.println("\n" + "=".repeat(70));
        
        // Verdict
        if (apogeeImprovement > 0) {
            System.out.print("                      OPTIMISATION SUCCESSFUL\n");
        } else {
            System.out.println("                        OPTIMISATION FAILED\n");
        }
        System.out.println("=".repeat(70) + "\n");
    }
}
