"""Resumen estadistico (sin datos personales) y chequeos de integridad de los datos simulados.

Uso: python verificar.py [--hasta AAAA-MM-DD]   -> exit 1 si algun chequeo falla.
"""
import argparse
import statistics as st
import sys
from collections import defaultdict
from datetime import date, datetime, timedelta

import db
import nucleo

DIAS = ["Lun", "Mar", "Mie", "Jue", "Vie", "Sab", "Dom"]
FRANJAS = [(0, 6), (6, 12), (12, 18), (18, 24)]


def pct(vals, q):
    s = sorted(vals)
    if not s:
        return float("nan")
    i = (len(s) - 1) * q
    lo, hi = int(i), min(int(i) + 1, len(s) - 1)
    return s[lo] + (s[hi] - s[lo]) * (i - lo)


def verificar_demo(cur, chequeo):
    """Usuarios REALES con historial simulado (--usuario-demo-placa). Solo conteos y fechas, sin datos personales."""
    cur.execute("""SELECT DISTINCT u.id FROM usuario u WHERE NOT u.simulado AND (
                     EXISTS (SELECT 1 FROM pago p WHERE p.usuario_id = u.id AND p.simulado)
                  OR EXISTS (SELECT 1 FROM evento_acceso e WHERE e.usuario_id = u.id AND e.simulado))""")
    ids = [r[0] for r in cur.fetchall()]
    print(f"
== Historial demo en usuarios reales: {len(ids)}")
    for n, uid in enumerate(ids, 1):
        cur.execute("""SELECT count(*) FILTER (WHERE simulado AND estado='APROBADO'),
                              count(*) FILTER (WHERE simulado AND estado='RECHAZADO'),
                              count(*) FILTER (WHERE NOT simulado), min(periodo_inicio) FILTER (WHERE simulado),
                              max(periodo_fin) FILTER (WHERE simulado)
                       FROM pago WHERE usuario_id = %s""", (uid,))
        ap, rj, reales, d0, d1 = cur.fetchone()
        cur.execute("""SELECT tipo, ocurrido_en, simulado FROM evento_acceso
                       WHERE usuario_id = %s AND resultado='PERMITIDO' ORDER BY ocurrido_en, id""", (uid,))
        evs = [(t, o.astimezone(nucleo.TZ), s) for t, o, s in cur.fetchall()]
        sim = [e for e in evs if e[2]]
        print(f"  usuario demo #{n}: pagos simulados aprobados={ap} rechazados={rj}, pagos reales={reales}, "
              f"periodos {d0} a {d1}; eventos simulados={len(sim)} (reales={len(evs) - len(sim)}), "
              f"{sim[0][1].date() if sim else None} a {sim[-1][1].date() if sim else None}")
        cur.execute("""SELECT periodo_inicio, periodo_fin FROM pago
                       WHERE usuario_id = %s AND estado='APROBADO' ORDER BY periodo_inicio""", (uid,))
        per = cur.fetchall()
        solapes = sum(1 for a, b in zip(per, per[1:]) if b[0] <= a[1])
        huecos = sum(1 for a, b in zip(per, per[1:]) if b[0] != a[1] + timedelta(days=1))
        chequeo(f"demo #{n}: periodos (simulados + reales) sin solapes", solapes == 0)
        chequeo(f"demo #{n}: periodos (simulados + reales) sin huecos, incluido el limite con el primer pago real",
                huecos == 0)
        chequeo(f"demo #{n}: eventos simulados + reales alternan ENTRADA/SALIDA",
                nucleo.alternancia_valida([(t, o) for t, o, _ in evs]))
        chequeo(f"demo #{n}: el historial simulado termina en SALIDA", bool(sim) and sim[-1][0] == "SALIDA")
        cur.execute("""SELECT (SELECT count(*) FROM pago WHERE usuario_id = %s AND simulado AND (creado_en > now()
                                                   OR revisado_en > now())),
                              (SELECT count(*) FROM evento_acceso WHERE usuario_id = %s AND simulado AND ocurrido_en > now())""",
                    (uid, uid))
        fut = cur.fetchone()
        chequeo(f"demo #{n}: nada simulado en el futuro", sum(fut) == 0)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--hasta", type=date.fromisoformat, default=date.today())
    hasta = ap.parse_args().hasta
    fallos = []

    def chequeo(nombre, ok, detalle=""):
        print(f"  [{'OK' if ok else 'FALLA'}] {nombre}{(' - ' + detalle) if detalle else ''}")
        if not ok:
            fallos.append(nombre)

    with db.conectar() as con, con.cursor() as cur:
        print("== Conteos (simulado / total)")
        for t in ("usuario", "vehiculo", "pago", "evento_acceso"):
            cur.execute(f"SELECT count(*) FILTER (WHERE simulado), count(*) FROM {t}")
            s, n = cur.fetchone()
            print(f"  {t}: {s} / {n}")
        cur.execute("SELECT count(*) FILTER (WHERE estado='ACTIVO'), count(*) FILTER (WHERE estado='VENCIDO'), "
                    "count(*) FILTER (WHERE dado_de_baja_en IS NOT NULL) FROM usuario WHERE simulado")
        print("  usuarios simulados: activos=%d vencidos=%d bajas=%d" % cur.fetchone())

        cur.execute("SELECT min(ocurrido_en), max(ocurrido_en) FROM evento_acceso WHERE simulado")
        a, b = cur.fetchone()
        cur.execute("SELECT min(periodo_inicio), max(periodo_fin) FROM pago WHERE simulado")
        c, d = cur.fetchone()
        print(f"\n== Rangos\n  eventos: {a.date() if a else None} a {b.date() if b else None}; periodos de pago: {c} a {d}")

        # ---- pagos
        cur.execute("""SELECT count(*), count(*) FILTER (WHERE (creado_en AT TIME ZONE 'America/Bogota')::date > periodo_inicio),
                              count(*) FILTER (WHERE estado='RECHAZADO')
                       FROM pago WHERE simulado AND estado <> 'RECHAZADO'""")
        n, tard, _ = cur.fetchone()
        cur.execute("SELECT count(*) FROM pago WHERE simulado AND estado='RECHAZADO'")
        print(f"\n== Pagos\n  aprobados: {n}; tardios (pagados despues del inicio del periodo): "
              f"{100 * tard / max(n, 1):.1f}%; rechazados: {cur.fetchone()[0]}")

        # ---- eventos permitidos por vehiculo
        cur.execute("""SELECT vehiculo_id, tipo, ocurrido_en FROM evento_acceso
                       WHERE simulado AND resultado='PERMITIDO' ORDER BY vehiculo_id, ocurrido_en, id""")
        por_veh = defaultdict(list)
        for v, t, o in cur.fetchall():
            por_veh[v].append((t, o.astimezone(nucleo.TZ)))
        estancias, fuera, horas_ent, ocup = [], [], [], defaultdict(float)
        alternancia_ok = True
        for evs in por_veh.values():
            alternancia_ok &= nucleo.alternancia_valida(evs)
            ent = None
            prev_sal = None
            for t, o in evs:
                if t == "ENTRADA":
                    horas_ent.append(o.hour + o.minute / 60)
                    if prev_sal:
                        fuera.append((o - prev_sal).total_seconds() / 3600)
                    ent = o
                elif ent:
                    estancias.append((o - ent).total_seconds() / 3600)
                    prev_sal = o
                    # ocupacion por hora calendario
                    h = ent.replace(minute=0, second=0, microsecond=0)
                    while h < o:
                        solape = (min(o, h + timedelta(hours=1)) - max(ent, h)).total_seconds() / 3600
                        ocup[h] += solape
                        h += timedelta(hours=1)
                    ent = None
        print("\n== Permanencia y llegadas")
        print(f"  estancia (h): mediana={st.median(estancias):.1f} p25={pct(estancias, .25):.1f} "
              f"p75={pct(estancias, .75):.1f}  (n={len(estancias)})")
        print(f"  tiempo promedio fuera entre visitas (h): media={st.mean(fuera):.1f} mediana={st.median(fuera):.1f}")
        hist = defaultdict(int)
        for h in horas_ent:
            hist[int(h) // 3 * 3] += 1
        print("  entradas por franja de 3 h: " + ", ".join(f"{k:02d}-{k + 3:02d}h={100 * v / len(horas_ent):.0f}%"
                                                          for k, v in sorted(hist.items())))

        # ---- ocupacion media por dia de semana y franja (sobre capacidad total)
        cur.execute("SELECT count(*) FROM cupo WHERE parqueadero_id=1 AND activo")
        cap = cur.fetchone()[0]
        if ocup and a:
            h0 = a.astimezone(nucleo.TZ).replace(hour=0, minute=0, second=0, microsecond=0)
            h1 = b.astimezone(nucleo.TZ)
            suma, cuenta = defaultdict(float), defaultdict(int)
            h = h0
            while h <= h1:
                for i, (x, y) in enumerate(FRANJAS):
                    if x <= h.hour < y:
                        suma[(h.weekday(), i)] += ocup.get(h, 0.0)
                        cuenta[(h.weekday(), i)] += 1
                h += timedelta(hours=1)
            print(f"\n== Ocupacion media por dia y franja (% de {cap} cupos; vehiculos simulados dentro)")
            print("       " + "".join(f"{x:02d}-{y:02d}h ".rjust(9) for x, y in FRANJAS))
            for dsem in range(7):
                print(f"  {DIAS[dsem]}  " + "".join(f"{100 * suma[(dsem, i)] / max(cuenta[(dsem, i)], 1) / cap:8.0f}%"
                                                  for i in range(4)))

        # ---- eventos denegados
        cur.execute("SELECT resultado, coalesce(motivo,'-'), count(*) FROM evento_acceso WHERE simulado "
                    "AND (resultado='DENEGADO' OR motivo IS NOT NULL) GROUP BY 1,2 ORDER BY 1,2")
        print("\n== Eventos con motivo: " + "; ".join(f"{r}/{m}={n}" for r, m, n in cur.fetchall()))

        # ---- chequeos
        print("\n== Chequeos de integridad")
        chequeo("eventos PERMITIDOS alternan ENTRADA/SALIDA (empiezan en ENTRADA, tiempos crecientes)", alternancia_ok)
        cur.execute("SELECT count(*) FROM evento_acceso WHERE simulado AND ocurrido_en > now()")
        chequeo("sin eventos en el futuro", cur.fetchone()[0] == 0)
        cur.execute("""SELECT count(*) FROM (
                         SELECT usuario_id, periodo_inicio, periodo_fin,
                                lag(periodo_fin) OVER (PARTITION BY usuario_id ORDER BY periodo_inicio) AS ant
                         FROM pago WHERE simulado AND estado='APROBADO') x
                       WHERE ant IS NOT NULL AND periodo_inicio <= ant""")
        chequeo("sin solapes de periodos aprobados por usuario", cur.fetchone()[0] == 0)
        cur.execute("""SELECT count(*) FROM (
                         SELECT periodo_inicio, lag(periodo_fin) OVER (PARTITION BY usuario_id ORDER BY periodo_inicio) AS ant
                         FROM pago WHERE simulado AND estado='APROBADO') x
                       WHERE ant IS NOT NULL AND periodo_inicio <> ant + 1""")
        chequeo("periodos aprobados continuos (sin huecos)", cur.fetchone()[0] == 0)
        cur.execute("""SELECT count(*) FROM pago p JOIN tarifa t ON t.id = p.tarifa_id JOIN usuario u ON u.id = p.usuario_id
                       WHERE p.simulado AND p.monto_esperado <> t.valor_mensual""")
        chequeo("monto = tarifa", cur.fetchone()[0] == 0)
        # cupo unico por fecha: usuarios simulados vigentes por tipo y dia <= cupos de ese tipo
        cur.execute("""SELECT v.tipo_vehiculo, (u.creado_en AT TIME ZONE 'America/Bogota')::date,
                              (u.dado_de_baja_en AT TIME ZONE 'America/Bogota')::date
                       FROM usuario u JOIN vehiculo v ON v.usuario_id = u.id WHERE u.simulado""")
        filas = cur.fetchall()
        cur.execute("SELECT tipo_vehiculo, count(*) FROM cupo WHERE activo GROUP BY 1")
        capt = dict(cur.fetchall())
        peor = 0
        if filas:
            d = min(f[1] for f in filas)
            while d <= hasta:
                for tipo, cp in capt.items():
                    n = sum(1 for t, i, bj in filas if t == tipo and i <= d and (bj is None or d < bj))
                    peor = max(peor, n - cp)
                d += timedelta(days=1)
        chequeo("cupo unico por fecha (nunca mas usuarios vigentes que cupos por tipo)", peor <= 0)
        cur.execute("""SELECT count(*) - count(DISTINCT cupo_id) FROM usuario WHERE simulado AND cupo_id IS NOT NULL""")
        chequeo("sin cupo_id repetido entre simulados vigentes", cur.fetchone()[0] == 0)
        cur.execute("""SELECT count(*) FROM usuario u WHERE simulado AND dado_de_baja_en IS NULL AND estado <>
                       CASE WHEN EXISTS (SELECT 1 FROM pago p WHERE p.usuario_id=u.id AND p.estado='APROBADO'
                            AND %s BETWEEN p.periodo_inicio AND p.periodo_fin) THEN 'ACTIVO' ELSE 'VENCIDO' END""", (hasta,))
        chequeo(f"estado del usuario coherente con la cobertura al {hasta}", cur.fetchone()[0] == 0)
        cur.execute("""SELECT count(*) FROM evento_acceso e JOIN usuario u ON u.id = e.usuario_id
                       WHERE e.simulado AND NOT u.simulado AND NOT EXISTS (
                         SELECT 1 FROM pago p WHERE p.usuario_id = u.id AND p.simulado)""")
        chequeo("eventos simulados de usuarios reales solo en historial demo (con pagos simulados)", cur.fetchone()[0] == 0)
        verificar_demo(cur, chequeo)
        cur.execute("SELECT count(*) FROM usuario WHERE simulado AND telefono !~ '^30[0-9]{8}$'")
        chequeo("telefonos simulados con formato ficticio", cur.fetchone()[0] == 0)

    if fallos:
        print(f"\n{len(fallos)} chequeo(s) fallaron.")
        sys.exit(1)
    print("\nTodos los chequeos pasaron.")


if __name__ == "__main__":
    main()
