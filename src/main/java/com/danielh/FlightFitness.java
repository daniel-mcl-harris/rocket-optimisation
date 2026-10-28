package com.danielh;

import java.util.ArrayList;
import java.util.List;

/**
 * Fitness is the mean apogee across the wind cases. A higher value is better.
 * Stability is not part of the score.
 */
public final class FlightFitness {

    /** Mean-apogee differences inside this band are the same result. */
    public static final double NOISE_METRES = 1.0;

    public static final double LOW_MARGIN_CALIBRES = 1.0;
    public static final double HIGH_MARGIN_CALIBRES = 6.0;

    private FlightFitness() {
    }

    public static String verdict(double baselineMeanApogee, double optimisedMeanApogee) {
        double improvement = optimisedMeanApogee - baselineMeanApogee;
        if (improvement > NOISE_METRES) {
            return "OPTIMISATION SUCCESSFUL";
        }
        if (improvement >= -NOISE_METRES) {
            return "NO SIGNIFICANT CHANGE";
        }
        return "OPTIMISATION FAILED";
    }

    /**
     * Notes for the final design when the static margin, the worst in-flight
     * margin, or the highest in-flight margin sits outside about 1 to 6 calibres.
     * These lines do not change the score.
     */
    public static List<String> stabilityWarnings(double staticCalibres, double minimumInFlightCalibres,
                                                 double maximumInFlightCalibres) {
        List<String> warnings = new ArrayList<>();
        addBound(warnings, "Static margin", staticCalibres);
        addBound(warnings, "Worst in-flight margin", minimumInFlightCalibres);
        if (!Double.isNaN(maximumInFlightCalibres) && maximumInFlightCalibres > HIGH_MARGIN_CALIBRES) {
            warnings.add(String.format("In-flight stability reached %.2f cal, above %.0f cal.",
                    maximumInFlightCalibres, HIGH_MARGIN_CALIBRES));
        }
        return warnings;
    }

    private static void addBound(List<String> warnings, String label, double calibres) {
        if (Double.isNaN(calibres)) {
            return;
        }
        if (calibres < LOW_MARGIN_CALIBRES) {
            warnings.add(String.format("%s is %.2f cal, below %.0f cal.",
                    label, calibres, LOW_MARGIN_CALIBRES));
        } else if (calibres > HIGH_MARGIN_CALIBRES) {
            warnings.add(String.format("%s is %.2f cal, above %.0f cal.",
                    label, calibres, HIGH_MARGIN_CALIBRES));
        }
    }
}
