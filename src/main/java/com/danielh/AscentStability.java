package com.danielh;

import java.util.ArrayList;
import java.util.List;

import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.simulation.FlightDataBranch;
import info.openrocket.core.simulation.FlightDataType;
import info.openrocket.core.simulation.FlightEvent;

/**
 * Rejects a simulated flight that goes unstable on the way up.
 *
 * <p>The check covers powered and coasting ascent, from launch-rod clearance
 * to apogee, and it is applied to every wind case. Two OpenRocket signals are
 * enough. A {@code TUMBLE} event means the simulator has left six-degree-of-freedom
 * flight because the angle of attack exceeded the fin stall angle while the
 * centre of gravity was ahead of the centre of pressure; on the audited design
 * that event is about 0.2 s before apogee at roughly 3 m/s, and the tumble
 * stepper is why flight time grows from about 50 s to about 200 s. Separately,
 * stability below {@link #MIN_STABILITY_CALIBRES} while faster than
 * {@link #MIN_SPEED_METRES_PER_SECOND} catches a rocket that is unstable off
 * the rod but has not stalled yet. The base rocket has neither signal.
 *
 * <p>Calibres at a few metres per second are not a margin. The audited design's
 * minimum of about -29 cal is the sample OpenRocket writes as it switches to
 * the tumble stepper, at about 3 m/s. Samples slower than the speed cutoff are
 * ignored, and so is the last {@link #NEAR_APOGEE_IGNORE_SECONDS} of an ascent
 * that is longer than that. A flight that never gets that far, such as a
 * tiny-fin rocket that aborts during boost, is judged on the fast samples it
 * does have.
 *
 * <p>The shared warnings "no recovery device" and "supersonic body calculations"
 * are on the base rocket as well, so warning text is not a gate. An aborted
 * simulation is. Failing any wind case rejects the candidate with
 * {@link #REJECTED_FITNESS} rather than a soft penalty: the tumbled design was
 * about 150 m higher, so a penalty smaller than that would still have won.
 */
public final class AscentStability {

    /** Lowest margin that still counts as stable, in calibres. */
    public static final double MIN_STABILITY_CALIBRES = 1.0;

    /**
     * Speed below which a calibre reading is ignored. Rod clearance on the base
     * rocket is about 33 m/s, so 30 m/s keeps the first off-rod samples and drops
     * the near-apogee readings.
     */
    public static final double MIN_SPEED_METRES_PER_SECOND = 30.0;

    /**
     * End of the ascent window on a long flight. The rocket is slowing and the
     * margin stops meaning what it means in the climb.
     */
    public static final double NEAR_APOGEE_IGNORE_SECONDS = 2.0;

    /**
     * Longest excursion below {@link #MIN_STABILITY_CALIBRES} that is still
     * tolerated. One integration step on the audited design was about 4 ms and
     * is inside this. The moderate-wind upset stayed under 1 cal for about
     * 0.10 s from 33 m/s, and the breezy upset for about 0.25 s, so both fail.
     */
    public static final double MAX_LOW_MARGIN_SECONDS = 0.05;

    /**
     * Angle of attack above OpenRocket's 17.5 degree fin stall, and above the
     * base rocket, which stayed under 15 degrees while faster than the speed cutoff.
     */
    public static final double MAX_ANGLE_OF_ATTACK_DEGREES = 20.0;

    /** Longest excursion above {@link #MAX_ANGLE_OF_ATTACK_DEGREES} that is still tolerated. */
    public static final double MAX_HIGH_AOA_SECONDS = 0.05;

    /** One pair of samples cannot contribute more than this, so a logging gap is not a long upset. */
    public static final double MAX_SAMPLE_GAP_SECONDS = 0.5;

    /**
     * Fitness returned for a rejected design. The search minimises, flight
     * fitness is negative apogee, and a failed simulation returns 1000. One is
     * worse than every non-negative apogee and better than a simulation that
     * threw.
     */
    public static final double REJECTED_FITNESS = 1.0;

