import random
import re
import sys
from datetime import date, datetime, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import nucleo  # noqa: E402

TARIFAS = [
    dict(id=1, tipo="CARRO", valor=64000, desde=date(2025, 1, 1)),
    dict(id=2, tipo="CARRO", valor=80000, desde=date(2026, 1, 1)),
    dict(id=3, tipo="MOTO", valor=8000, desde=date(2025, 1, 1)),
    dict(id=4, tipo="MOTO", valor=10000, desde=date(2026, 1, 1)),
]
CUPOS = [dict(codigo=f"C{i}", tipo="CARRO") for i in range(1, 7)] + [dict(codigo="M1", tipo="MOTO")]
AHORA = datetime(2026, 10, 8, 12, 0, tzinfo=nucleo.TZ)


def plan(semilla=2025):
    return nucleo.planificar(semilla, 12, date(2026, 10, 8), TARIFAS, CUPOS, ahora=AHORA)


def test_placas_formato_y_unicas():
    rng = random.Random(1)
    usadas = set()
    for _ in range(200):
        assert re.fullmatch(r"[A-Z]{3}\d{3}", nucleo.placa_unica(rng, "CARRO", usadas))
        assert re.fullmatch(r"[A-Z]{3}\d{2}[A-Z]", nucleo.placa_unica(rng, "MOTO", usadas))
    assert len(usadas) == 400


def test_periodos_continuos_y_fin_de_mes():
    ancla = date(2025, 1, 28)
    for k in range(14):
        _, fin = nucleo.periodo(ancla, k)
        assert nucleo.periodo(ancla, k + 1)[0] == fin + timedelta(days=1)
    assert nucleo.add_months(date(2025, 1, 31), 1) == date(2025, 2, 28)


def test_tarifa_vigente_cambia_en_2026():
    assert nucleo.tarifa_vigente(TARIFAS, "CARRO", date(2025, 12, 31))["valor"] == 64000
    assert nucleo.tarifa_vigente(TARIFAS, "MOTO", date(2026, 1, 1))["valor"] == 10000


def test_eventos_alternan_y_corte():
    rng = random.Random(3)
    for perfil in nucleo.PERFILES_USO:
        v = nucleo.generar_visitas(rng, perfil, date(2025, 11, 1), date(2025, 12, 31), [])
        ev = nucleo.eventos_desde_visitas(v, nucleo.a_dt(date(2025, 12, 20), 12))
        assert ev and nucleo.alternancia_valida(ev)
    assert not nucleo.alternancia_valida([("SALIDA", AHORA)])
    assert not nucleo.alternancia_valida([("ENTRADA", AHORA), ("ENTRADA", AHORA + timedelta(hours=1))])


def test_reproducible_con_misma_semilla_y_distinto_con_otra():
    def firma(p):
        return [(u.placa, u.baja, len(u.pagos), [(e.tipo, e.ocurrido_en) for e in u.eventos]) for u in p.usuarios]
    assert firma(plan(7)) == firma(plan(7))
    assert firma(plan(7)) != firma(plan(8))


def test_plan_coherente():
    p = plan()
    assert 7 <= len(p.usuarios) <= 11
    assert len({u.placa for u in p.usuarios}) == len(p.usuarios)
    assert sum(1 for u in p.usuarios if u.baja) >= 3
    for u in p.usuarios:
        assert nucleo.alternancia_valida(
            [(e.tipo, e.ocurrido_en) for e in u.eventos if e.resultado == "PERMITIDO"])
        ap = sorted((x for x in u.pagos if x.estado == "APROBADO"), key=lambda x: x.periodo_inicio)
        for a, b in zip(ap, ap[1:]):
            assert b.periodo_inicio == a.periodo_fin + timedelta(days=1)
        assert all(x.monto == nucleo.tarifa_vigente(TARIFAS, u.tipo, x.periodo_inicio)["valor"] for x in u.pagos)


# ---------- historial demo ----------
def test_periodos_hacia_atras_continuos_y_terminan_antes_del_real():
    real = date(2026, 10, 14)
    ps = nucleo.periodos_hacia_atras(real, 12)
    assert len(ps) == 12 and ps[-1][1] == real - timedelta(days=1)
    assert ps[0][0] == date(2025, 10, 14)
    for (_, fin), (ini, _) in zip(ps, ps[1:]):
        assert ini == fin + timedelta(days=1)
    # dia 31: los limites se ajustan pero siguen sin hueco ni solape
    ps = nucleo.periodos_hacia_atras(date(2026, 3, 31), 6)
    assert all(b[0] == a[1] + timedelta(days=1) for a, b in zip(ps, ps[1:]))


def test_tarifa_por_fecha_en_el_cambio_de_anio():
    assert nucleo.tarifa_vigente(TARIFAS, "CARRO", date(2025, 12, 31))["valor"] == 64000
    assert nucleo.tarifa_vigente(TARIFAS, "CARRO", date(2026, 1, 1))["valor"] == 80000
    ps = nucleo.periodos_hacia_atras(date(2026, 10, 14), 12)
    pagos = nucleo.pagos_demo(random.Random(1), ps, "CARRO", TARIFAS, AHORA)
    for p in pagos:
        assert p.monto == (80000 if p.periodo_inicio >= date(2026, 1, 1) else 64000)
    ap = [p for p in pagos if p.estado == "APROBADO"]
    assert len(ap) == 12 and sum(p.estado == "RECHAZADO" for p in pagos) == 1
    assert all(p.creado_en <= AHORA and p.ruta is None for p in pagos)


def test_eventos_demo_alternan_y_cierran_en_salida_antes_del_tope():
    tope = datetime(2026, 10, 14, 10, 0, tzinfo=nucleo.TZ)
    evs = nucleo.eventos_demo(random.Random(3), date(2025, 10, 14), tope, "ZZZ999")
    assert evs and evs[-1].tipo == "SALIDA" and evs[-1].ocurrido_en < tope
    assert nucleo.alternancia_valida([(e.tipo, e.ocurrido_en) for e in evs])
    assert evs == nucleo.eventos_demo(random.Random(3), date(2025, 10, 14), tope, "ZZZ999")  # determinista


def test_cerrar_en_salida_quita_entrada_final():
    t = datetime(2026, 1, 1, tzinfo=nucleo.TZ)
    ev = [("ENTRADA", t), ("SALIDA", t + timedelta(hours=1)), ("ENTRADA", t + timedelta(hours=2))]
    assert nucleo.cerrar_en_salida(ev) == ev[:2]
