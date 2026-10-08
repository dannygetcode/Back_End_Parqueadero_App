# Servicio de pronostico del parqueadero

FastAPI + psycopg 3 + numpy. Lee la BD con `SELECT` (transaccion de solo lectura, sin tablas nuevas), calcula bajo demanda y
cachea el resultado en memoria (`CACHE_HORAS`, 3 h por defecto). Es independiente del servicio OCR.

**Seguridad:** escucha en `0.0.0.0:8000` pero **no tiene autenticacion**: solo debe vivir en la red interna de compose
y no publicarse al host. Solo el backend Java le habla (proxy autenticado en `/api/analitica/pronostico/*`, paso aparte).
Las respuestas no incluyen telefonos, nombres ni placas; solo `usuarioId` y codigo de cupo.

## Ejecutar

```bash
pip install -r requirements.txt
# variables: DB_HOST, DB_PORT, POSTGRES_DB, DB_USER, DB_PASSWORD (en desarrollo local tambien se lee el .env del repo)
python scripts/backtest.py                       # escribe metricas.json
python -m uvicorn app.main:app --port 8000       # desarrollo
python -m pytest                                 # sin BD ni Docker
```
Para leer la BD de desarrollo desde el host: `docker compose -f docker-compose.yml -f simulador/compose.publicar-bd.yml up -d db`
y al terminar `docker compose up -d db`. Otras variables: `GRACIA_DIAS` (5, igual que `usuarios.vencimiento.dias-gracia`),
`PRIOR_MEDIA` (0.85), `PRIOR_PESO` (10), `VENTANA_SEMANAS_OCUPACION` (12), `CACHE_HORAS`, `METRICAS_RUTA`.

## Datos simulados

`incluirSimulados=false` (por defecto) usa **solo** filas reales (`simulado=false`); `true` usa **solo** filas simuladas.
Nunca se mezclan (los cupos C1.. se repetirian y los totales no tendrian sentido). `incluyeSimulados` lo confirma en la
respuesta. Las `metricas` salen del backtest, que corre sobre los datos simulados (`origenDatos: "simulado"`), porque los
reales aun no tienen historia suficiente: no son la precision en produccion.

## Contrato

Todas las respuestas llevan `generadoEn` (hora Colombia, ISO-8601), `versionModelo`, `incluyeSimulados` y `metricas`
(`disponible`, `origenDatos`, `generadoEn`, `resumen`). Errores: `503` si no se puede leer la BD, `422` si `horizonteDias`
no esta en 1..14.

### GET /salud
`{"estado":"ok","versionModelo":"pronostico-1.0.0"}`

### GET /pronostico/vacancia?incluirSimulados=true
Probabilidad de que haya **al menos un cupo libre** del tipo en `hoy + dias`, con incertidumbre (`p10`-`p90`) por pocos datos
por usuario. Ejemplo real (CARRO, abreviado):
```json
{"generadoEn":"2026-10-08T17:28:26-05:00","versionModelo":"pronostico-1.0.0","incluyeSimulados":true,
 "metricas":{"disponible":true,"origenDatos":"simulado","resumen":{"n":66,"brier_modelo":0.0692,"brier_baseline_tasa_constante":0.0705,"logloss_modelo":0.2641,"logloss_baseline_tasa_constante":0.2714,"horizonte_dias":30}},
 "parametros":{"graciaDias":5,"priorMedia":0.85,"priorPeso":10.0,"periodoDias":30},
 "porTipo":{"CARRO":{"capacidad":6,"titulares":6,"cuposSinAsignar":0,
   "probabilidades":[{"dias":7,"fecha":"2026-10-15","probabilidad":0.271,"p10":0.133,"p90":0.429},
                     {"dias":30,"fecha":"2026-11-07","probabilidad":0.553,"p10":0.389,"p90":0.715},
                     {"dias":90,"fecha":"2027-01-06","probabilidad":0.854,"p10":0.728,"p90":0.955}],
   "primeraFechaProbMayorIgual50":"2026-11-07",
   "detalle":[{"usuarioId":25,"cupo":"C1","estado":"ACTIVO","fechaFin":"2026-10-23","fechaLiberacionPotencial":"2026-10-28",
               "pRenovar":0.929,"renovacionesObservadas":11,"renovacionesATiempo":11}]}}}
```
(`probabilidades` trae 7, 15, 30, 60 y 90 dias; `porTipo` trae CARRO y MOTO.) Si hay cupos sin titular la probabilidad es 1.

