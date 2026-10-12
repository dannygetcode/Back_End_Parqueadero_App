"""Backtest de origen movil semanal sobre el historico (por defecto el simulado).

Uso (desde pronostico/): python scripts/backtest.py [--real] [--salida metricas.json]
Imprime una tabla de metricas y guarda metricas.json (sin datos personales).
"""
import argparse
import json
import sys
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app import config as cfg  # noqa: E402
from app import db  # noqa: E402
from app import evaluacion as ev  # noqa: E402
from app import ocupacion as oc  # noqa: E402


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--real", action="store_true", help="usar datos reales en vez de los simulados")
    ap.add_argument("--salida", default=str(Path(__file__).resolve().parent.parent / "metricas.json"))
    args = ap.parse_args()
    simulados = not args.real
    ahora = datetime.now(cfg.TZ).replace(tzinfo=None)

    conn = db.conectar()
    try:
        datos = db.cargar(conn, simulados)
    finally:
        conn.close()

    visitas = oc.construir_visitas(datos.eventos)
    capacidad = sum(1 for _, t in datos.cupos if t == "CARRO")
    res = {"generadoEn": datetime.now(cfg.TZ).isoformat(timespec="seconds"), "versionModelo": cfg.VERSION_MODELO,
           "origenDatos": "simulado" if simulados else "real"}
    if visitas:
        primero = min(v.entrada for v in visitas).replace(hour=0, minute=0, second=0, microsecond=0)
        n = int((ahora.replace(minute=0, second=0, microsecond=0) - primero).total_seconds() // 3600) + 1
        serie = oc.serie_ocupacion(visitas, "CARRO", primero, n, ahora)
        res["ocupacion"] = ev.backtest_ocupacion(serie, primero, capacidad, cfg.VENTANA_SEMANAS_OCUPACION)
    res["vacancia"] = ev.backtest_vacancia(datos.pagos, ahora.date(), cfg.GRACIA_DIAS, cfg.PRIOR_MEDIA,
                                           cfg.PRIOR_PESO)
    res["llegadas"] = {"validado": False, "nota": "sin backtest: se reporta la variabilidad (p25-p75) y la "
                                                  "probabilidad de venir por usuario"}

    Path(args.salida).write_text(json.dumps(res, indent=2, ensure_ascii=False), encoding="utf-8")
    imprimir(res)


def imprimir(res):
    print(f"Backtest ({res['origenDatos']}) - {res['generadoEn']}\n")
    o = res.get("ocupacion", {})
    if o.get("n_horas"):
        print(f"OCUPACION carros por hora ({o['n_origenes']} origenes semanales, {o['n_horas']} horas)")
        print(f"  MAE perfil estacional (mediana)      : {o['mae_modelo']}")
        print(f"  MAE perfil estacional (media)        : {o['mae_media']}")
        print(f"  MAE baseline misma hora semana pasada: {o['mae_baseline_semana_pasada']}")
        gana = o["mae_modelo"] < o["mae_baseline_semana_pasada"]
        print(f"  -> {'el modelo GANA' if gana else 'el modelo NO gana'} al baseline "
              f"(mejora relativa {o['mejora_relativa_vs_baseline']:+.1%})")
        print(f"  Cobertura real del intervalo p10-p90 : {o['cobertura_p10_p90']:.1%} (nominal 80%)")
        print(f"  Brier 'carros llenos' modelo/baseline: {o['brier_lleno_modelo']} / {o['brier_lleno_baseline']} "
              f"(frecuencia observada {o['frecuencia_lleno_observada']:.1%})")
    v = res["vacancia"]
    print()
    if v.get("n"):
        print(f"VACANCIA: renovacion a tiempo en {v['horizonte_dias']} dias (n={v['n']} renovaciones evaluadas, "
              f"tasa observada {v['tasa_renovacion_observada']:.1%})")
        print(f"  Brier   modelo / tasa constante: {v['brier_modelo']} / {v['brier_baseline_tasa_constante']}")
        print(f"  LogLoss modelo / tasa constante: {v['logloss_modelo']} / {v['logloss_baseline_tasa_constante']}")
        gana = v["brier_modelo"] < v["brier_baseline_tasa_constante"]
        print(f"  -> {'el modelo gana' if gana else 'el modelo NO gana'} en Brier; con n={v['n']} la diferencia "
              f"no es estadisticamente concluyente")
    else:
        print("VACANCIA: sin renovaciones evaluables")


if __name__ == "__main__":
    main()
