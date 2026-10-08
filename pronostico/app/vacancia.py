"""Vacancia de cupos: beta-binomial contraido por usuario y probabilidad de >=1 cupo libre por tipo.

Para cada titular i: p_i = P(renueva a tiempo), con previo Beta(media*peso, (1-media)*peso) y su historial de
renovaciones (a tiempo = paga a mas tardar fin + 1 + gracia). El cupo de i se libera potencialmente en
L_i = fin + gracia; si renueva queda ocupado otros 30 dias y vuelve a decidir, asi que a la fecha d sigue
ocupado con probabilidad p_i^m, m = 1 + (d - L_i)//30 (m = 0 si d < L_i). Con independencia entre usuarios:
P(>=1 libre en d) = 1 - prod_i p_i^m_i. La incertidumbre sale de muestrear p_i ~ Beta posterior.
"""
from dataclasses import dataclass
from datetime import date, timedelta

import numpy as np

HORIZONTES = (7, 15, 30, 60, 90)
PERIODO_DIAS = 30
MAX_DIAS = 180


def posterior(exitos: int, n: int, media: float, peso: float) -> tuple[float, float]:
    """Parametros (a, b) de la Beta posterior."""
    return media * peso + exitos, (1 - media) * peso + (n - exitos)


def media_posterior(exitos: int, n: int, media: float, peso: float) -> float:
    a, b = posterior(exitos, n, media, peso)
    return a / (a + b)


def observaciones(pagos, hoy: date, gracia: int, incluir_final: bool = False) -> list[int]:
    """1 si el pago k+1 llego a tiempo tras el periodo k, 0 si no. Con incluir_final, un ultimo periodo cuyo
    plazo ya vencio sin pago nuevo cuenta como 0 (no renovo)."""
    ps = sorted(pagos, key=lambda p: p.inicio)
    obs = [1 if ps[k + 1].pagado_el <= ps[k].fin + timedelta(days=1 + gracia) else 0
           for k in range(len(ps) - 1)]
    if incluir_final and ps and hoy > ps[-1].fin + timedelta(days=1 + gracia):
        obs.append(0)
    return obs


@dataclass
class Titular:
    usuario_id: int
    cupo: str
    estado: str
    fin: date
    exitos: int
    n: int


def liberacion(t: Titular, hoy: date, gracia: int) -> date:
    """Fecha en que el cupo queda potencialmente libre (un no ACTIVO ya esta en ese punto)."""
    if t.estado != "ACTIVO":
        return hoy
    return max(hoy, t.fin + timedelta(days=gracia))


def periodos_hasta(fecha_liberacion: date, d: date) -> int:
    return 0 if d < fecha_liberacion else 1 + (d - fecha_liberacion).days // PERIODO_DIAS


def prob_libre(titulares, total_cupos: int, hoy: date, gracia: int, media: float, peso: float,
               muestras: int, semilla: int):
    """Devuelve (media, p10, p90): arreglos de largo MAX_DIAS+1 con P(>=1 cupo libre) desde hoy."""
    dias = MAX_DIAS + 1
    if total_cupos > len(titulares) or not titulares:
        uno = np.ones(dias)
        return uno, uno.copy(), uno.copy()
    ab = np.array([posterior(t.exitos, t.n, media, peso) for t in titulares])
    rng = np.random.default_rng(semilla)
    p = np.clip(rng.beta(ab[:, 0], ab[:, 1], size=(muestras, len(titulares))), 1e-12, 1.0)
    m = np.array([[periodos_hasta(liberacion(t, hoy, gracia), hoy + timedelta(days=k)) for t in titulares]
                  for k in range(dias)])
    p_libre = 1.0 - np.exp(np.log(p) @ m.T)     # (muestras, dias)
    return p_libre.mean(axis=0), np.quantile(p_libre, 0.10, axis=0), np.quantile(p_libre, 0.90, axis=0)


def primera_fecha(media_dias: np.ndarray, hoy: date, umbral: float = 0.5):
    idx = np.nonzero(media_dias >= umbral)[0]
    return None if idx.size == 0 else hoy + timedelta(days=int(idx[0]))


def construir_titulares(datos, hoy: date, gracia: int, tipo: str) -> list[Titular]:
    res = []
    for uid, estado, cupo, tipo_cupo in datos.usuarios:
        if tipo_cupo != tipo:
            continue
        pagos = datos.pagos.get(uid, [])
        obs = observaciones(pagos, hoy, gracia)
        fin = max((p.fin for p in pagos), default=hoy)
        res.append(Titular(uid, cupo, estado, fin, sum(obs), len(obs)))
    return res


def pronosticar(datos, hoy: date, gracia: int, media: float, peso: float, muestras: int, semilla: int) -> dict:
    out = {}
    for tipo in sorted({t for _, t in datos.cupos}):
        total = sum(1 for _, t in datos.cupos if t == tipo)
        tit = construir_titulares(datos, hoy, gracia, tipo)
        mu, lo, hi = prob_libre(tit, total, hoy, gracia, media, peso, muestras, semilla)
        primera = primera_fecha(mu, hoy)
        out[tipo] = {
            "capacidad": total,
            "titulares": len(tit),
            "cuposSinAsignar": max(0, total - len(tit)),
            "probabilidades": [
                {"dias": d, "fecha": (hoy + timedelta(days=d)).isoformat(), "probabilidad": round(float(mu[d]), 3),
                 "p10": round(float(lo[d]), 3), "p90": round(float(hi[d]), 3)} for d in HORIZONTES],
            "primeraFechaProbMayorIgual50": primera.isoformat() if primera else None,
            "detalle": [
                {"usuarioId": t.usuario_id, "cupo": t.cupo, "estado": t.estado, "fechaFin": t.fin.isoformat(),
                 "fechaLiberacionPotencial": liberacion(t, hoy, gracia).isoformat(),
                 "pRenovar": round(media_posterior(t.exitos, t.n, media, peso), 3),
                 "renovacionesObservadas": t.n, "renovacionesATiempo": t.exitos}
                for t in sorted(tit, key=lambda t: t.cupo)],
        }
    return out
