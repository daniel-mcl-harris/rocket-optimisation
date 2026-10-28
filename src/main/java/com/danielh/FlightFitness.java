package com.danielh;

/**
 * The score the genetic algorithm minimises the negation of: mean apogee
 * after the stability penalty. A higher score is a better design.
 */
public final class FlightFitness {

    /** Scores within this many metres are the same result. */
    public static final double NOISE_METRES = 1.0;

    public static final double STABILITY_TARGET_CALIBRES = 2.0;

    private FlightFitness() {
    }

    /**
     * The margin the penalty uses: the smaller of the static Barrowman margin
     * and the worst in-flight margin. A missing value is ignored; if both are
     * missing the result is NaN and the score becomes zero.
     */
    public static double governingMargin(double staticCalibres, double minimumInFlightCalibres) {
        boolean staticMissing = Double.isNaN(staticCalibres);
        boolean flightMissing = Double.isNaN(minimumInFlightCalibres);
        if (staticMissing && flightMissing) {
            return Double.NaN;
        }
        if (flightMissing) {
            return staticCalibres;
        }
        if (staticMissing) {
            return minimumInFlightCalibres;
        }
        return Math.min(staticCalibres, minimumInFlightCalibres);
    }

    /**
     * Fraction of apogee removed, from 0 at the stability target up to 1 when
     * the margin is zero or negative.
     */
    public static double penalty(double governingMarginCalibres) {
        if (Double.isNaN(governingMarginCalibres)) {
            return 1.0;
        }
        if (governingMarginCalibres >= STABILITY_TARGET_CALIBRES) {
            return 0.0;
        }
        if (governingMarginCalibres > 0.0) {
            double marginBelowTarget = STABILITY_TARGET_CALIBRES - governingMarginCalibres;
            return 1.0 - Math.exp(-marginBelowTarget);
        }
        return 1.0;
    }

    public static double score(double meanApogeeMetres, double governingMarginCalibres) {
        if (Double.isNaN(meanApogeeMetres)) {
            return 0.0;
        }
        return meanApogeeMetres * (1.0 - penalty(governingMarginCalibres));
    }

    public static String verdict(double baselineScore, double optimisedScore) {
        double improvement = optimisedScore - baselineScore;
        if (improvement > NOISE_METRES) {
            return "OPTIMISATION SUCCESSFUL";
        }
        if (improvement >= -NOISE_METRES) {
            return "NO SIGNIFICANT CHANGE";
        }
        return "OPTIMISATION FAILED";
    }
}
