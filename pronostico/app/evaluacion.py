"""Backtesting de origen movil (funciones puras; el script scripts/backtest.py las orquesta)."""
from datetime import date, timedelta

import numpy as np

from . import ocupacion as oc
from . import vacancia as vc


def backtest_ocupacion(serie: np.ndarray, base, capacidad: float, ventana_semanas: int | None,
                       min_semanas: int = 8, horizonte_h: int = oc.SEMANA_H) -> dict:
    """Pronostico puntual = mediana del perfil (minimiza MAE). Origen cada semana; pronostica las 168 h siguientes con el perfil de lo anterior.
    Baseline ingenuo: misma hora de la semana pasada."""
    err_m, err_med, err_n, dentro, lleno_p, lleno_n, lleno_y = [], [], [], [], [], [], []
    for t in range(min_semanas * oc.SEMANA_H, len(serie) - horizonte_h + 1, oc.SEMANA_H):
        est = oc.estadisticos(oc.perfil_estacional(serie, base, t, ventana_semanas), capacidad)
        real = serie[t:t + horizonte_h]
        k = (np.arange(t, t + horizonte_h) + base.weekday() * 24 + base.hour) % oc.SEMANA_H
        naive = serie[t - oc.SEMANA_H:t - oc.SEMANA_H + horizonte_h]
        err_m.append(np.abs(real - est["media"][k]))
        err_med.append(np.abs(real - est["mediana"][k]))
        err_n.append(np.abs(real - naive))
        dentro.append((real >= est["p10"][k]) & (real <= est["p90"][k]))
        lleno_p.append(est["pLleno"][k])
        lleno_n.append((naive >= capacidad).astype(float))
        lleno_y.append((real >= capacidad).astype(float))
    if not err_m:
        return {"n_horas": 0}
    c = np.concatenate
    y, p, pn = c(lleno_y), c(lleno_p), c(lleno_n)
    mae_m, mae_n = float(c(err_med).mean()), float(c(err_n).mean())
    return {
        "n_origenes": len(err_m), "n_horas": int(len(c(err_m))),
        "mae_modelo": round(float(c(err_med).mean()), 4), "mae_media": round(float(c(err_m).mean()), 4),
        "mae_baseline_semana_pasada": round(mae_n, 4),
        "mejora_relativa_vs_baseline": round(1 - mae_m / mae_n, 4) if mae_n > 0 else None,
        "cobertura_p10_p90": round(float(c(dentro).mean()), 4), "cobertura_nominal": 0.8,
        "brier_lleno_modelo": round(float(np.mean((p - y) ** 2)), 4),
        "brier_lleno_baseline": round(float(np.mean((pn - y) ** 2)), 4),
        "frecuencia_lleno_observada": round(float(y.mean()), 4),
    }


def _brier(p, y):
    return float(np.mean((np.asarray(p) - np.asarray(y)) ** 2))


def _logloss(p, y, eps=1e-6):
    p = np.clip(np.asarray(p, dtype=float), eps, 1 - eps)
    y = np.asarray(y, dtype=float)
    return float(-np.mean(y * np.log(p) + (1 - y) * np.log(1 - p)))


def backtest_vacancia(pagos_por_usuario: dict, hoy: date, gracia: int, media: float, peso: float,
                      horizonte: int = 30, paso: int = 7, min_historia_dias: int = 60) -> dict:
    """Origen cada semana. Evento: periodo que termina en (t, t+horizonte]; resultado: renovo a tiempo (1) o no (0),
    con el plazo ya vencido a `hoy`. Cada fin de periodo se evalua una sola vez (primer origen que lo cubre).
    Modelo: beta-binomial por usuario con su historial a t. Baseline: tasa constante global (misma contraccion)
    con todas las renovaciones resueltas a t."""
    todos = [p for ps in pagos_por_usuario.values() for p in ps]
    if not todos:
        return {"n": 0}
    t = min(p.pagado_el for p in todos) + timedelta(days=min_historia_dias)
    visto, pm, pb, ys = set(), [], [], []
    while t <= hoy:
        # tasa global con lo resuelto a t
        ex = n = 0
        for ps in pagos_por_usuario.values():
            conocidos = [p for p in ps if p.pagado_el <= t]
            obs = vc.observaciones(conocidos, t, gracia, incluir_final=True)
            ex, n = ex + sum(obs), n + len(obs)
        tasa = vc.media_posterior(ex, n, media, peso)
        for uid, ps in pagos_por_usuario.items():
            ps = sorted(ps, key=lambda p: p.inicio)
            conocidos = [p for p in ps if p.pagado_el <= t]
            if not conocidos:
                continue
            ultimo = max(conocidos, key=lambda p: p.inicio)
            fin = ultimo.fin
            if not (t < fin <= t + timedelta(days=horizonte)) or (uid, fin) in visto:
                continue
            plazo = fin + timedelta(days=1 + gracia)
            if plazo > hoy:
                continue        # resultado aun no observable
            visto.add((uid, fin))
            sig = [p for p in ps if p.inicio > ultimo.inicio]
            y = 1 if sig and sig[0].pagado_el <= plazo else 0
            obs = vc.observaciones(conocidos, t, gracia)
            pm.append(vc.media_posterior(sum(obs), len(obs), media, peso))
            pb.append(tasa)
            ys.append(y)
        t += timedelta(days=paso)
    if not ys:
        return {"n": 0}
    return {
        "n": len(ys), "tasa_renovacion_observada": round(float(np.mean(ys)), 4),
        "brier_modelo": round(_brier(pm, ys), 4), "brier_baseline_tasa_constante": round(_brier(pb, ys), 4),
        "logloss_modelo": round(_logloss(pm, ys), 4), "logloss_baseline_tasa_constante": round(_logloss(pb, ys), 4),
        "horizonte_dias": horizonte,
    }
