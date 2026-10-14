"""Generador de datos simulados (simulado=true) escrito directo en PostgreSQL.

Uso: python generar.py [--meses 12] [--semilla 2025] [--hasta AAAA-MM-DD] [--limpiar] [--usuario-demo-placa PLACA]
"""
import argparse
import json
import random
from datetime import date

import db
import nucleo


def limpiar(cur):
    # Orden por FK. Solo filas simulado=true; los reales no se tocan.
    for t in ("evento_acceso", "pago", "vehiculo", "usuario"):
        cur.execute(f"DELETE FROM {t} WHERE simulado")


def cargar_referencias(cur):
    cur.execute("SELECT id, tipo_vehiculo, valor_mensual, vigente_desde FROM tarifa WHERE parqueadero_id = 1")
    tarifas = [dict(id=i, tipo=t, valor=v, desde=d) for i, t, v, d in cur.fetchall()]
    cur.execute("SELECT id, codigo, tipo_vehiculo FROM cupo WHERE parqueadero_id = 1 AND activo")
    cupos = [dict(id=i, codigo=c, tipo=t) for i, c, t in cur.fetchall()]
    cur.execute("SELECT id FROM camara WHERE parqueadero_id = 1 ORDER BY id LIMIT 1")
    r = cur.fetchone()
    return tarifas, cupos, (r[0] if r else None)


def _insertar_eventos(cur, filas):
    cur.executemany(
        """INSERT INTO evento_acceso (parqueadero_id, placa_leida, vehiculo_id, usuario_id, camara_id, tipo,
               tipo_inferido, resultado, motivo, origen, ocurrido_en, registrado_en, simulado)
           VALUES (1, %s, %s, %s, %s, %s, true, %s, %s, 'CAMARA', %s, %s, true)""",
        [(p, v, u, c, t, r, m, o, o) for p, v, u, c, t, r, m, o in filas])


def _insertar_pago(cur, uid, p, t):
    aprobado = p.estado == "APROBADO"
    cur.execute(
        """INSERT INTO pago (usuario_id, tarifa_id, periodo_inicio, periodo_fin, monto_esperado, monto_ocr,
               fecha_pago_ocr, ocr_estado, ocr_datos, monto_confirmado, estado, motivo_rechazo,
               registrado_por, comprobante_ruta, comprobante_tipo, comprobante_sha256, revisado_en,
               simulado, creado_en, actualizado_en)
           VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,'USUARIO',%s,%s,%s,%s,true,%s,%s)""",
        (uid, t["id"], p.periodo_inicio, p.periodo_fin, p.monto, p.monto_ocr, p.fecha_pago_ocr,
         p.ocr_estado, json.dumps({"simulado": True}) if p.ocr_estado != "FALLIDO" else None,
         p.monto if aprobado else None, p.estado, p.motivo_rechazo, p.ruta,
         "image/jpeg" if p.ruta else None, p.sha, p.revisado_en, p.creado_en, p.revisado_en))


def escribir(cur, plan, tarifas, cupos, camara_id):
    cupo_id = {c["codigo"]: c["id"] for c in cupos}
    n_pago = n_ev = 0
    for u in plan.usuarios:
        cur.execute(
            """INSERT INTO usuario (parqueadero_id, cupo_id, telefono, nombre, apellido, estado,
                   consentimiento_datos_en, consentimiento_version, simulado, dado_de_baja_en, creado_en, actualizado_en)
               VALUES (1, %s, %s, %s, %s, %s, %s, 'simulado', true, %s, %s, %s) RETURNING id""",
            (None if u.baja else cupo_id[u.cupo_codigo], u.telefono, u.nombre, u.apellido, u.estado,
             u.creado_en, u.baja_en, u.creado_en, u.baja_en or u.creado_en))
        uid = cur.fetchone()[0]
        cur.execute(
            """INSERT INTO vehiculo (usuario_id, placa, tipo_vehiculo, carroceria, color, marca, activo, simulado, creado_en)
               VALUES (%s, %s, %s, %s, %s, %s, %s, true, %s) RETURNING id""",
            (uid, u.placa, u.tipo, u.carroceria, u.color, u.marca, not u.baja, u.creado_en))
        vid = cur.fetchone()[0]
        for p in u.pagos:
            t = nucleo.tarifa_vigente(tarifas, p.tipo, p.periodo_inicio)
            _insertar_pago(cur, uid, p, t)
            n_pago += 1
        filas = [(e.placa_leida, vid, uid, camara_id, e.tipo, e.resultado, e.motivo, e.ocurrido_en)
                 for e in u.eventos]
        _insertar_eventos(cur, filas)
        n_ev += len(filas)
    filas = [(e.placa_leida, None, None, camara_id, e.tipo, e.resultado, e.motivo, e.ocurrido_en)
             for e in plan.desconocidos]
    _insertar_eventos(cur, filas)
    return len(plan.usuarios), n_pago, n_ev + len(filas)


