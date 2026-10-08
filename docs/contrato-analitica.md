# Contrato de analítica (Fase 4, paso 1)

Para quien programa el panel. Todos los endpoints son `GET`, de solo lectura, y devuelven JSON.

## Convenciones

- Prefijo `/api`. Cabecera `Authorization: Bearer <JWT>`. Sin token responde 401; con un rol incorrecto, 403.
- Rol: ADMIN en `/api/analitica/**`; USUARIO en `/api/usuarios/yo/resumen` (siempre sobre el usuario del token).
- `incluirSimulados` (boolean, por omisión `false`): con `false` solo cuentan datos reales; con `true` se suman los
  12 meses de datos simulados. El panel de la demo debe enviarlo en `true`. Los listados operativos no cambian.
- Fechas ISO-8601. Los instantes salen con el desfase de `America/Bogota` (`-05:00`); los parámetros `desde`,
  `hasta` son fechas `YYYY-MM-DD` inclusivas en esa zona. Los campos `zonaHoraria` lo repiten.
- Montos en COP enteros. Series ordenadas de forma ascendente por fecha.
- Rangos (`desde`/`hasta`): por omisión `hasta` = hoy y `desde` = `hasta - 6 días`. Más de 400 días, `desde > hasta`,
  una fecha mal escrita o una granularidad inválida responden 400 (`ProblemDetail`).
- Privacidad: no hay teléfonos, nombres ni placas; solo `usuarioId` y código de cupo donde se indica. La ocupación
  nunca se cruza con la placa.
- Censura: una ENTRADA permitida sin SALIDA dentro de 24 h se marca `censurada`; su ocupación se corta a las 24 h
  (o a la siguiente entrada del mismo vehículo) y no cuenta como estancia. Si es reciente y no hay más eventos,
  el vehículo sigue dentro hasta ahora y no es censurado. Solo se usan eventos con `resultado = PERMITIDO`.

## GET /api/analitica/resumen

Parámetros: `incluirSimulados`. KPIs del momento. `porcentajeContratado` = cupos con usuario ACTIVO / cupos totales.
`dentroAhora` cuenta vehículos cuyo último evento permitido es una ENTRADA de las últimas 24 h. Los ingresos del mes
suman pagos APROBADOS con monto mayor que 0, por fecha de registro del pago; `variacionPct` es nulo si el mes anterior
fue 0 y el mes actual es parcial. `vencimientos` lista usuarios ACTIVO cuyo último periodo aprobado termina en los
próximos 30 días (`en7Dias` y `en15Dias` son subconjuntos).

```json
{
  "generadoEn": "2026-10-08T17:30:35.705-05:00",
  "incluirSimulados": true,
  "cupos": [
    {"tipo": "CARRO", "totales": 6, "asignados": 6, "vigentes": 6, "porcentajeContratado": 100.0},
    {"tipo": "MOTO", "totales": 1, "asignados": 1, "vigentes": 1, "porcentajeContratado": 100.0}
  ],
  "dentroAhora": {"carro": 2, "moto": 0},
  "usuarios": {"activo": 9, "vencido": 2, "suspendido": 0},
  "ingresos": {"mesActual": 160000, "mesAnterior": 410000, "variacionPct": -61.0},
  "pagosPendientes": 0,
  "vencimientos": {
    "en7Dias": 1, "en15Dias": 4, "en30Dias": 9,
    "lista": [{"usuarioId": 30, "cupo": "C4", "fechaFin": "2026-10-10", "diasRestantes": 2}]
  }
}
```

## GET /api/analitica/ocupacion

Parámetros: `desde`, `hasta`, `granularidad=hora|dia` (por omisión `hora`), `incluirSimulados`. (RF-51)
Una cubeta por hora o por día de Bogotá. `carro`/`moto` son la ocupación media de la cubeta (vehículos dentro,
ponderada por tiempo, 2 decimales); `picoCarro`/`picoMoto` el máximo simultáneo; `censuradas` cuántos intervalos
censurados la tocan.

