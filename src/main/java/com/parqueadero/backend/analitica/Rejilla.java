package com.parqueadero.backend.analitica;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Cubetas contiguas de igual duración (1 h o 24 h; Bogotá no tiene horario de verano). */
record Rejilla(Instant inicio, Duration paso, int n) {

    Instant inicioDe(int i) {
        return inicio.plus(paso.multipliedBy(i));
    }

    Instant fin() {
        return inicioDe(n);
    }

    /** Índices (a, b) inclusive de las cubetas que toca el intervalo, o null si no toca ninguna. */
    int[] rango(Instant ini, Instant fin) {
        if (!fin.isAfter(ini) || !fin.isAfter(inicio) || !ini.isBefore(fin())) {
            return null;
        }
        long p = paso.toMillis();
        int a = ini.isBefore(inicio) ? 0 : (int) ((ini.toEpochMilli() - inicio.toEpochMilli()) / p);
        int b = (int) Math.min(n - 1, (fin.toEpochMilli() - 1 - inicio.toEpochMilli()) / p);
        return new int[]{a, b};
    }

    /** Ocupación media (ponderada por tiempo) y pico de cada cubeta, con barrido de +1/-1. */
    Resultado ocupacion(List<Intervalos.Intervalo> ivs) {
        List<long[]> ev = new ArrayList<>();
        for (Intervalos.Intervalo iv : ivs) {
            ev.add(new long[]{iv.ini().toEpochMilli(), 1});
            ev.add(new long[]{iv.fin().toEpochMilli(), -1});
        }
        ev.sort(Comparator.comparingLong((long[] x) -> x[0]).thenComparingLong(x -> x[1]));
        double[] media = new double[n];
        int[] pico = new int[n];
        int k = 0;
        int c = 0;
        for (int i = 0; i < n; i++) {
            long ini = inicioDe(i).toEpochMilli();
            long fin = inicioDe(i + 1).toEpochMilli();
            while (k < ev.size() && ev.get(k)[0] <= ini) {
                c += (int) ev.get(k++)[1];
            }
            int max = c;
            double area = 0;
            long last = ini;
            while (k < ev.size() && ev.get(k)[0] < fin) {
                area += (double) c * (ev.get(k)[0] - last);
                last = ev.get(k)[0];
                c += (int) ev.get(k++)[1];
                max = Math.max(max, c);
            }
            area += (double) c * (fin - last);
            media[i] = area / (fin - ini);
            pico[i] = max;
        }
        return new Resultado(media, pico);
    }

    record Resultado(double[] media, int[] pico) {
    }
}
