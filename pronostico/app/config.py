"""Configuracion por variables de entorno. Los valores por defecto replican los del backend Java."""
import os
from datetime import timedelta, timezone

# Colombia no tiene horario de verano: un desfase fijo evita depender de tzdata.
TZ = timezone(timedelta(hours=-5), "America/Bogota")
VERSION_MODELO = "pronostico-1.0.0"

# Mismo valor que usuarios.vencimiento.dias-gracia (application.properties, ADR 0006).
GRACIA_DIAS = int(os.environ.get("GRACIA_DIAS", "5"))
# Previo Beta de la probabilidad de renovar: media y "numero de observaciones equivalentes".
PRIOR_MEDIA = float(os.environ.get("PRIOR_MEDIA", "0.85"))
PRIOR_PESO = float(os.environ.get("PRIOR_PESO", "10"))
CACHE_HORAS = float(os.environ.get("CACHE_HORAS", "3"))
VENTANA_SEMANAS_OCUPACION = int(os.environ.get("VENTANA_SEMANAS_OCUPACION", "12"))
SEMANAS_LLEGADA = 8
# Umbrales de suficiencia de datos: por debajo, el pronostico se marca como insuficiente y no se inventan valores.
MIN_SEMANAS_OCUPACION = float(os.environ.get("MIN_SEMANAS_OCUPACION", "4"))
MIN_OBS_HORA = int(os.environ.get("MIN_OBS_HORA", "3"))
MIN_DIAS_LLEGADA = 3
MUESTRAS_MC = 2000
SEMILLA = 2025
HORIZONTE_MAX_DIAS = 14
METRICAS_RUTA = os.environ.get(
    "METRICAS_RUTA", os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "metricas.json"))