```json
{
  "desde": "2026-10-06", "hasta": "2026-10-07", "granularidad": "hora", "zonaHoraria": "America/Bogota",
  "incluirSimulados": true,
  "serie": [
    {"inicio": "2026-10-06T08:00:00-05:00", "carro": 2.35, "moto": 0.5, "picoCarro": 3, "picoMoto": 1, "censuradas": 0}
  ]
}
```

## GET /api/analitica/ocupacion/mapa-calor

Parámetros: `desde`, `hasta`, `incluirSimulados`. Matriz día de la semana por hora (hasta 168 celdas; se omiten las
celdas sin muestras). `diaSemana` ISO: 1 = lunes ... 7 = domingo. Los valores son el promedio de las cubetas
horarias de esa celda dentro del rango. `probabilidadLlenoCarro` = fracción de cubetas (`muestras`) en que los
carros dentro alcanzaron `cuposCarro` (cupos de CARRO activos); es nula si no hay cupos. `usuariosDistintos` cuenta
usuarios que estuvieron dentro en esa celda (solo el número).

```json
{
  "desde": "2025-09-10", "hasta": "2026-10-08", "zonaHoraria": "America/Bogota", "incluirSimulados": true,
  "cuposCarro": 6,
  "celdas": [
    {"diaSemana": 1, "hora": 9, "ocupacionMediaCarro": 2.4, "ocupacionMediaMoto": 0.3,
     "probabilidadLlenoCarro": 0.0, "usuariosDistintos": 7, "muestras": 56}
  ]
}
```

## GET /api/analitica/ingresos

Parámetros: `anio` (por omisión el actual; 2000 a 2200), `incluirSimulados`. (RF-52) Suma de `monto_confirmado` de
pagos APROBADOS con monto mayor que 0 (las cortesías de 0 no cuentan), por mes de registro del pago y tipo de
vehículo. Siempre 12 meses (con ceros). `tarifaVigente` es la tarifa mensual del tipo el día 1 del mes, para anotar
el salto de tarifa de 2026 (carro 64.000 a 80.000, moto 8.000 a 10.000). `acumulado` es el total acumulado hasta ese
mes. `tarifas` lista las tarifas que rigieron en el año.

```json
{
  "anio": 2026, "incluirSimulados": true,
  "meses": [
    {"mes": 1,
     "carro": {"ingresos": 480000, "pagos": 6, "ticketPromedio": 80000, "tarifaVigente": 80000},
     "moto": {"ingresos": 10000, "pagos": 1, "ticketPromedio": 10000, "tarifaVigente": 10000},
     "total": 490000, "pagos": 7, "ticketPromedio": 70000, "acumulado": 490000}
  ],
  "totalAnual": {"carro": 4160000, "moto": 90000, "total": 4250000, "pagos": 61, "ticketPromedio": 69672},
  "tarifas": [
    {"tipo": "CARRO", "valorMensual": 80000, "vigenteDesde": "2026-01-01"},
    {"tipo": "MOTO", "valorMensual": 10000, "vigenteDesde": "2026-01-01"}
  ]
}
```

## GET /api/analitica/morosidad

Parámetros: `incluirSimulados`. (RF-53) `usuarios`: VENCIDO con al menos un pago aprobado, ordenados por mayor mora.
`diasMora` = hoy - fin del último periodo aprobado; `mesesAdeudados` = máx(1, techo(diasMora / 30));
`montoAdeudado` = meses por tarifa vigente hoy del tipo del cupo. Los VENCIDO que nunca pagaron se cuentan en
`vencidosSinPagoPrevio`. `atraso` (últimos 12 meses, pagos aprobados con monto mayor que 0): un pago es tardío si se
registró después del inicio de su periodo; `medianaDiasAtraso` y `p90DiasAtraso` se calculan solo entre los
tardíos. `renovacionMensual`: por mes de fin de periodo, usuarios que vencían (se omiten los que aún están dentro de
la gracia) y cuántos pagaron el periodo siguiente a más tardar `diasGracia` días después; `tasa` es nula sin base.
Cubre los últimos 6 meses.

