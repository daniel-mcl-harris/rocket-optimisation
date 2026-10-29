package com.danielh;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import info.openrocket.core.motor.Motor;
import info.openrocket.core.motor.MotorConfiguration;
import info.openrocket.core.motor.ThrustCurveMotor;
import info.openrocket.core.rocketcomponent.FlightConfigurationId;
import info.openrocket.core.rocketcomponent.MotorMount;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.startup.Application;

/**
 * Looks up a command-line motor name in the OpenRocket thrust-curve database
 * and mounts it on the rocket, replacing whatever the file had loaded.
 */
public final class MotorCatalogue {

    private static final Pattern WITH_DELAY = Pattern.compile(
            "^(?<body>.+)-(?<delay>\\d+(?:\\.\\d+)?|[Pp]|plugged)$",
            Pattern.CASE_INSENSITIVE);

    public static final class Selection {
        public final ThrustCurveMotor motor;
        public final double ejectionDelaySeconds;
        public final String requestedName;

        Selection(ThrustCurveMotor motor, double ejectionDelaySeconds, String requestedName) {
            this.motor = motor;
            this.ejectionDelaySeconds = ejectionDelaySeconds;
            this.requestedName = requestedName;
        }

        public String describe() {
            return motor.getManufacturer().getSimpleName() + " " + motor.getDesignation(ejectionDelaySeconds);
        }
    }

    public static class MotorLookupException extends IllegalArgumentException {
        public MotorLookupException(String message) {
            super(message);
        }
    }

    private MotorCatalogue() {
    }

    public static Selection select(String requested) {
        if (requested == null || requested.isBlank()) {
            throw new MotorLookupException("Motor name is missing. Example: I350R or AeroTech/I350R-P");
        }
        Parsed parsed = parse(requested.trim());
        List<ThrustCurveMotor> matches = exactMatches(parsed.designation, parsed.manufacturer);
        if (matches.isEmpty() && parsed.delaySpecified) {
            List<ThrustCurveMotor> asWholeName = exactMatches(parsed.spec, parsed.manufacturer);
            if (!asWholeName.isEmpty()) {
                matches = asWholeName;
                parsed = parsed.withoutDelay();
            }
        }
        if (matches.isEmpty()) {
            throw new MotorLookupException(notFound(requested, parsed.designation));
        }

        if (parsed.delaySpecified) {
            List<ThrustCurveMotor> withDelay = filterDelay(matches, parsed.delay);
            if (withDelay.isEmpty()) {
                throw new MotorLookupException(unlistedDelay(requested, matches));
            }
            matches = withDelay;
        }

        Map<String, List<ThrustCurveMotor>> groups = group(matches);
        if (groups.size() > 1) {
            throw new MotorLookupException(ambiguous(requested, groups));
        }

        ThrustCurveMotor motor = heaviest(matches);
        double delay = parsed.delaySpecified ? canonicalDelay(motor, parsed.delay) : defaultDelay(motor);
        return new Selection(motor, delay, requested.trim());
    }

    /**
     * Replace the motor in the acting mount for the selected flight configuration.
     * A mount that already holds a motor is preferred. A diameter that exceeds the
     * mount is reported and still installed.
     */
    public static void mount(Rocket rocket, Selection selection) {
        List<MotorMount> mounts = new ArrayList<>();
        collectMounts(rocket, mounts);
        if (mounts.isEmpty()) {
            throw new MotorLookupException("The rocket has no motor mount, so " + selection.describe() + " cannot be installed.");
        }
        MotorMount mount = mounts.get(0);
        for (MotorMount candidate : mounts) {
            if (candidate.hasMotor()) {
                mount = candidate;
                break;
            }
        }

        double mountDiameter = mount.getMotorMountDiameter();
        if (selection.motor.getDiameter() > mountDiameter + 0.002) {
            System.err.printf("Warning: %s is %.1f mm in diameter and the mount inner diameter is %.1f mm.%n",
                    selection.describe(), selection.motor.getDiameter() * 1000.0, mountDiameter * 1000.0);
        }

        FlightConfigurationId id = rocket.getSelectedConfiguration().getFlightConfigurationID();
        MotorConfiguration configuration = mount.getMotorConfig(id);
        if (configuration == null) {
            configuration = new MotorConfiguration(mount, id);
            mount.setMotorConfig(configuration, id);
        }
        configuration.setMotor(selection.motor);
        configuration.setEjectionDelay(selection.ejectionDelaySeconds);
        rocket.getSelectedConfiguration().update();
    }