def historial_demo(cur, placa, tarifas, camara_id, semilla, meses, hasta):
    """Historial simulado (simulado=true) para el usuario REAL dueno de la placa. Devuelve (pagos, eventos, desde, hasta)."""
    cur.execute("""SELECT u.id, v.id, v.tipo_vehiculo FROM vehiculo v JOIN usuario u ON u.id = v.usuario_id
                   WHERE v.placa = %s AND v.activo AND NOT v.simulado AND NOT u.simulado
                     AND u.dado_de_baja_en IS NULL""", (placa,))
    fila = cur.fetchone()
    if not fila:
        raise SystemExit(f"No hay un usuario real activo con la placa {placa} (--usuario-demo-placa).")
    uid, vid, tipo = fila
    cur.execute("SELECT min(periodo_inicio) FROM pago WHERE usuario_id = %s AND NOT simulado AND estado <> 'RECHAZADO'",
                (uid,))
    inicio_real = cur.fetchone()[0]
    if inicio_real is None:
        raise SystemExit("El usuario demo no tiene pagos reales; el historial debe encajar con ellos.")
    cur.execute("SELECT min(ocurrido_en) FROM evento_acceso WHERE NOT simulado AND (vehiculo_id = %s OR placa_leida = %s)",
                (vid, placa))
    primer_real = cur.fetchone()[0]
    ahora = min(nucleo.datetime.now(nucleo.TZ), nucleo.a_dt(hasta, 23.99))
    tope = min(ahora, primer_real.astimezone(nucleo.TZ) if primer_real else ahora)
    tope = min(tope, nucleo.a_dt(inicio_real, 0))  # nada de visitas simuladas dentro del periodo real
    rng = random.Random(semilla + 7919)
    periodos = nucleo.periodos_hacia_atras(inicio_real, meses)
    pagos = nucleo.pagos_demo(rng, periodos, tipo, tarifas, ahora)
    eventos = nucleo.eventos_demo(rng, periodos[0][0], tope, placa)
    for p in pagos:
        _insertar_pago(cur, uid, p, nucleo.tarifa_vigente(tarifas, tipo, p.periodo_inicio))
    _insertar_eventos(cur, [(e.placa_leida, vid, uid, camara_id, e.tipo, e.resultado, e.motivo, e.ocurrido_en)
                            for e in eventos])
    return len(pagos), len(eventos), periodos[0][0], periodos[-1][1]


def main():
    ap = argparse.ArgumentParser(description="Genera datos simulados (simulado=true) del parqueadero.")
    ap.add_argument("--meses", type=int, default=12)
    ap.add_argument("--semilla", type=int, default=2025)
    ap.add_argument("--hasta", type=date.fromisoformat, default=date.today())
    ap.add_argument("--limpiar", action="store_true", help="borra solo filas simulado=true y las regenera")
    ap.add_argument("--usuario-demo-placa", metavar="PLACA", default=None,
                    help="da historial simulado al usuario REAL con esa placa (pagos y visitas anteriores a sus datos reales)")
    a = ap.parse_args()
    if not 6 <= a.meses <= 36:
        raise SystemExit("--meses debe estar entre 6 y 36")
    with db.conectar() as con, con.cursor() as cur:
        cur.execute("SELECT count(*) FROM usuario WHERE simulado")
        hay_simulados = cur.fetchone()[0] > 0
        if hay_simulados and not a.limpiar and not a.usuario_demo_placa:
            raise SystemExit("Ya hay datos simulados; usa --limpiar para regenerarlos.")
        tarifas, cupos, camara_id = cargar_referencias(cur)
        if len(cupos) != 7 or not tarifas:
            raise SystemExit("Faltan cupos/tarifas de referencia (Flyway V2).")
        if a.limpiar or not hay_simulados:
            plan = nucleo.planificar(a.semilla, a.meses, a.hasta, tarifas, cupos)
            limpiar(cur)  # misma transaccion: si algo falla, no se pierde nada
            nu, np_, ne = escribir(cur, plan, tarifas, cupos, camara_id)
            print(f"Generado ({plan.inicio} a {plan.hasta}, semilla {a.semilla}): {nu} usuarios, {np_} pagos, {ne} eventos.")
        else:
            print("Ya hay datos simulados generales; no se regeneran (usa --limpiar para hacerlo).")
        if a.usuario_demo_placa:
            cur.execute("""SELECT count(*) FROM pago p JOIN usuario u ON u.id = p.usuario_id
                           WHERE p.simulado AND NOT u.simulado""")
            if cur.fetchone()[0]:
                print("El historial demo ya existe; no se duplica (usa --limpiar para regenerarlo).")
                return
            np_, ne, d0, d1 = historial_demo(cur, a.usuario_demo_placa, tarifas, camara_id, a.semilla, a.meses, a.hasta)
            print(f"Historial demo (usuario real, placa indicada): {np_} pagos, {ne} eventos, periodos {d0} a {d1}.")


if __name__ == "__main__":
    main()