```json
{
  "fecha": "2026-10-08", "incluirSimulados": true,
  "usuarios": [{"usuarioId": 33, "cupo": "C6", "tipo": "CARRO", "vencioEl": "2026-08-20", "diasMora": 49,
                "mesesAdeudados": 2, "montoAdeudado": 160000}],
  "totalAdeudado": 160000, "vencidosSinPagoPrevio": 1,
  "atraso": {"ventanaMeses": 12, "pagos": 81, "tardios": 5, "porcentajeTardios": 6.2,
             "medianaDiasAtraso": 2.0, "p90DiasAtraso": 6.4, "diasGracia": 5,
             "renovacionMensual": [{"mes": "2026-09", "vencian": 7, "renovaron": 6, "tasa": 0.857}]}
}
```

## GET /api/analitica/permanencia

Parámetros: `desde`, `hasta`, `incluirSimulados`. Minutos con 1 decimal. `estanciaMin` usa visitas completas (sin
censuradas) con entrada en el rango; `fueraMin` es el tiempo entre una salida y la siguiente entrada del mismo
vehículo (por día de la salida). `porTipo` tiene una fila por tipo; `porTipoYDia` una por tipo y día ISO de la
entrada (`diaSemana` 1 a 7), omitiendo las vacías. `histogramaHoraEntrada` siempre trae 24 elementos e incluye las
entradas censuradas.

```json
{
  "desde": "2025-09-10", "hasta": "2026-10-08", "zonaHoraria": "America/Bogota", "incluirSimulados": true,
  "porTipo": [{"tipo": "CARRO", "diaSemana": null, "visitas": 1457,
               "estanciaMin": {"mediana": 616.1, "p25": 576.2, "p75": 658.4},
               "fueraMin": {"media": 1331.8, "mediana": 837.4}}],
  "porTipoYDia": [{"tipo": "CARRO", "diaSemana": 1, "visitas": 260,
                   "estanciaMin": {"mediana": 610.0, "p25": 570.0, "p75": 655.0},
                   "fueraMin": {"media": 1200.0, "mediana": 800.0}}],
  "histogramaHoraEntrada": [{"hora": 0, "carro": 3, "moto": 1}]
}
```

## GET /api/usuarios/yo/resumen

Rol USUARIO; sin parámetros (RF-50). `diasRestantes`: días hasta el fin del último periodo aprobado (0 si ya pasó;
nulo si nunca pagó). `totalPagadoAnio`: pagos aprobados del año calendario; `promedioMensualPagado` = total / mes
actual (1 a 12). `visitasMes`: entradas permitidas del mes. `permanenciaPromedioMin` y `horaHabitualLlegada`
(mediana de la hora de entrada, `HH:mm`) usan los últimos 90 días y son nulos sin visitas.

```json
{"estado": "ACTIVO", "diasRestantes": 29, "totalPagadoAnio": 720000, "promedioMensualPagado": 72000,
 "visitasMes": 6, "permanenciaPromedioMin": 612.5, "horaHabitualLlegada": "07:45"}
```

## GET /api/analitica/pronostico (pendiente)

No implementado en este paso (RF-54); lo servirá el servicio Python detrás de Spring (que autentica y reenvía).
Contrato esperado, rol ADMIN: `?horizonteDias=7&incluirSimulados=true`.

```json
{
  "horizonteDias": 7, "generadoEn": "2026-10-08T17:30:00-05:00", "modelo": "pendiente",
  "serie": [{"inicio": "2026-10-09T08:00:00-05:00", "carro": 2.4, "moto": 0.3, "bajo": 1.5, "alto": 3.4}]
}
```
