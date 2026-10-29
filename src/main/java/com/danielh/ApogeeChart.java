package com.danielh;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Live plot of best apogee against generation.
 *
 * <p>On a machine with a display, {@link #openWindow()} shows a Swing frame on
 * the event-dispatch thread. The search thread is not blocked by later
 * updates. The frame stays open after the run; closing it exits the process.
 * Closing it during the search disposes the window and the search continues.
 *
 * <p>With no display, or when the caller passes {@code false} to
 * {@link #openWindow(boolean)}, the window is skipped and nothing is thrown.
 * {@link #writeBeside(File)} still writes a CSV and a PNG.
 */
public final class ApogeeChart {

    public static final int IMAGE_WIDTH = 800;
    public static final int IMAGE_HEIGHT = 480;

    private final ApogeeSeries series = new ApogeeSeries();
    private final int totalGenerations;
    private JFrame frame;
    private volatile boolean windowOpen;

    public ApogeeChart(int totalGenerations) {
        this.totalGenerations = Math.max(totalGenerations, 1);
    }

    public ApogeeSeries series() {
        return series;
    }

    public static boolean displayAvailable() {
        return !GraphicsEnvironment.isHeadless();
    }

    public void openWindow() {
        openWindow(displayAvailable());
    }

    /**
     * @param display when false, skip the window even if a display exists
     */
    public void openWindow(boolean display) {
        if (!display) {
            windowOpen = false;
            return;
        }
        try {
            SwingUtilities.invokeAndWait(this::createFrame);
        } catch (Exception failure) {
            windowOpen = false;
        }
    }

    public void record(int generation, double bestApogeeMetres) {
        series.add(generation, bestApogeeMetres);
        if (!windowOpen) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (frame != null) {
                frame.repaint();
            }
        });
    }

    public boolean isWindowOpen() {
        return windowOpen;
    }

    /**
     * After the search, leave a still-open window up until the user closes it,
     * and make that close exit the program. Returns true when the caller
     * should not call {@code System.exit}.
     */
    public boolean holdOpenUntilClosed() {
        if (frame == null) {
            return false;
        }
        final boolean[] stay = {false};
        try {
            SwingUtilities.invokeAndWait(() -> {
                if (frame.isDisplayable()) {
                    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
                    stay[0] = true;
                } else {
                    windowOpen = false;
                }
            });
        } catch (Exception failure) {
            windowOpen = false;
            return false;
        }
        windowOpen = stay[0];
        return stay[0];
    }

    public void writeBeside(File rocketFile) throws IOException {
        File directory = rocketFile.getParentFile();
        String name = rocketFile.getName();
        if (name.endsWith(".ork")) {
            name = name.substring(0, name.length() - 4);
        }
        writeCsv(new File(directory, name + ".csv"));
        writePng(new File(directory, name + ".png"));
    }

    public void writeCsv(File file) throws IOException {
        List<ApogeeSeries.Point> points = series.points();
        try (PrintWriter writer = new PrintWriter(file, StandardCharsets.UTF_8)) {
            writer.println("generation,best_apogee_m");
            for (ApogeeSeries.Point point : points) {
                if (Double.isNaN(point.bestApogeeMetres)) {
                    writer.println(point.generation + ",");
                } else {
                    writer.printf("%d,%.4f%n", point.generation, point.bestApogeeMetres);
                }
            }
        }
    }

    public void writePng(File file) throws IOException {
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            paintChart(graphics, IMAGE_WIDTH, IMAGE_HEIGHT, series.points(), totalGenerations);
        } finally {
            graphics.dispose();
        }
        if (!ImageIO.write(image, "png", file)) {
            throw new IOException("PNG writer is not available");
        }
    }

    private void createFrame() {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                paintChart((Graphics2D) graphics, getWidth(), getHeight(), series.points(), totalGenerations);
            }
        };
        frame = new JFrame("Best apogee by generation");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                windowOpen = false;
            }
        });
        frame.setContentPane(panel);
        frame.setSize(IMAGE_WIDTH, IMAGE_HEIGHT);
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
        windowOpen = true;
    }

    static void paintChart(Graphics2D graphics, int width, int height, List<ApogeeSeries.Point> points,
                           int totalGenerations) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, Math.max(width, 1), Math.max(height, 1));
        if (width < 80 || height < 80) {
            return;
        }

        int left = 72;
        int right = 24;
        int top = 40;
        int bottom = 48;
        int plotWidth = width - left - right;
        int plotHeight = height - top - bottom;

        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (ApogeeSeries.Point point : points) {
            if (Double.isNaN(point.bestApogeeMetres)) {
                continue;
            }
            minY = Math.min(minY, point.bestApogeeMetres);
            maxY = Math.max(maxY, point.bestApogeeMetres);
        }
        if (minY == Double.POSITIVE_INFINITY) {
            minY = 0;
            maxY = 3000;
        } else if (maxY - minY < 1) {
            minY -= 50;
            maxY += 50;
        } else {
            double pad = (maxY - minY) * 0.08;
            minY -= pad;
            maxY += pad;
        }

        int generations = Math.max(totalGenerations, 1);
        graphics.setColor(new Color(0xE6E6E6));
        for (int i = 0; i <= 4; i++) {
            int y = top + plotHeight - (plotHeight * i / 4);
            graphics.drawLine(left, y, left + plotWidth, y);
        }

        graphics.setColor(new Color(0x222222));
        graphics.drawLine(left, top, left, top + plotHeight);
        graphics.drawLine(left, top + plotHeight, left + plotWidth, top + plotHeight);

        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        graphics.drawString("Best apogee by generation", left, 24);
        for (int i = 0; i <= 4; i++) {
            double value = minY + (maxY - minY) * i / 4.0;
            int y = top + plotHeight - (plotHeight * i / 4);
            String label = (maxY - minY) < 50 ? String.format("%.1f", value) : String.format("%.0f", value);
            graphics.drawString(label, 8, y + 4);
        }
        graphics.drawString("1", left, top + plotHeight + 18);
        String end = Integer.toString(generations);
        graphics.drawString(end, left + plotWidth - graphics.getFontMetrics().stringWidth(end), top + plotHeight + 18);
        graphics.drawString("Generation", left + plotWidth / 2 - 36, height - 8);

        graphics.setColor(new Color(0x1F4E79));
        graphics.setStroke(new BasicStroke(2f));
        int previousX = Integer.MIN_VALUE;
        int previousY = Integer.MIN_VALUE;
        for (ApogeeSeries.Point point : points) {
            if (Double.isNaN(point.bestApogeeMetres)) {
                continue;
            }
            double xFraction = generations == 1 ? 0.5 : (point.generation - 1) / (double) (generations - 1);
            double yFraction = (point.bestApogeeMetres - minY) / (maxY - minY);
            int x = left + (int) Math.round(xFraction * plotWidth);
            int y = top + plotHeight - (int) Math.round(yFraction * plotHeight);
            if (previousX != Integer.MIN_VALUE) {
                graphics.drawLine(previousX, previousY, x, y);
            }
            previousX = x;
            previousY = y;
        }
    }
}
