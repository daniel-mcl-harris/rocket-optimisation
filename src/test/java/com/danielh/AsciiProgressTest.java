package com.danielh;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.Test;

public class AsciiProgressTest {

    @Test
    public void renderIsPlainAsciiWithBestApogee() {
        AsciiProgress progress = new AsciiProgress(100, new PrintStream(Output.NULL), true);
        String line = progress.render(45, 2553.9);
        assertTrue(line.contains("[#########-----------]"));
        assertTrue(line.contains("45%"));
        assertTrue(line.contains("gen 45/100"));
        assertTrue(line.contains("best 2553.9 m"));
        assertTrue(line.contains("elapsed"));
        assertFalse(line.chars().anyMatch(ch -> ch > 127));
    }

    @Test
    public void interactiveUpdatesStayOnOneLineUntilFinished() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        AsciiProgress progress = new AsciiProgress(100, new PrintStream(buffer, true), true);
        progress.update(45, 2553.9);
        progress.update(10, 1000.0);
        String mid = buffer.toString();
        assertTrue(mid.contains("\r"));
        assertFalse(mid.contains("\n"));
        assertTrue(mid.endsWith(" ") || mid.contains("gen 10/100"));
        progress.finish(100, 2600.0);
        String end = buffer.toString();
        assertTrue(end.contains("\n"));
        assertTrue(end.contains("[####################]"));
        assertTrue(end.contains("100%"));
        assertTrue(end.contains("gen 100/100"));
    }

    @Test
    public void pipedOutputPrintsMilestonesInsteadOfCarriageReturns() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        AsciiProgress progress = new AsciiProgress(100, new PrintStream(buffer, true), false);
        for (int generation = 1; generation <= 100; generation++) {
            progress.update(generation, 2000.0);
        }
        progress.finish(100, 2000.0);
        String text = buffer.toString();
        assertFalse(text.contains("\r"));
        long lines = text.lines().filter(line -> !line.isBlank()).count();
        assertTrue("milestone lines were " + lines, lines <= 12);
        assertTrue(text.contains("gen 100/100"));
    }

    private static final class Output {
        static final PrintStream NULL = new PrintStream(java.io.OutputStream.nullOutputStream());
    }
}
