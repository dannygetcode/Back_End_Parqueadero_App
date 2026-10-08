from datetime import date, datetime, timedelta

import numpy as np
import pytest

from app import evaluacion as ev
from app import llegadas as lg
from app import ocupacion as oc
from app import vacancia as vc
from app.cache import CacheTTL
from app.modelos import Datos, Pago, Visita


# ---------- beta-binomial ----------
def test_posterior_sin_datos_es_el_previo():
    assert vc.media_posterior(0, 0, 0.85, 10) == pytest.approx(0.85)
    assert vc.posterior(0, 0, 0.85, 10) == pytest.approx((8.5, 1.5))


def test_posterior_se_contrae_hacia_el_previo():
    # 3 de 3 con peso 10: (8.5+3)/(10+3) = 0.8846, no 1.0
    assert vc.media_posterior(3, 3, 0.85, 10) == pytest.approx(11.5 / 13)
    assert vc.media_posterior(0, 3, 0.85, 10) == pytest.approx(8.5 / 13)
    # muchos datos dominan el previo
    assert vc.media_posterior(90, 100, 0.85, 10) == pytest.approx(98.5 / 110)


def _pago(ini, dias_pago_tras_inicio=0):
    ini = date(2026, 1, 1) + timedelta(days=ini)
    return Pago(ini, ini + timedelta(days=29), ini + timedelta(days=dias_pago_tras_inicio))


def test_observaciones_a_tiempo_tarde_y_final():
    # periodo0 fin = dia 29; el 1 se paga el dia 30+5=35 (<= fin+1+gracia=35) -> a tiempo;
    # el 2 se paga 6 dias tarde -> 0
    pagos = [_pago(0), _pago(30, 5), _pago(60, 6)]
    assert vc.observaciones(pagos, date(2026, 6, 1), 5) == [1, 0]
    # el ultimo periodo vencio hace mucho y no hubo pago nuevo -> cuenta como no renovo solo con incluir_final
    assert vc.observaciones(pagos, date(2026, 6, 1), 5, incluir_final=True) == [1, 0, 0]
    assert vc.observaciones(pagos, date(2026, 3, 20), 5, incluir_final=True) == [1, 0]


# ---------- producto de probabilidades ----------
def test_periodos_hasta():
    L = date(2026, 1, 10)
    assert vc.periodos_hasta(L, date(2026, 1, 9)) == 0
    assert vc.periodos_hasta(L, L) == 1
    assert vc.periodos_hasta(L, L + timedelta(days=29)) == 1
    assert vc.periodos_hasta(L, L + timedelta(days=30)) == 2


def _tit(uid, fin, exitos=10, n=10, estado="ACTIVO"):
    return vc.Titular(uid, f"C{uid}", estado, fin, exitos, n)


def test_prob_libre_producto_exacto_con_pocas_varianzas():
    hoy = date(2026, 3, 1)
    t1, t2 = _tit(1, date(2026, 3, 10)), _tit(2, date(2026, 3, 20))
    # con peso enorme la Beta es casi un punto: p = media del previo
    mu, lo, hi = vc.prob_libre([t1, t2], 2, hoy, 5, 0.9, 1e7, 400, 1)
    d = (date(2026, 3, 18) - hoy).days            # solo t1 liberado (L=15 mar); t2 L=25 mar
    assert mu[0] == pytest.approx(0.0, abs=1e-3)
    assert mu[d] == pytest.approx(1 - 0.9, abs=2e-3)
    d2 = (date(2026, 3, 26) - hoy).days
    assert mu[d2] == pytest.approx(1 - 0.9 * 0.9, abs=2e-3)
    assert np.all((mu >= 0) & (mu <= 1)) and np.all(lo <= mu + 1e-9) and np.all(hi >= mu - 1e-9)


def test_cupo_sin_asignar_da_probabilidad_uno():
    mu, _, _ = vc.prob_libre([_tit(1, date(2026, 3, 10))], 2, date(2026, 3, 1), 5, 0.85, 10, 100, 1)
    assert np.all(mu == 1.0)


