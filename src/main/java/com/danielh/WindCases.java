package com.danielh;

import java.util.List;

/**
 * Wind conditions flown for every candidate.
 *
 * <p>The three averages are typical hobby and high-power launch days, not a
 * storm. Speeds are metres per second. Turbulence intensity is OpenRocket's
 * standard deviation divided by the average (below 0.05 is "very low", 0.10 is
 * "low", 0.15 is "medium", 0.20 is "high"). Each case has a fixed pink-noise
 * seed so every candidate sees the same gusts.
 */
public final class WindCases {

    public static final class Case {
        public final String name;
        public final double averageMetresPerSecond;
        public final double turbulenceIntensity;
        public final int seed;

        public Case(String name, double averageMetresPerSecond, double turbulenceIntensity, int seed) {
            this.name = name;
            this.averageMetresPerSecond = averageMetresPerSecond;
            this.turbulenceIntensity = turbulenceIntensity;
            this.seed = seed;
        }
    }

    /**
     * Calm is a light breeze (about 2 knots) with low turbulence.
     * Moderate is a common club afternoon (about 8 knots) with medium turbulence.
     * Breezy is about 14 knots with high turbulence, near the top of what many
     * clubs will still fly, where weathercocking off the rod shows up.
     */
    public static final List<Case> CASES = List.of(
            new Case("calm", 1.0, 0.08, 24011),
            new Case("moderate", 4.0, 0.12, 24012),
            new Case("breezy", 7.0, 0.18, 24013)
    );

    private WindCases() {
    }

    public static String summary() {
        StringBuilder text = new StringBuilder();
        for (Case wind : CASES) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(String.format("%s %.0f m/s", wind.name, wind.averageMetresPerSecond));
        }
        return text.toString();
    }
}