    private AscentStability() {
    }

    public static final class Result {
        public final boolean rejected;
        /** Empty when {@link #rejected} is false. */
        public final String reason;

        private Result(boolean rejected, String reason) {
            this.rejected = rejected;
            this.reason = reason;
        }
    }

    public static Result pass() {
        return new Result(false, "");
    }

    public static Result assess(FlightData data) {
        if (data == null || data.getBranchCount() == 0) {
            return reject("no flight data");
        }
        List<String> reasons = new ArrayList<>();
        for (int i = 0; i < data.getBranchCount(); i++) {
            String reason = assessBranch(data, data.getBranch(i));
            if (reason != null) {
                reasons.add(reason);
            }
        }
        if (reasons.isEmpty()) {
            return pass();
        }
        return reject(String.join("; ", reasons));
    }

    /**
     * One sentence listing the numeric gates, for the report and the README.
     */
    public static String thresholdSummary() {
        return String.format(
                "Thresholds: tumble or aborted flight; stability below %.1f cal for more than %.2f s "
                        + "while faster than %.0f m/s; angle of attack above %.0f deg for more than %.2f s. "
                        + "Ignores samples slower than %.0f m/s, and the last %.1f s before apogee when the ascent is longer than that.",
                MIN_STABILITY_CALIBRES,
                MAX_LOW_MARGIN_SECONDS,
                MIN_SPEED_METRES_PER_SECOND,
                MAX_ANGLE_OF_ATTACK_DEGREES,
                MAX_HIGH_AOA_SECONDS,
                MIN_SPEED_METRES_PER_SECOND,
                NEAR_APOGEE_IGNORE_SECONDS);
    }

    private static String assessBranch(FlightData data, FlightDataBranch branch) {
        if (branch == null) {
            return "no flight data";
        }
        List<String> reasons = new ArrayList<>();
        FlightEvent tumble = branch.getFirstEvent(FlightEvent.Type.TUMBLE);
        if (tumble != null) {
            reasons.add(String.format("tumble at %.2f s", tumble.getTime()));
        }
        FlightEvent abort = branch.getFirstEvent(FlightEvent.Type.SIM_ABORT);
        if (abort != null) {
            reasons.add(abortText(abort));
        }
        FlightEvent exception = branch.getFirstEvent(FlightEvent.Type.EXCEPTION);
        if (exception != null) {
            reasons.add(String.format("simulation exception at %.2f s", exception.getTime()));
        }

        double rodTime = eventTime(branch, FlightEvent.Type.LAUNCHROD, 0.0);
        double apogeeTime = apogeeTime(data, branch);
        double lowMarginSeconds = 0.0;
        double highAoaSeconds = 0.0;
        if (!Double.isNaN(apogeeTime)) {
            List<Double> time = branch.get(FlightDataType.TYPE_TIME);
            List<Double> stability = branch.get(FlightDataType.TYPE_STABILITY);
            List<Double> velocity = branch.get(FlightDataType.TYPE_VELOCITY_TOTAL);
            List<Double> aoa = branch.get(FlightDataType.TYPE_AOA);
            double ascentLimit = ascentLimit(rodTime, apogeeTime);
            double aoaLimit = Math.toRadians(MAX_ANGLE_OF_ATTACK_DEGREES);
            int length = time == null ? 0 : time.size();
            for (int i = 0; i < length - 1; i++) {
                double sampleTime = value(time, i);
                if (Double.isNaN(sampleTime) || sampleTime < rodTime || sampleTime > ascentLimit) {
                    continue;
                }
                double speed = value(velocity, i);
                if (Double.isNaN(speed) || speed < MIN_SPEED_METRES_PER_SECOND) {
                    continue;
                }
                double dt = sampleSpan(time, i, sampleTime, ascentLimit);
                if (dt <= 0.0) {
                    continue;
                }
                double margin = value(stability, i);
                if (!Double.isNaN(margin) && margin < MIN_STABILITY_CALIBRES) {
                    lowMarginSeconds += dt;
                }
                double angle = value(aoa, i);
                if (!Double.isNaN(angle) && angle > aoaLimit) {
                    highAoaSeconds += dt;
                }
            }
        }
        if (exceeds(lowMarginSeconds, MAX_LOW_MARGIN_SECONDS)) {
            reasons.add(String.format(
                    "stability below %.1f cal for %.2f s while faster than %.0f m/s",
                    MIN_STABILITY_CALIBRES, lowMarginSeconds, MIN_SPEED_METRES_PER_SECOND));
        }
        if (exceeds(highAoaSeconds, MAX_HIGH_AOA_SECONDS)) {
            reasons.add(String.format(
                    "angle of attack above %.0f deg for %.2f s while faster than %.0f m/s",
                    MAX_ANGLE_OF_ATTACK_DEGREES, highAoaSeconds, MIN_SPEED_METRES_PER_SECOND));
        }
        if (reasons.isEmpty()) {
            return null;
        }
        return String.join("; ", reasons);
    }