def test_vencido_se_libera_hoy_y_primera_fecha():
    hoy = date(2026, 3, 1)
    t = _tit(1, date(2026, 2, 1), estado="VENCIDO")
    assert vc.liberacion(t, hoy, 5) == hoy
    mu, _, _ = vc.prob_libre([t], 1, hoy, 5, 0.85, 10, 500, 1)
    assert mu[0] == pytest.approx(1 - 18.5 / 20, abs=0.02)   # 1 - E[p]; 10/10 renovaciones
    assert vc.primera_fecha(np.array([0.1, 0.4, 0.6]), hoy) == hoy + timedelta(days=2)
    assert vc.primera_fecha(np.array([0.1, 0.2]), hoy) is None


def test_reproducible_con_semilla():
    hoy = date(2026, 3, 1)
    tit = [_tit(1, date(2026, 3, 10), 8, 10), _tit(2, date(2026, 3, 20), 5, 6)]
    a = vc.prob_libre(tit, 2, hoy, 5, 0.85, 10, 300, 7)
    b = vc.prob_libre(tit, 2, hoy, 5, 0.85, 10, 300, 7)
    assert all(np.array_equal(x, y) for x, y in zip(a, b))


def test_pronosticar_estructura_sin_pii():
    d = Datos(cupos=[("C1", "CARRO"), ("M1", "MOTO")], usuarios=[(1, "ACTIVO", "C1", "CARRO")],
              pagos={1: [_pago(0), _pago(30)]})
    r = vc.pronosticar(d, date(2026, 1, 20), 5, 0.85, 10, 200, 1)
    assert r["MOTO"]["cuposSinAsignar"] == 1 and r["MOTO"]["primeraFechaProbMayorIgual50"] == "2026-01-20"
    assert set(r["CARRO"]["detalle"][0]) == {"usuarioId", "cupo", "estado", "fechaFin", "fechaLiberacionPotencial",
                                             "pRenovar", "renovacionesObservadas", "renovacionesATiempo"}


# ---------- perfil estacional ----------
BASE = datetime(2026, 1, 5)       # lunes 00:00


def test_serie_y_perfil_estacional():
    # un carro entra los lunes 08:00 y sale 17:00 durante 4 semanas
    vis = [Visita(1, 1, "CARRO", BASE + timedelta(weeks=w, hours=8), BASE + timedelta(weeks=w, hours=17))
           for w in range(4)]
    n = 4 * 168
    serie = oc.serie_ocupacion(vis, "CARRO", BASE, n, BASE + timedelta(weeks=4))
    assert serie[8] == 1 and serie[16] == 1 and serie[17] == 0 and serie[7] == 0
    perfil = oc.perfil_estacional(serie, BASE, n)
    est = oc.estadisticos(perfil, 6)
    assert est["n"][8] == 4 and est["media"][8] == 1.0 and est["media"][9 + 24] == 0.0
    assert est["pLleno"][8] == pytest.approx(0.5 / 5)       # Jeffreys con 0 de 4
    assert oc.clave(datetime(2026, 1, 6, 9)) == 24 + 9


def test_perfil_con_base_no_lunes_y_ventana():
    base = datetime(2026, 1, 7)  # miercoles
    serie = np.arange(3 * 168, dtype=float)
    perfil = oc.perfil_estacional(serie, base, len(serie))
    assert all(len(v) == 3 for v in perfil)
    # clave del miercoles 00:00 = 2*24 -> valores 0, 168, 336
    assert list(perfil[48]) == [0, 168, 336]
    p2 = oc.perfil_estacional(serie, base, len(serie), ventana_semanas=2)
    assert all(len(v) == 2 for v in p2) and list(p2[48]) == [168, 336]


def test_construir_visitas_empareja_y_ignora_ruido():
    t = BASE
    ev_ = [(1, 9, "CARRO", "ENTRADA", t), (1, 9, "CARRO", "ENTRADA", t + timedelta(hours=1)),
           (1, 9, "CARRO", "SALIDA", t + timedelta(hours=5)), (1, 9, "CARRO", "SALIDA", t + timedelta(hours=6)),
           (1, 9, "CARRO", "ENTRADA", t + timedelta(hours=24))]
    v = oc.construir_visitas(ev_)
    assert len(v) == 2 and v[0].salida == t + timedelta(hours=5) and v[1].salida is None


