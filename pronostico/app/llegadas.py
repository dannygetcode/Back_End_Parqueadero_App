"""Hora estimada de llegada por usuario: mediana de la primera entrada del mismo dia de semana (ultimas 8 semanas)."""
from datetime import datetime, timedelta

import numpy as np

DIAS = ["lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo"]
MIN_DIAS_CON_ENTRADA = 3


def hhmm(h: float) -> str:
    m = int(round(h * 60))
    return f"{m // 60 % 24:02d}:{m % 60:02d}"


def primeras_entradas(visitas) -> dict:
    """fecha -> hora decimal de la primera entrada del dia."""
    out = {}
    for v in sorted(visitas, key=lambda v: v.entrada):
        out.setdefault(v.entrada.date(), v.entrada.hour + v.entrada.minute / 60)
    return out


def estadistica_dia(primeras: dict, fecha, hoy, semanas: int) -> dict:
    """Probabilidad de venir y hora (p25/mediana/p75) para el dia de semana de `fecha`, mirando `semanas` atras."""
    ref = [hoy - timedelta(days=k) for k in range(1, semanas * 7 + 1)
           if (hoy - timedelta(days=k)).weekday() == fecha.weekday()]
    horas = [primeras[d] for d in ref if d in primeras]
    n, k = len(ref), len(horas)
    res = {"fecha": fecha.isoformat(), "diaSemana": DIAS[fecha.weekday()], "diasObservados": n,
           "diasConEntrada": k, "probabilidadVenir": round((k + 0.5) / (n + 1), 3) if n else None}
    if k >= MIN_DIAS_CON_ENTRADA:
        q = np.quantile(horas, [0.25, 0.5, 0.75])
        res.update(horaMediana=hhmm(q[1]), horaP25=hhmm(q[0]), horaP75=hhmm(q[2]), _p75=float(q[2]),
                   datosInsuficientes=False)
    else:
        res.update(horaMediana=None, horaP25=None, horaP75=None, _p75=None, datosInsuficientes=True)
    return res


def tiempo_fuera_horas(visitas, ahora: datetime, dias: int = 90):
    """Mediana de horas entre una salida y la siguiente entrada (ultimos `dias`). Devuelve (mediana, n)."""
    vs = sorted(visitas, key=lambda v: v.entrada)
    lim = ahora - timedelta(days=dias)
    gaps = [(b.entrada - a.salida).total_seconds() / 3600 for a, b in zip(vs, vs[1:])
            if a.salida is not None and a.salida >= lim]
    return (float(np.median(gaps)), len(gaps)) if gaps else (None, 0)


def proxima_llegada(visitas, ahora: datetime, semanas: int = 8, horizonte: int = 7) -> dict:
    """Primer dia (desde hoy) con probabilidad de venir >= 0.5; hoy solo si aun no entro y la hora p75 no paso.
    Si ningun dia llega a 0.5 devuelve el de mayor probabilidad con alcanzaUmbral50=false."""
    hoy = ahora.date()
    primeras = primeras_entradas(visitas)
    mejor = None
    for k in range(0, horizonte + 1):
        f = hoy + timedelta(days=k)
        est = estadistica_dia(primeras, f, hoy, semanas)
        if k == 0:
            hora_actual = ahora.hour + ahora.minute / 60
            if hoy in primeras or (est["_p75"] is not None and hora_actual > est["_p75"]):
                continue
        if est["probabilidadVenir"] is None:
            continue
        if mejor is None or est["probabilidadVenir"] > mejor["probabilidadVenir"]:
            mejor = est
        if est["probabilidadVenir"] >= 0.5:
            mejor = est
            break
    if mejor is None:
        return {"proxima": None}
    mejor = {k: v for k, v in mejor.items() if k != "_p75"}
    mejor["alcanzaUmbral50"] = mejor["probabilidadVenir"] >= 0.5
    return {"proxima": mejor}
