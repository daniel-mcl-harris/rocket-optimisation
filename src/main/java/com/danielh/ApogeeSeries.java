package com.danielh;

import java.util.ArrayList;
import java.util.List;

/**
 * Best accepted apogee after each completed generation. Rejected designs do not
 * appear here: the optimiser only records the best finite apogee.
 */
public final class ApogeeSeries {

    public static final class Point {
        public final int generation;
        public final double bestApogeeMetres;

        public Point(int generation, double bestApogeeMetres) {
            this.generation = generation;
            this.bestApogeeMetres = bestApogeeMetres;
        }
    }

    private final List<Point> points = new ArrayList<>();

    public synchronized void add(int generation, double bestApogeeMetres) {
        points.add(new Point(generation, bestApogeeMetres));
    }

    public synchronized List<Point> points() {
        return List.copyOf(points);
    }

    public synchronized int size() {
        return points.size();
    }
}
