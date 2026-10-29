package com.danielh;

import java.io.PrintStream;

/**
 * A one-line ASCII progress bar. Interactive terminals are updated with a
 * carriage return. When standard output is not a terminal, milestones are
 * printed as ordinary lines.
 */
public final class AsciiProgress {

    static final int BAR_WIDTH = 20;

    private final int total;
    private final PrintStream out;
    private final boolean interactive;
    private final long startNanos;
    private int drawnLength;
    private int lastMilestone = -1;
    private boolean finished;

    public AsciiProgress(int total, PrintStream out, boolean interactive) {
        this.total = Math.max(total, 1);
        this.out = out;
        this.interactive = interactive;
        this.startNanos = System.nanoTime();
    }

    public static boolean standardOutputIsTerminal() {
        return System.console() != null;
    }

    public String render(int completed, double bestApogeeMetres) {
        int done = Math.max(0, Math.min(completed, total));
        int percent = (int) Math.round(100.0 * done / total);
        int filled = (int) Math.round(BAR_WIDTH * (percent / 100.0));
        String bar = "#".repeat(filled) + "-".repeat(BAR_WIDTH - filled);
        String best = Double.isNaN(bestApogeeMetres) ? "n/a" : String.format("%.1f m", bestApogeeMetres);
        return String.format("[%s] %3d%% gen %d/%d best %s  %s",
                bar, percent, done, total, best, timing(done));
    }

    public void update(int completed, double bestApogeeMetres) {
        if (finished) {
            return;
        }
        String line = render(completed, bestApogeeMetres);
        if (interactive) {
            int pad = Math.max(0, drawnLength - line.length());
            out.print('\r');
            out.print(line);
            if (pad > 0) {
                out.print(" ".repeat(pad));
            }
            out.flush();
            drawnLength = line.length();
            return;
        }
        int percent = percent(completed);
        int milestone = percent / 10;
        if (completed > 0 && completed < total && milestone == lastMilestone) {
            return;
        }
        lastMilestone = milestone;
        out.println(line);
    }

    public void finish(int completed, double bestApogeeMetres) {
        if (finished) {
            return;
        }
        finished = true;
        String line = render(completed, bestApogeeMetres);
        if (interactive) {
            int pad = Math.max(0, drawnLength - line.length());
            out.print('\r');
            out.print(line);
            if (pad > 0) {
                out.print(" ".repeat(pad));
            }
            out.println();
            out.flush();
            return;
        }
        out.println(line);
    }

    private int percent(int completed) {
        int done = Math.max(0, Math.min(completed, total));
        return (int) Math.round(100.0 * done / total);
    }

    private String timing(int completed) {
        long elapsedNanos = System.nanoTime() - startNanos;
        long elapsedSeconds = elapsedNanos / 1_000_000_000L;
        String elapsed = clock(elapsedSeconds);
        if (completed <= 0 || completed >= total) {
            return "elapsed " + elapsed;
        }
        long etaSeconds = elapsedSeconds * (total - completed) / completed;
        return "elapsed " + elapsed + " eta " + clock(etaSeconds);
    }

    private static String clock(long seconds) {
        long safe = Math.max(0, seconds);
        long hours = safe / 3600;
        long minutes = (safe % 3600) / 60;
        long secs = safe % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format("%d:%02d", minutes, secs);
    }
}