    /** A fraction of a millisecond past the limit is still the limit. */
    private static boolean exceeds(double seconds, double limit) {
        return seconds > limit + 1e-6;
    }

    private static String abortText(FlightEvent abort) {
        Object detail = abort.getData();
        if (detail == null || detail.toString().isBlank()) {
            return String.format("simulation aborted at %.2f s", abort.getTime());
        }
        return String.format("simulation aborted at %.2f s (%s)", abort.getTime(), detail);
    }

    private static double ascentLimit(double rodTime, double apogeeTime) {
        if (apogeeTime - rodTime > NEAR_APOGEE_IGNORE_SECONDS) {
            return apogeeTime - NEAR_APOGEE_IGNORE_SECONDS;
        }
        return apogeeTime;
    }

    private static double sampleSpan(List<Double> time, int index, double sampleTime, double ascentLimit) {
        double next = value(time, index + 1);
        if (Double.isNaN(next) || next <= sampleTime) {
            return 0.0;
        }
        double end = Math.min(next, ascentLimit);
        return Math.min(end - sampleTime, MAX_SAMPLE_GAP_SECONDS);
    }

    private static double apogeeTime(FlightData data, FlightDataBranch branch) {
        double event = eventTime(branch, FlightEvent.Type.APOGEE, Double.NaN);
        if (!Double.isNaN(event)) {
            return event;
        }
        double reported = data.getTimeToApogee();
        if (!Double.isNaN(reported) && reported > 0.0) {
            return reported;
        }
        List<Double> time = branch.get(FlightDataType.TYPE_TIME);
        List<Double> altitude = branch.get(FlightDataType.TYPE_ALTITUDE);
        if (time == null || altitude == null) {
            return Double.NaN;
        }
        double bestAltitude = Double.NEGATIVE_INFINITY;
        double bestTime = Double.NaN;
        int length = Math.min(time.size(), altitude.size());
        for (int i = 0; i < length; i++) {
            double sampleAltitude = value(altitude, i);
            double sampleTime = value(time, i);
            if (!Double.isNaN(sampleAltitude) && sampleAltitude > bestAltitude) {
                bestAltitude = sampleAltitude;
                bestTime = sampleTime;
            }
        }
        return bestTime;
    }

    private static double eventTime(FlightDataBranch branch, FlightEvent.Type type, double fallback) {
        FlightEvent event = branch.getFirstEvent(type);
        if (event == null || Double.isNaN(event.getTime())) {
            return fallback;
        }
        return event.getTime();
    }

    private static double value(List<Double> values, int index) {
        if (values == null || index < 0 || index >= values.size()) {
            return Double.NaN;
        }
        Double sample = values.get(index);
        if (sample == null) {
            return Double.NaN;
        }
        return sample;
    }

    private static Result reject(String reason) {
        return new Result(true, reason);
    }
}
