package br.com.tw.terrenos.managers;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PlotPricingTest {
    @Test
    public void doublesPriceForEachAdditionalPlot() {
        assertEquals(50000.0, PlotPricing.nextPrice(50000.0, 2.0, 0), 0.0);
        assertEquals(100000.0, PlotPricing.nextPrice(50000.0, 2.0, 1), 0.0);
        assertEquals(200000.0, PlotPricing.nextPrice(50000.0, 2.0, 2), 0.0);
    }

    @Test
    public void roundsToCents() {
        assertEquals(10.01, PlotPricing.nextPrice(10.005, 1.0, 0), 0.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOverflowingPrice() {
        PlotPricing.nextPrice(Double.MAX_VALUE, 2.0, 2);
    }
}
