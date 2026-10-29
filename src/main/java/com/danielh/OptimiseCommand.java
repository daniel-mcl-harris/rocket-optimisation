package com.danielh;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses {@code optimise} arguments. Population and generations are optional
 * and independent: a missing one keeps its default.
 */
public final class OptimiseCommand {

    public static final String DEFAULT_MOTOR = "I350R";
    public static final int DEFAULT_POPULATION = 50;
    public static final int DEFAULT_GENERATIONS = 100;

    public static final class Args {
        public final String motor;
        public final int population;
        public final int generations;
        /** When false, skip the live chart and do not write the CSV or PNG. */
        public final boolean plot;

        Args(String motor, int population, int generations, boolean plot) {
            this.motor = motor;
            this.population = population;
            this.generations = generations;
            this.plot = plot;
        }
    }

    public static class UsageException extends IllegalArgumentException {
        public UsageException(String message) {
            super(message);
        }
    }

    private OptimiseCommand() {
    }

    public static String usage() {
        return String.join("\n",
                "Usage: optimise [motor] [population] [generations] [--no-plot]",
                "       optimise [motor] [--pop <population>] [--gens <generations>] [--no-plot]",
                "Valid commands: optimise",
                "Defaults: motor " + DEFAULT_MOTOR + ", population " + DEFAULT_POPULATION
                        + ", generations " + DEFAULT_GENERATIONS + ".",
                "population and generations must be positive integers.",
                "A single number after the motor sets the population. Use --gens to set only the generation count.",
                "--no-plot skips the apogee chart window and does not write the CSV or PNG.");
    }

    public static Args parse(String[] args) {
        if (args == null || args.length == 0 || !args[0].equalsIgnoreCase("optimise")) {
            throw new UsageException(usage());
        }

        String motor = null;
        Integer population = null;
        Integer generations = null;
        boolean plot = true;
        List<String> positionals = new ArrayList<>();

        for (int i = 1; i < args.length; i++) {
            String token = args[i];
            if ("--no-plot".equals(token)) {
                if (!plot) {
                    throw new UsageException("plot was given more than once.\n" + usage());
                }
                plot = false;
            } else if ("--pop".equals(token) || "--gens".equals(token)) {
                if (i + 1 >= args.length) {
                    throw new UsageException(usage());
                }
                int value = positiveInteger(args[++i]);
                if ("--pop".equals(token)) {
                    population = assignOnce(population, value, "population");
                } else {
                    generations = assignOnce(generations, value, "generations");
                }
            } else if (token.startsWith("--pop=") || token.startsWith("--gens=")) {
                int split = token.indexOf('=');
                String name = token.substring(0, split);
                int value = positiveInteger(token.substring(split + 1));
                if ("--pop".equals(name)) {
                    population = assignOnce(population, value, "population");
                } else {
                    generations = assignOnce(generations, value, "generations");
                }
            } else if (token.startsWith("-")) {
                throw new UsageException(usage());
            } else {
                positionals.add(token);
            }
        }

        if (positionals.size() > 3) {
            throw new UsageException(usage());
        }
        if (!positionals.isEmpty()) {
            motor = positionals.get(0);
        }
        if (positionals.size() >= 2) {
            population = assignOnce(population, positiveInteger(positionals.get(1)), "population");
        }
        if (positionals.size() == 3) {
            generations = assignOnce(generations, positiveInteger(positionals.get(2)), "generations");
        }

        return new Args(
                motor == null ? DEFAULT_MOTOR : motor,
                population == null ? DEFAULT_POPULATION : population,
                generations == null ? DEFAULT_GENERATIONS : generations,
                plot);
    }

    private static int assignOnce(Integer current, int value, String name) {
        if (current != null) {
            throw new UsageException(name + " was given more than once.\n" + usage());
        }
        return value;
    }

    private static int positiveInteger(String text) {
        try {
            int value = Integer.parseInt(text);
            if (value <= 0) {
                throw new UsageException(usage());
            }
            return value;
        } catch (NumberFormatException e) {
            throw new UsageException(usage());
        }
    }
}
