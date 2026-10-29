package com.danielh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.Test;

public class ApogeeChartTest {

    @Test
    public void seriesUpdatesAsGenerationsComplete() {
        ApogeeChart chart = new ApogeeChart(5);
        chart.record(1, 2488.8);
        chart.record(2, Double.NaN);
        chart.record(3, 2641.1);

        assertEquals(3, chart.series().size());
        assertEquals(1, chart.series().points().get(0).generation);
        assertEquals(2488.8, chart.series().points().get(0).bestApogeeMetres, 1e-9);
        assertTrue(Double.isNaN(chart.series().points().get(1).bestApogeeMetres));
        assertEquals(2641.1, chart.series().points().get(2).bestApogeeMetres, 1e-9);
    }

    @Test
    public void headlessFallbackDoesNotThrowAndStillWritesFiles() throws Exception {
        ApogeeChart chart = new ApogeeChart(4);
        chart.openWindow(false);
        assertFalse(chart.isWindowOpen());
        if (!ApogeeChart.displayAvailable()) {
            chart.openWindow();
            assertFalse(chart.isWindowOpen());
        }

        chart.record(1, 1000.0);
        chart.record(2, 1100.5);

        File directory = new File("target", "chart-test");
        assertTrue(directory.mkdirs() || directory.isDirectory());
        chart.writeBeside(new File(directory, "GA_rocket_v1.ork"));

        File png = new File(directory, "GA_rocket_v1.png");
        File csv = new File(directory, "GA_rocket_v1.csv");
        assertTrue(png.isFile());
        byte[] magic = Files.readAllBytes(png.toPath());
        assertTrue(magic.length > 8);
        assertEquals((byte) 137, magic[0]);
        assertEquals((byte) 80, magic[1]);
        assertEquals((byte) 78, magic[2]);
        assertEquals((byte) 71, magic[3]);

        String text = Files.readString(csv.toPath());
        assertTrue(text.contains("generation,best_apogee_m"));
        assertTrue(text.contains("1,1000.0000"));
        assertTrue(text.contains("2,1100.5000"));
        assertFalse(chart.holdOpenUntilClosed());
    }
}