    private static void collectMounts(RocketComponent component, List<MotorMount> mounts) {
        if (component instanceof MotorMount mount && mount.isMotorMount()) {
            mounts.add(mount);
        }
        for (RocketComponent child : component.getChildren()) {
            collectMounts(child, mounts);
        }
    }

    private static List<ThrustCurveMotor> exactMatches(String designation, String manufacturer) {
        List<ThrustCurveMotor> matches = new ArrayList<>();
        for (Motor motor : Application.getMotorSetDatabase().findMotors(null, null, null, designation, Double.NaN, Double.NaN)) {
            if (!(motor instanceof ThrustCurveMotor curve)) {
                continue;
            }
            boolean name = curve.getDesignation().equalsIgnoreCase(designation)
                    || curve.getCommonName().equalsIgnoreCase(designation);
            if (!name) {
                continue;
            }
            if (manufacturer != null && !curve.getManufacturer().matches(manufacturer)) {
                continue;
            }
            matches.add(curve);
        }
        return matches;
    }

    private static List<ThrustCurveMotor> filterDelay(List<ThrustCurveMotor> motors, double delay) {
        List<ThrustCurveMotor> matched = new ArrayList<>();
        for (ThrustCurveMotor motor : motors) {
            if (hasDelay(motor, delay)) {
                matched.add(motor);
            }
        }
        return matched;
    }

