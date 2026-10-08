import threading
import time
from datetime import date, datetime, timedelta

from app import llegadas as lg
from app import servicio
from app.cache import CacheTTL
from app.modelos import Datos, Visita


def _datos(dias, ahora):
    """Un carro que entra 08:00 y sale 17:00 cada uno de los ultimos `dias` dias."""
    d = Datos(cupos=[("C1", "CARRO"), ("C2", "CARRO")], usuarios=[(1, "ACTIVO", "C1", "CARRO")])
    for k in range(dias, 0, -1):
        dia = (ahora - timedelta(days=k)).replace(hour=0, minute=0, second=0, microsecond=0)
        d.eventos += [(1, 1, "CARRO", "ENTRADA", dia + timedelta(hours=8)),
                      (1, 1, "CARRO", "SALIDA", dia + timedelta(hours=17))]
    return d


AHORA = datetime(2026, 3, 11, 10, 0)


def test_ocupacion_historia_corta_es_insuficiente_sin_valores():
    r = servicio.respuesta_ocupacion(_datos(2, AHORA), AHORA, False, 2)
    assert r["datosInsuficientes"] is True
    horas = [h for d in r["dias"] for h in d["horas"]]
    assert horas and all(h["suficiente"] is False for h in horas)
    assert all(h[c] is None for h in horas for c in ("mediana", "media", "p10", "p90", "probLleno"))


def test_ocupacion_larga_marca_horas_con_pocas_observaciones():
    r = servicio.respuesta_ocupacion(_datos(60, AHORA), AHORA, True, 1)
    assert r["datosInsuficientes"] is False
    h8 = r["dias"][0]["horas"][10]
    assert h8["suficiente"] is True and h8["mediana"] == 1.0 and h8["observaciones"] >= 3
    assert all(h["suficiente"] == (h["observaciones"] >= 3) for h in r["dias"][0]["horas"])


def test_llegadas_dias_observados_solo_con_datos_reales():
    d = _datos(2, AHORA)
    r = servicio.respuesta_llegadas(d, AHORA, False)
    p = r["usuarios"][0]["proxima"]
    assert p is None   # 1-2 dias de historia: ningun dia de semana alcanza 3 observaciones
    d = _datos(30, AHORA)
    p = servicio.respuesta_llegadas(d, AHORA, False)["usuarios"][0]["proxima"]
    assert p["diasObservados"] <= 5 and p["probabilidadVenir"] is not None


def test_estadistica_dia_desde_recorta_ventana():
    est = lg.estadistica_dia({}, date(2026, 3, 9), date(2026, 3, 8), 8, desde=date(2026, 3, 8))
    assert est["diasObservados"] == 0 and est["probabilidadVenir"] is None


def test_cache_single_flight():
    c = CacheTTL(60)
    llamadas = []

    def fabrica():
        llamadas.append(1)
        time.sleep(0.2)
        return 42

    res = []
    hilos = [threading.Thread(target=lambda: res.append(c.obtener("k", fabrica))) for _ in range(8)]
    [t.start() for t in hilos]
    [t.join() for t in hilos]
    assert res == [42] * 8 and len(llamadas) == 1


def test_cache_single_flight_no_guarda_fallos():
    c = CacheTTL(60)

    def mala():
        raise RuntimeError("x")

    for _ in range(2):
        try:
            c.obtener("k", mala)
        except RuntimeError:
            pass
    assert c.obtener("k", lambda: 7) == 7
