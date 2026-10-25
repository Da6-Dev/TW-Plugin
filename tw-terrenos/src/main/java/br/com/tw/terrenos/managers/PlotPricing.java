package br.com.tw.terrenos.managers;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PlotPricing {
    private PlotPricing() {
    }

    public static double nextPrice(double basePrice, double multiplier, int ownedPlots) {
        if (!Double.isFinite(basePrice) || basePrice < 0) {
            throw new IllegalArgumentException("O preço base deve ser finito e não negativo.");
        }
        if (!Double.isFinite(multiplier) || multiplier < 1) {
            throw new IllegalArgumentException("O multiplicador deve ser finito e no mínimo 1.");
        }
        if (ownedPlots < 0) {
            throw new IllegalArgumentException("A quantidade de terrenos não pode ser negativa.");
        }

        BigDecimal price = BigDecimal.valueOf(basePrice)
                .multiply(BigDecimal.valueOf(multiplier).pow(ownedPlots))
                .setScale(2, RoundingMode.HALF_UP);
        double result = price.doubleValue();
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException("O preço calculado excede o limite suportado.");
        }
        return result;
    }
}