    private static boolean hasDelay(ThrustCurveMotor motor, double delay) {
        for (double candidate : motor.getStandardDelays()) {
            if (sameDelay(candidate, delay)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameDelay(double left, double right) {
        if (left == Motor.PLUGGED_DELAY || right == Motor.PLUGGED_DELAY) {
            return left == right;
        }
        return Math.abs(left - right) <= 0.05;
    }

    private static double defaultDelay(ThrustCurveMotor motor) {
        double smallest = Double.NaN;
        boolean plugged = false;
        for (double delay : motor.getStandardDelays()) {
            if (delay == Motor.PLUGGED_DELAY) {
                plugged = true;
            } else if (Double.isNaN(smallest) || delay < smallest) {
                smallest = delay;
            }
        }
        if (!Double.isNaN(smallest)) {
            return smallest;
        }
        if (plugged) {
            return Motor.PLUGGED_DELAY;
        }
        return 0.0;
    }

    private static double canonicalDelay(ThrustCurveMotor motor, double requested) {
        for (double listed : motor.getStandardDelays()) {
            if (sameDelay(listed, requested)) {
                return listed;
            }
        }
        return requested;
    }

    private static String unlistedDelay(String requested, List<ThrustCurveMotor> motors) {
        StringBuilder message = new StringBuilder();
        message.append('\'').append(requested).append("' is not a listed delay.\n");
        for (List<ThrustCurveMotor> group : group(motors).values()) {
            ThrustCurveMotor motor = heaviest(group);
            message.append(motor.getManufacturer().getSimpleName()).append(' ')
                    .append(motor.getDesignation())
                    .append(" delays: ").append(delayList(motor)).append('\n');
            message.append("Valid names: ").append(validNames(motor)).append('\n');
        }
        return message.toString().stripTrailing();
    }

    private static String validNames(ThrustCurveMotor motor) {
        StringBuilder text = new StringBuilder();
        for (double delay : motor.getStandardDelays()) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(motor.getDesignation()).append('-').append(ThrustCurveMotor.getDelayString(delay));
        }
        if (text.length() > 0) {
            text.append(", ");
        }
        text.append(motor.getDesignation());
        return text.toString();
    }

    private static Map<String, List<ThrustCurveMotor>> group(List<ThrustCurveMotor> motors) {
        Map<String, List<ThrustCurveMotor>> groups = new LinkedHashMap<>();
        for (ThrustCurveMotor motor : motors) {
            String key = motor.getManufacturer().getSimpleName().toLowerCase(Locale.ROOT)
                    + "|" + motor.getDesignation().toUpperCase(Locale.ROOT)
                    + "|" + Math.round(motor.getDiameter() * 1000.0);
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(motor);
        }
        return groups;
    }

    private static ThrustCurveMotor heaviest(List<ThrustCurveMotor> motors) {
        return motors.stream()
                .max(Comparator.comparingDouble(ThrustCurveMotor::getLaunchMass)
                        .thenComparing(ThrustCurveMotor::getDigest))
                .orElseThrow();
    }

    private static String notFound(String requested, String designation) {
        StringBuilder message = new StringBuilder();
        message.append("No motor matches '").append(requested).append("'.\n");
        message.append("Use a designation such as I350R, an optional listed delay (H128W-6 or I350R-P),\n");
        message.append("and an optional manufacturer (AeroTech/I350R).\n");
        message.append("Close matches:\n");
        List<String> close = closeMatches(designation);
        if (close.isEmpty()) {
            message.append("  none\n");
        } else {
            for (String line : close) {
                message.append("  ").append(line).append('\n');
            }
        }
        return message.toString().stripTrailing();
    }

    private static String ambiguous(String requested, Map<String, List<ThrustCurveMotor>> groups) {
        StringBuilder message = new StringBuilder();
        message.append('\'').append(requested).append("' matches more than one motor. ");
        message.append("Add the manufacturer, for example 'AeroTech/I350R'.\n");
        for (String line : describeGroups(groups)) {
            message.append("  ").append(line).append('\n');
        }
        return message.toString().stripTrailing();
    }

    private static List<String> describeGroups(Map<String, List<ThrustCurveMotor>> groups) {
        List<String> lines = new ArrayList<>();
        for (List<ThrustCurveMotor> group : groups.values()) {
            ThrustCurveMotor motor = heaviest(group);
            lines.add(motor.getManufacturer().getSimpleName() + " " + motor.getDesignation()
                    + " (" + Math.round(motor.getDiameter() * 1000.0) + " mm, delays: " + delayList(motor) + ")");
        }
        lines.sort(String::compareToIgnoreCase);
        return lines;
    }

    private static String delayList(ThrustCurveMotor motor) {
        StringBuilder text = new StringBuilder();
        for (double delay : motor.getStandardDelays()) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(ThrustCurveMotor.getDelayString(delay));
        }
        if (text.length() == 0) {
            return "none";
        }
        return text.toString();
    }

    private static List<String> closeMatches(String designation) {
        String query = designation.toUpperCase(Locale.ROOT);
        List<Scored> scored = new ArrayList<>();
        for (Motor motor : Application.getMotorSetDatabase().findMotors(null, null, null, null, Double.NaN, Double.NaN)) {
            if (!(motor instanceof ThrustCurveMotor curve)) {
                continue;
            }
            int distance = Math.min(
                    editDistance(query, curve.getDesignation().toUpperCase(Locale.ROOT)),
                    editDistance(query, curve.getCommonName().toUpperCase(Locale.ROOT)));
            if (distance <= 2 || curve.getDesignation().toUpperCase(Locale.ROOT).contains(query)) {
                scored.add(new Scored(distance, curve));
            }
        }
        scored.sort(Comparator.comparingInt((Scored item) -> item.distance)
                .thenComparing(item -> item.motor.getDesignation()));
        List<String> lines = new ArrayList<>();
        for (Scored item : scored) {
            String line = item.motor.getManufacturer().getSimpleName() + " " + item.motor.getDesignation()
                    + " (delays: " + delayList(item.motor) + ")";
            if (!lines.contains(line)) {
                lines.add(line);
            }
            if (lines.size() == 8) {
                break;
            }
        }
        return lines;
    }

    private static int editDistance(String left, String right) {
        if (Math.abs(left.length() - right.length()) > 3) {
            return 99;
        }
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private static Parsed parse(String text) {
        String manufacturer = null;
        String spec = text;
        int slash = text.lastIndexOf('/');
        if (slash > 0) {
            manufacturer = text.substring(0, slash).trim();
            spec = text.substring(slash + 1).trim();
        } else {
            int space = text.lastIndexOf(' ');
            if (space > 0) {
                manufacturer = text.substring(0, space).trim();
                spec = text.substring(space + 1).trim();
            }
        }
        Matcher matcher = WITH_DELAY.matcher(spec);
        if (matcher.matches()) {
            return new Parsed(manufacturer, spec, matcher.group("body"), parseDelay(matcher.group("delay")), true);
        }
        return new Parsed(manufacturer, spec, spec, Double.NaN, false);
    }

    private static double parseDelay(String token) {
        if (token.equalsIgnoreCase("P") || token.equalsIgnoreCase("plugged")) {
            return Motor.PLUGGED_DELAY;
        }
        return Double.parseDouble(token);
    }

    private record Parsed(String manufacturer, String spec, String designation, double delay, boolean delaySpecified) {
        Parsed withoutDelay() {
            return new Parsed(manufacturer, spec, spec, Double.NaN, false);
        }
    }

    private static final class Scored {
        final int distance;
        final ThrustCurveMotor motor;

        Scored(int distance, ThrustCurveMotor motor) {
            this.distance = distance;
            this.motor = motor;
        }
    }
}
