"""Lectura de la BD (solo SELECT, transaccion de solo lectura). Nunca imprime credenciales."""
import os
from pathlib import Path

import psycopg

from .config import TZ
from .modelos import Datos, Pago

RAIZ_REPO = Path(__file__).resolve().parents[2]


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
    # El .env del repo solo es un respaldo para desarrollo local; en compose mandan las variables de entorno.
    env = {**_leer_env(RAIZ_REPO / ".env"), **os.environ}
    faltan = [k for k in ("POSTGRES_DB", "DB_USER", "DB_PASSWORD") if not env.get(k)]
    if faltan:
        raise RuntimeError("Faltan variables de entorno: " + ", ".join(faltan))
    conn = psycopg.connect(host=env.get("DB_HOST", "localhost"), port=int(env.get("DB_PORT", "5432")),
                           dbname=env["POSTGRES_DB"], user=env["DB_USER"], password=env["DB_PASSWORD"],
                           connect_timeout=5)
    conn.read_only = True
    return conn


def _local(ts):
    return ts.astimezone(TZ).replace(tzinfo=None)


def cargar(conn, simulados: bool) -> Datos:
    """Carga solo filas simulado=<simulados>: nunca se mezclan simulados con reales."""
    d = Datos()
    with conn.cursor() as cur:
        cur.execute("SELECT codigo, tipo_vehiculo FROM cupo WHERE activo ORDER BY codigo")
        d.cupos = [(r[0], r[1]) for r in cur.fetchall()]
        cur.execute("""SELECT u.id, u.estado, c.codigo, c.tipo_vehiculo FROM usuario u
                       JOIN cupo c ON c.id = u.cupo_id
                       WHERE u.simulado = %s AND u.dado_de_baja_en IS NULL ORDER BY u.id""", (simulados,))
        d.usuarios = [tuple(r) for r in cur.fetchall()]
        cur.execute("""SELECT usuario_id, periodo_inicio, periodo_fin, creado_en FROM pago
                       WHERE estado = 'APROBADO' AND simulado = %s ORDER BY usuario_id, periodo_inicio""",
                    (simulados,))
        for uid, ini, fin, creado in cur.fetchall():
            d.pagos.setdefault(uid, []).append(Pago(ini, fin, _local(creado).date()))
        cur.execute("""SELECT e.vehiculo_id, e.usuario_id, v.tipo_vehiculo, e.tipo, e.ocurrido_en
                       FROM evento_acceso e JOIN vehiculo v ON v.id = e.vehiculo_id
                       WHERE e.resultado = 'PERMITIDO' AND e.simulado = %s
                       ORDER BY e.vehiculo_id, e.ocurrido_en""", (simulados,))
        d.eventos = [(r[0], r[1], r[2], r[3], _local(r[4])) for r in cur.fetchall()]
    conn.rollback()
    return d
