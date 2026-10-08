"""Orquesta los modelos: Datos + ahora -> respuestas JSON. Sin acceso a la BD (eso lo hace main.py)."""
import json
import os
from datetime import datetime, timedelta

import numpy as np

from . import config as cfg
from . import llegadas as lg
from . import ocupacion as oc
from . import vacancia as vc


def cargar_metricas(ruta: str | None = None):
    ruta = ruta or cfg.METRICAS_RUTA
    if not os.path.isfile(ruta):
        return None
    with open(ruta, encoding="utf-8") as f:
        return json.load(f)


def _metricas(modelo: str, metricas):
    if not metricas:
        return {"disponible": False, "nota": "Sin backtest: ejecutar scripts/backtest.py"}
    m = metricas.get(modelo)
    return {"disponible": m is not None, "origenDatos": metricas.get("origenDatos"),
            "generadoEn": metricas.get("generadoEn"), "resumen": m}


def _sobre(ahora, incluir, modelo, metricas, cuerpo):
    return {"generadoEn": ahora.replace(tzinfo=cfg.TZ).isoformat(timespec="seconds"), "versionModelo": cfg.VERSION_MODELO,
            "incluyeSimulados": incluir, "metricas": _metricas(modelo, metricas), **cuerpo}


def respuesta_vacancia(datos, ahora: datetime, incluir: bool, metricas=None) -> dict:
    ahora = ahora.replace(tzinfo=None)
    por_tipo = vc.pronosticar(datos, ahora.date(), cfg.GRACIA_DIAS, cfg.PRIOR_MEDIA, cfg.PRIOR_PESO,
                              cfg.MUESTRAS_MC, cfg.SEMILLA)
    return _sobre(ahora, incluir, "vacancia", metricas, {
        "parametros": {"graciaDias": cfg.GRACIA_DIAS, "priorMedia": cfg.PRIOR_MEDIA, "priorPeso": cfg.PRIOR_PESO,
                       "periodoDias": vc.PERIODO_DIAS},
        "nota": "probabilidad = P(al menos un cupo libre del tipo); p10/p90 = incertidumbre por pocos datos por usuario",
        "porTipo": por_tipo})


def _base_serie(visitas, ahora):
    primero = min(v.entrada for v in visitas)
    base = primero.replace(hour=0, minute=0, second=0, microsecond=0)
    n = int((ahora.replace(minute=0, second=0, microsecond=0) - base).total_seconds() // 3600) + 1
    return base, n


def respuesta_ocupacion(datos, ahora: datetime, incluir: bool, horizonte_dias: int, metricas=None) -> dict:
    ahora = ahora.replace(tzinfo=None)
    visitas = oc.construir_visitas(datos.eventos)
    capacidad = sum(1 for _, t in datos.cupos if t == "CARRO")
    cuerpo = {"tipoVehiculo": "CARRO", "capacidad": capacidad, "horizonteDias": horizonte_dias, "dias": []}
    if not visitas or capacidad == 0:
        cuerpo.update(datosInsuficientes=True, semanasHistoria=0)
        return _sobre(ahora, incluir, "ocupacion", metricas, cuerpo)
    base, n = _base_serie(visitas, ahora)
    serie = oc.serie_ocupacion(visitas, "CARRO", base, n, ahora)
    est = oc.estadisticos(oc.perfil_estacional(serie, base, n, cfg.VENTANA_SEMANAS_OCUPACION), capacidad)
    redondeo = lambda x: None if np.isnan(x) else round(float(x), 2)  # noqa: E731
    hoy0 = ahora.replace(hour=0, minute=0, second=0, microsecond=0)
    for d in range(horizonte_dias):
        dia = hoy0 + timedelta(days=d)
        horas = []
        for h in range(24):
            k = oc.clave(dia + timedelta(hours=h))
            horas.append({"hora": h, "mediana": redondeo(est["mediana"][k]), "media": redondeo(est["media"][k]), "p10": redondeo(est["p10"][k]),
                          "p90": redondeo(est["p90"][k]),
                          "probLleno": None if np.isnan(est["pLleno"][k]) else round(float(est["pLleno"][k]), 3),
                          "observaciones": int(est["n"][k])})
        cuerpo["dias"].append({"fecha": dia.date().isoformat(), "horas": horas})
    cuerpo.update(datosInsuficientes=False, semanasHistoria=round(n / oc.SEMANA_H, 1),
                  ventanaSemanas=cfg.VENTANA_SEMANAS_OCUPACION)
    return _sobre(ahora, incluir, "ocupacion", metricas, cuerpo)


def respuesta_llegadas(datos, ahora: datetime, incluir: bool, metricas=None) -> dict:
    ahora = ahora.replace(tzinfo=None)
    visitas = oc.construir_visitas(datos.eventos)
    por_usuario = {}
    for v in visitas:
        if v.usuario_id is not None:
            por_usuario.setdefault(v.usuario_id, []).append(v)
    usuarios = []
    for uid, estado, cupo, tipo in sorted(datos.usuarios, key=lambda u: u[2]):
        vs = por_usuario.get(uid, [])
        fuera, n_gaps = lg.tiempo_fuera_horas(vs, ahora)
        item = {"usuarioId": uid, "cupo": cupo, "tipoVehiculo": tipo, "estado": estado,
                **lg.proxima_llegada(vs, ahora, cfg.SEMANAS_LLEGADA),
                "tiempoFueraMedianaHoras": None if fuera is None else round(fuera, 1), "visitasConsideradas": n_gaps}
        usuarios.append(item)
    return _sobre(ahora, incluir, "llegadas", metricas, {
        "ventanaSemanas": cfg.SEMANAS_LLEGADA,
        "nota": "hora = primera entrada del dia (hora local); probabilidadVenir con suavizado Jeffreys",
        "usuarios": usuarios})
