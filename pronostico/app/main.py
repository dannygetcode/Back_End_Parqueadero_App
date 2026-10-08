"""Servicio de pronostico (FastAPI). Solo lo consume el backend Java dentro de la red interna de compose:
NO tiene autenticacion y no debe publicarse al exterior."""
import logging
from datetime import datetime

from fastapi import FastAPI, HTTPException, Query

from . import config as cfg
from . import db, servicio
from .cache import CacheTTL

log = logging.getLogger("pronostico")
app = FastAPI(title="Pronostico del parqueadero", version=cfg.VERSION_MODELO,
              docs_url=None, redoc_url=None, openapi_url=None)
cache = CacheTTL(cfg.CACHE_HORAS * 3600)


def _calcular(nombre: str, incluir: bool, extra: tuple, fn):
    def fabrica():
        try:
            conn = db.conectar()
            try:
                datos = db.cargar(conn, incluir)
            finally:
                conn.close()
        except Exception as e:  # no filtrar detalles (pueden incluir host o usuario)
            log.error("Fallo al leer la BD: %s", type(e).__name__)
            raise HTTPException(status_code=503, detail="No se pudo leer la base de datos") from None
        return fn(datos, datetime.now(cfg.TZ), incluir, servicio.cargar_metricas())
    return cache.obtener((nombre, incluir, extra), fabrica)


@app.get("/salud")
def salud():
    return {"estado": "ok", "versionModelo": cfg.VERSION_MODELO}


@app.get("/pronostico/vacancia")
def vacancia(incluirSimulados: bool = False):
    return _calcular("vacancia", incluirSimulados, (), servicio.respuesta_vacancia)


@app.get("/pronostico/ocupacion")
def ocupacion(horizonteDias: int = Query(7, ge=1, le=cfg.HORIZONTE_MAX_DIAS), incluirSimulados: bool = False):
    return _calcular("ocupacion", incluirSimulados, (horizonteDias,),
                     lambda d, a, i, m: servicio.respuesta_ocupacion(d, a, i, horizonteDias, m))


@app.get("/pronostico/llegadas")
def llegadas(incluirSimulados: bool = False):
    return _calcular("llegadas", incluirSimulados, (), servicio.respuesta_llegadas)
