"""Ocupacion por hora: serie de vehiculos dentro y perfil estacional por (dia de semana, hora)."""
import math
from datetime import datetime

import numpy as np

from .modelos import Visita

SEMANA_H = 168


def construir_visitas(eventos) -> list[Visita]:
    """Empareja ENTRADA -> siguiente SALIDA por vehiculo. Una ENTRADA repetida o una SALIDA sin entrada se ignoran."""
    visitas, abiertas = [], {}
    for veh, uid, tipo_veh, tipo, ts in sorted(eventos, key=lambda e: (e[0], e[4])):
        if tipo == "ENTRADA":
            abiertas.setdefault(veh, (uid, tipo_veh, ts))
        elif veh in abiertas:
            u, tv, ent = abiertas.pop(veh)
            visitas.append(Visita(u, veh, tv, ent, ts))
    visitas += [Visita(u, veh, tv, ent, None) for veh, (u, tv, ent) in abiertas.items()]
    return sorted(visitas, key=lambda v: v.entrada)


def _horas(dt: datetime, base: datetime) -> int:
    return math.ceil((dt - base).total_seconds() / 3600)


def serie_ocupacion(visitas, tipo: str, base: datetime, n_horas: int, ahora: datetime) -> np.ndarray:
    """Vehiculos dentro en cada hora en punto: base + i horas (base debe estar alineada a la hora)."""
    diff = np.zeros(n_horas + 1)
    for v in visitas:
        if v.tipo_vehiculo != tipo:
            continue
        i0 = max(_horas(v.entrada, base), 0)
        i1 = min(_horas(v.salida or ahora, base), n_horas)
        if i1 > i0:
            diff[i0] += 1
            diff[i1] -= 1
    return np.cumsum(diff)[:n_horas]


def perfil_estacional(serie: np.ndarray, base: datetime, hasta: int, ventana_semanas: int | None = None):
    """Lista de 168 arreglos: observaciones de cada (dia de semana, hora) en serie[:hasta]. Indice = dow*24+hora."""
    desde = 0 if not ventana_semanas else max(0, hasta - ventana_semanas * SEMANA_H)
    idx = np.arange(desde, hasta)
    claves = (idx + base.weekday() * 24 + base.hour) % SEMANA_H
    return [serie[desde:hasta][claves == k] for k in range(SEMANA_H)]


def estadisticos(perfil, capacidad: float) -> dict:
    """Por clave: n, media, mediana, p10, p90 y probabilidad de lleno (Jeffreys: (k+.5)/(n+1))."""
    nan = lambda: np.full(SEMANA_H, np.nan)  # noqa: E731
    n = np.array([len(v) for v in perfil], dtype=float)
    media, mediana, p10, p90, pl = nan(), nan(), nan(), nan(), nan()
    for k, v in enumerate(perfil):
        if len(v):
            media[k], mediana[k] = v.mean(), np.median(v)
            p10[k], p90[k] = np.quantile(v, 0.10), np.quantile(v, 0.90)
            pl[k] = ((v >= capacidad).sum() + 0.5) / (len(v) + 1)
    return {"n": n, "media": media, "mediana": mediana, "p10": p10, "p90": p90, "pLleno": pl}


def clave(dt: datetime) -> int:
    return dt.weekday() * 24 + dt.hour