### GET /pronostico/ocupacion?horizonteDias=7&incluirSimulados=true
Carros dentro por hora para los proximos N dias (maximo 14), desde hoy 00:00 hora local. Por hora: `mediana` (pronostico puntual),
`media`, `p10`/`p90` (cuantiles empiricos), `probLleno` (P(carros >= capacidad)) y `observaciones` usadas.
```json
{"generadoEn":"2026-10-08T17:28:37-05:00","versionModelo":"pronostico-1.0.0","incluyeSimulados":true,
 "metricas":{"disponible":true,"origenDatos":"simulado","resumen":{"mae_modelo":0.4801,"mae_baseline_semana_pasada":0.5065,"cobertura_p10_p90":0.8804,"cobertura_nominal":0.8,"n_horas":7224}},
 "tipoVehiculo":"CARRO","capacidad":6,"horizonteDias":1,"datosInsuficientes":false,"semanasHistoria":51.8,"ventanaSemanas":12,
 "dias":[{"fecha":"2026-10-08","horas":[{"hora":8,"mediana":2.0,"media":2.17,"p10":1.0,"p90":3.0,"probLleno":0.038,"observaciones":12}]}]}
```
Sin eventos: `datosInsuficientes:true` y `dias:[]`.

### GET /pronostico/llegadas?incluirSimulados=true
Por usuario con cupo (sin placa): proximo dia con probabilidad de venir >= 0.5 (hoy solo si aun no entro y no paso su hora p75;
si ningun dia llega a 0.5 devuelve el mas probable con `alcanzaUmbral50:false`), hora de la primera entrada (mediana y p25-p75
sobre el mismo dia de semana en las ultimas 8 semanas) y mediana de horas fuera entre visitas (90 dias).
Con menos de 3 dias con entrada: `datosInsuficientes:true` y horas `null`.
```json
{"usuarioId":25,"cupo":"C1","tipoVehiculo":"CARRO","estado":"ACTIVO",
 "proxima":{"fecha":"2026-10-08","diaSemana":"jueves","diasObservados":8,"diasConEntrada":8,"probabilidadVenir":0.944,
            "horaMediana":"20:40","horaP25":"19:56","horaP75":"21:06","datosInsuficientes":false,"alcanzaUmbral50":true},
 "tiempoFueraMedianaHoras":14.0,"visitasConsideradas":89}
```

## Metodo

- **Vacancia.** Por titular, p = P(renueva a tiempo) con beta-binomial contraido: previo Beta(0.85*10, 0.15*10) mas sus
  renovaciones (a tiempo = pago a mas tardar `fin + 1 + gracia`). Cupo liberable en `L = fin + gracia`; si renueva, decide de
  nuevo 30 dias despues, asi que sigue ocupado en `d` con probabilidad `p^m`, `m = 1 + (d-L)//30`. Con independencia,
  `P(>=1 libre) = 1 - prod p_i^m_i`. Titulares no ACTIVOS (VENCIDO/SUSPENDIDO con cupo) cuentan como liberables hoy, con la
  probabilidad de que vuelvan a pagar. `p10`/`p90` salen de 2000 muestras de la Beta posterior (semilla fija).
- **Ocupacion.** Serie de carros dentro a cada hora en punto (visitas = ENTRADA PERMITIDA -> siguiente SALIDA). Perfil por
  (dia de semana, hora) de las ultimas 12 semanas; punto = mediana (minimiza el MAE). `probLleno` con suavizado de Jeffreys.
- **Llegadas.** Estadistica empirica por usuario y dia de semana, ver arriba.

## Backtest (`scripts/backtest.py`, datos simulados, origen movil semanal)

| Modelo | Metrica | Modelo | Baseline | Lectura |
|---|---|---|---|---|
| Ocupacion (43 origenes, 7224 h) | MAE carros/hora | 0.480 | 0.507 (misma hora semana pasada) | gana ~5%, margen pequeno |
| Ocupacion | cobertura p10-p90 | 88.0% | nominal 80% | intervalo algo conservador (variable discreta) |
| Vacancia (n=66, renovo a 30 d) | Brier | 0.0692 | 0.0705 (tasa constante) | empate tecnico |
| Vacancia | log loss | 0.2641 | 0.2714 | empate tecnico |
| Llegadas | - | sin backtest | - | no validado |

Advertencias honestas: la ventana de 12 semanas y la mediana se eligieron mirando este mismo backtest (5 ventanas probadas),
asi que la mejora de 5% esta ligeramente sobreestimada; con la ventana de 26 semanas y la media el modelo **no** gana al ingenuo
(MAE 0.562 vs 0.507). El Brier de "carros llenos" no es informativo: en el historico simulado nunca se llenaron los 6 cupos
(maximo 4). En vacancia, n=66 con 92% de renovaciones no permite distinguir el modelo de una tasa constante.

## Deuda conocida
- La cuenta de BD que usa este servicio todavía no es de solo lectura (hoy comparte las credenciales de la aplicación;
  solo ejecuta SELECT y abre la conexión en modo `read_only`). Pendiente: un rol con permisos únicamente de SELECT.
- Dependencias: `requirements.txt` es producción; las de prueba están en `requirements-dev.txt`.
- La lectura de `RAIZ_REPO/.env` como respaldo local solo ocurre con `PRONOSTICO_LEER_DOTENV=1`.
