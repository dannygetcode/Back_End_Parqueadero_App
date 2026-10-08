package com.parqueadero.backend.analitica;

import java.util.Arrays;
import java.util.Collection;

/** Estadísticos simples (percentil por interpolación lineal). Devuelven null si no hay datos. */
final class Estadistica {

    private Estadistica() {
    }

    static Double percentil(Collection<? extends Number> valores, double p) {
        if (valores.isEmpty()) {
            return null;
        }
        double[] v = valores.stream().mapToDouble(Number::doubleValue).toArray();
        Arrays.sort(v);
        double pos = p * (v.length - 1);
        int i = (int) Math.floor(pos);
        double r = i + 1 < v.length ? v[i] + (pos - i) * (v[i + 1] - v[i]) : v[i];
        return redondear(r, 1);
    }

    static Double mediana(Collection<? extends Number> valores) {
        return percentil(valores, 0.5);
    }

    static Double media(Collection<? extends Number> valores) {
        return valores.isEmpty() ? null
                : redondear(valores.stream().mapToDouble(Number::doubleValue).average().orElse(0), 1);
    }

    static double redondear(double v, int decimales) {
        double f = Math.pow(10, decimales);
        return Math.round(v * f) / f;
    }
}
