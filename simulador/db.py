"""Conexion a PostgreSQL por variables de entorno (o .env del repo). Nunca imprime credenciales."""
import os
from pathlib import Path

import psycopg

RAIZ = Path(__file__).resolve().parent.parent


def _leer_env(ruta: Path) -> dict:
    datos = {}
    if ruta.is_file():
        for linea in ruta.read_text(encoding="utf-8").splitlines():
            linea = linea.strip()
            if linea and not linea.startswith("#") and "=" in linea:
                k, v = linea.split("=", 1)
                datos[k.strip()] = v.strip().strip('"').strip("'")
    return datos


def conectar() -> psycopg.Connection:
    env = {**_leer_env(RAIZ / ".env"), **os.environ}
    faltan = [k for k in ("POSTGRES_DB", "DB_USER", "DB_PASSWORD") if not env.get(k)]
    if faltan:
        raise SystemExit("Faltan variables de entorno: " + ", ".join(faltan))
    try:
        return psycopg.connect(host=env.get("DB_HOST", "localhost"), port=int(env.get("DB_PORT", "5432")),
                               dbname=env["POSTGRES_DB"], user=env["DB_USER"], password=env["DB_PASSWORD"],
                               connect_timeout=5)
    except psycopg.OperationalError as e:
        raise SystemExit("No se pudo conectar a la BD (revisa DB_HOST/DB_PORT y que este publicada): "
                         + str(e).splitlines()[0])
