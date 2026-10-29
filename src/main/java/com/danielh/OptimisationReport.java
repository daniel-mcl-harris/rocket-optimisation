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
    public double baselineDescentRate;
    public double baselineDeploymentSpeed;
    public double baselineMaxMach;
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
    public double optimisedDescentRate;
    public double optimisedDeploymentSpeed;
    public double optimisedMaxMach;
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

    public String motorDescription;
    public String[] baselineWindCases;
    public String[] optimisedWindCases;
    
    // Component CG breakdowns
    public String[] baselineComponentCG;
    public String[] optimisedComponentCG;
    
    public long executionTimeMs;
    /** GA evaluations that {@link AscentStability} rejected. The baseline flight is not included. */
    public int rejectedCandidateEvaluations;
    public boolean baselineRejected;
    public String baselineRejectionReason = "";
    public boolean optimisedRejected;
    public String optimisedRejectionReason = "";
    
    // Store the best phenotype for potential use
    public Phenotype<DoubleGene, Double> bestPhenotype;
    /** Null when the run was started with {@code --no-plot}. */
    public ApogeeChart apogeeChart;
    
    public OptimisationReport(int popSize, int gens) {
        this.populationSize = popSize;
        this.generations = gens;
    }
    
    public void generateReport() {
        String border = "=".repeat(72);
        System.out.println("\n" + border);
        System.out.println(center("ROCKET OPTIMISATION REPORT", 72));
        System.out.println(border);
        long totalSeconds = executionTimeMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        System.out.println(String.format("Population %d   Generations %d   Time %02d:%02d:%02d",
                populationSize, generations, hours, minutes, seconds));
        if (motorDescription != null) {
            System.out.println("Motor: " + motorDescription);
        }
        System.out.println("Wind:  " + WindCases.summary() + " (means below)");

        System.out.println("\nDESIGN");
        printTableHeader();
        designRow("Nose cone length (m)", baselineNoseCone, optimisedNoseCone, 4);
        designRow("Body tube length (m)", baselineBodyTube, optimisedBodyTube, 4);
        designRow("Fin root chord (m)", baselineFinRootChord, optimisedFinRootChord, 4);
        designRow("Fin tip chord (m)", baselineFinTipChord, optimisedFinTipChord, 4);
        designRow("Fin height (m)", baselineFinHeight, optimisedFinHeight, 4);
        designRow("Fin sweep length (m)", baselineFinSweepLength, optimisedFinSweepLength, 4);
        designRow("CP (m)", baselineCenterOfPressure, optimisedCenterOfPressure, 4);
        designRow("Launch CG (m)", baselineCenterOfGravity, optimisedCenterOfGravity, 4);
        designRow("Launch mass (kg)", baselineLaunchMass, optimisedLaunchMass, 4);
        designRow("Static margin (cal)", baselineStabilityMarginCalibres, optimisedStabilityMarginCalibres, 2);

        System.out.println("\nFLIGHT (mean of the 3 wind cases; fitness is mean apogee)");
        printTableHeader();
        designRow("Mean apogee (m)", baselineApogee, optimisedApogee, 2);
        designRow("Time to apogee (s)", baselineTimeToApogee, optimisedTimeToApogee, 2);
        designRow("Flight time (s)", baselineFlightTime, optimisedFlightTime, 2);
        designRow("Max velocity (m/s)", baselineMaxVelocity, optimisedMaxVelocity, 2);
        designRow("Max acceleration (m/s^2)", baselineMaxAcceleration, optimisedMaxAcceleration, 2);
        designRow("Max Mach", baselineMaxMach, optimisedMaxMach, 3);
        designRow("Velocity off rod (m/s)", baselineLaunchRodVelocity, optimisedLaunchRodVelocity, 2);
        designRow("Descent rate (m/s)", baselineDescentRate, optimisedDescentRate, 2);
        designRow("Deployment speed (m/s)", baselineDeploymentSpeed, optimisedDeploymentSpeed, 2);
        designRow("In-flight min (cal)", baselineMinFlightStabilityCalibres, optimisedMinFlightStabilityCalibres, 2);
        designRow("In-flight max (cal)", baselineMaxFlightStabilityCalibres, optimisedMaxFlightStabilityCalibres, 2);

        java.util.List<String> stabilityWarnings = FlightFitness.stabilityWarnings(
                optimisedStabilityMarginCalibres,
                optimisedMinFlightStabilityCalibres,
                optimisedMaxFlightStabilityCalibres);
        if (!stabilityWarnings.isEmpty()) {
            System.out.println("\nWARNING: stability is outside 1 to 6 calibres. It is not part of the score.");
            for (String warning : stabilityWarnings) {
                System.out.println("  " + warning);
            }
        }

        System.out.println("\nIn-flight instability rejected " + rejectedCandidateEvaluations
                + " candidate evaluations.");
        System.out.println(AscentStability.thresholdSummary());
        System.out.println(ascentLine("Base design", baselineRejected, baselineRejectionReason));
        System.out.println(ascentLine("Optimised design", optimisedRejected, optimisedRejectionReason));

        System.out.println("\n" + border);
        String verdict = FlightFitness.verdict(baselineApogee, optimisedApogee);
        System.out.println(center(verdict, 72));
        System.out.println(border + "\n");
    }

    private static String ascentLine(String design, boolean rejected, String reason) {
        if (!rejected) {
            return design + " passed the in-flight check.";
        }
        String detail = reason == null || reason.isBlank() ? "unstable ascent" : reason;
        return design + " was rejected for in-flight instability: " + detail;
    }

    private static void printTableHeader() {
        System.out.println(row("Parameter", "Base", "Optimised", "Change"));
        System.out.println(row("---------", "----", "---------", "------"));
    }

    private static void designRow(String name, double base, double optimised, int decimals) {
        System.out.println(row(name, formatNumber(base, decimals), formatNumber(optimised, decimals),
                formatChange(base, optimised, decimals)));
    }

    private static String formatNumber(double value, int decimals) {
        if (Double.isNaN(value)) {
            return "n/a";
        }
        return String.format("%." + decimals + "f", value);
    }

    private static String formatChange(double base, double optimised, int decimals) {
        if (Double.isNaN(base) || Double.isNaN(optimised)) {
            return "n/a";
        }
        double change = optimised - base;
        String text = String.format("%." + decimals + "f", Math.abs(change));
        if (change > 0) {
            return "+" + text;
        }
        if (change < 0) {
            return "-" + text;
        }
        return text;
    }

    private static String row(String parameter, String base, String optimised, String change) {
        return pad(parameter, 26) + pad(base, 12) + pad(optimised, 12) + change;
    }

    private static String pad(String text, int width) {
        if (text.length() >= width) {
            return text + " ";
        }
        return text + " ".repeat(width - text.length());
    }

    private static String center(String text, int width) {
        if (text.length() >= width) {
            return text;
        }
        int left = (width - text.length()) / 2;
        return " ".repeat(left) + text;
    }
}