def test_backtest_ocupacion_periodico_perfecto():
    serie = np.tile(np.r_[np.zeros(100), np.ones(68)], 12)
    r = ev.backtest_ocupacion(serie, BASE, 6, None)
    assert r["mae_modelo"] == 0 and r["mae_baseline_semana_pasada"] == 0 and r["cobertura_p10_p90"] == 1.0


# ---------- hora de llegada ----------
def _visitas_lunes(horas, semanas_atras):
    hoy_lunes = datetime(2026, 3, 9)
    return [Visita(1, 1, "CARRO", hoy_lunes - timedelta(weeks=w, hours=-h), hoy_lunes - timedelta(weeks=w, hours=-h - 8))
            for w, h in zip(semanas_atras, horas)]


def test_hora_llegada_mediana_y_cuartiles():
    # ahora = domingo 8 mar 23:00 -> el proximo lunes es manana 9 mar
    vis = _visitas_lunes([7, 7.5, 8, 8.5, 9], [1, 2, 3, 4, 5])
    r = lg.proxima_llegada(vis, datetime(2026, 3, 8, 23, 0))["proxima"]
    assert r["fecha"] == "2026-03-09" and r["diaSemana"] == "lunes"
    assert r["horaMediana"] == "08:00" and r["horaP25"] == "07:30" and r["horaP75"] == "08:30"
    assert r["diasConEntrada"] == 5 and r["probabilidadVenir"] == pytest.approx(5.5 / 9, abs=1e-3)
    assert r["diasObservados"] == 8
    assert r["alcanzaUmbral50"] is True


def test_pocos_datos_no_inventa_hora():
    vis = _visitas_lunes([8, 9], [1, 2])
    est = lg.estadistica_dia(lg.primeras_entradas(vis), date(2026, 3, 9), date(2026, 3, 8), 8)
    assert est["datosInsuficientes"] is True and est["horaMediana"] is None


def test_hoy_se_salta_si_ya_entro_o_paso_la_hora():
    vis = _visitas_lunes([8, 8, 8, 8], [1, 2, 3, 4]) + [Visita(1, 1, "CARRO", datetime(2026, 3, 9, 8), None)]
    r = lg.proxima_llegada(vis, datetime(2026, 3, 9, 12, 0))["proxima"]
    assert r is None or r["fecha"] != "2026-03-09"


def test_tiempo_fuera_mediana():
    v = [Visita(1, 1, "CARRO", datetime(2026, 3, 1, 8), datetime(2026, 3, 1, 17)),
         Visita(1, 1, "CARRO", datetime(2026, 3, 2, 8), datetime(2026, 3, 2, 17)),
         Visita(1, 1, "CARRO", datetime(2026, 3, 3, 10), None)]
    med, n = lg.tiempo_fuera_horas(v, datetime(2026, 3, 4))
    assert n == 2 and med == pytest.approx((15 + 17) / 2)
    assert lg.hhmm(7.5) == "07:30"


# ---------- cache ----------
def test_cache_ttl():
    t = [0.0]
    cache = CacheTTL(100, reloj=lambda: t[0])
    llamadas = []
    f = lambda: llamadas.append(1) or len(llamadas)  # noqa: E731
    assert cache.obtener("k", f) == 1 and cache.obtener("k", f) == 1
    assert cache.obtener("otra", f) == 2
    t[0] = 99.9
    assert cache.obtener("k", f) == 1
    t[0] = 100.1
    assert cache.obtener("k", f) == 3
    cache.vaciar()
    assert cache.obtener("k", f) == 4


# ---------- backtest vacancia ----------
def test_backtest_vacancia_usuario_fiel_y_baja():
    fiel = [_pago(30 * k, 0) for k in range(8)]
    se_va = [_pago(30 * k, 0) for k in range(3)]
    r = ev.backtest_vacancia({1: fiel, 2: se_va}, date(2026, 1, 1) + timedelta(days=400), 5, 0.85, 10)
    assert r["n"] > 0 and 0 <= r["brier_modelo"] <= 1 and 0 <= r["tasa_renovacion_observada"] <= 1
