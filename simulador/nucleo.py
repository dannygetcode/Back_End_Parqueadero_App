"""Logica pura del simulador (sin base de datos): placas, periodos, visitas y plan completo.

Todo es determinista dado (semilla, meses, hasta, tarifas, cupos). Colombia no tiene horario de verano,
asi que America/Bogota se modela como UTC-5 fijo (sin depender de tzdata en Windows).
"""
from __future__ import annotations

import calendar
import hashlib
import random
import string
from dataclasses import dataclass, field
from datetime import date, datetime, time, timedelta, timezone

TZ = timezone(timedelta(hours=-5), "America/Bogota")

# Festivos nacionales aproximados (2025-2026); fuera de este rango simplemente no hay festivos.
FESTIVOS = {date.fromisoformat(s) for s in (
    "2025-01-01 2025-01-06 2025-03-24 2025-04-17 2025-04-18 2025-05-01 2025-06-02 2025-06-23 2025-06-30 "
    "2025-07-20 2025-08-07 2025-08-18 2025-10-13 2025-11-03 2025-11-17 2025-12-08 2025-12-25 "
    "2026-01-01 2026-01-12 2026-03-23 2026-04-02 2026-04-03 2026-05-01 2026-05-18 2026-06-08 2026-06-15 "
    "2026-06-29 2026-07-20 2026-08-07 2026-08-17 2026-10-12 2026-11-02 2026-11-16 2026-12-08 2026-12-25"
).split()}

NOMBRES = ["Camila", "Andres", "Valentina", "Julian", "Laura", "Sebastian", "Daniela", "Mateo", "Natalia",
           "Felipe", "Paula", "Santiago", "Carolina", "Esteban", "Juliana", "Nicolas", "Mariana", "Diego"]
APELLIDOS = ["Rojas", "Gomez", "Pardo", "Castro", "Mendoza", "Vargas", "Ortiz", "Salazar", "Cardenas",
             "Quintero", "Mora", "Herrera", "Pineda", "Acosta", "Bernal", "Lozano", "Suarez", "Reyes"]
COLORES = ["Blanco", "Negro", "Gris", "Plata", "Rojo", "Azul", "Verde", "Beige", "Vino tinto"]
CARROS = [("Chevrolet Spark GT", "HATCHBACK"), ("Renault Logan", "SEDAN"), ("Mazda 3", "SEDAN"),
          ("Kia Picanto", "HATCHBACK"), ("Renault Duster", "SUV"), ("Toyota Hilux", "PICKUP"),
          ("Chevrolet Tracker", "SUV"), ("Hyundai Tucson", "SUV"), ("Toyota Corolla", "SEDAN"),
          ("Nissan Frontier", "PICKUP"), ("Renault Kangoo", "VAN"), ("Mazda 2", "HATCHBACK")]
MOTOS = ["Yamaha FZ 150", "AKT NKD 125", "Honda CB 125F", "Bajaj Pulsar NS 160", "Suzuki Gixxer 155"]
PERFILES_USO = ("oficina", "nocturno", "mixto")


# ---------- placas, fechas, periodos ----------
def _letras(rng, n):
    return "".join(rng.choice(string.ascii_uppercase) for _ in range(n))


def placa_carro(rng: random.Random) -> str:
    """Formato colombiano de carro: ABC123."""
    return _letras(rng, 3) + f"{rng.randint(0, 999):03d}"


def placa_moto(rng: random.Random) -> str:
    """Formato colombiano de moto: ABC12D."""
    return _letras(rng, 3) + f"{rng.randint(0, 99):02d}" + _letras(rng, 1)


def placa_unica(rng, tipo: str, usadas: set) -> str:
    while True:
        p = placa_carro(rng) if tipo == "CARRO" else placa_moto(rng)
        if p not in usadas:
            usadas.add(p)
            return p


def add_months(d: date, n: int) -> date:
    m = d.month - 1 + n
    y, m = d.year + m // 12, m % 12 + 1
    return date(y, m, min(d.day, calendar.monthrange(y, m)[1]))


def periodo(ancla: date, k: int) -> tuple[date, date]:
    """Periodo k-esimo mensual continuo: [ancla+k meses, ancla+(k+1) meses - 1 dia]."""
    return add_months(ancla, k), add_months(ancla, k + 1) - timedelta(days=1)


def a_dt(d: date, horas: float) -> datetime:
    """Fecha + hora decimal local -> datetime con zona Bogota (admite horas >= 24 o negativas)."""
    return datetime.combine(d, time(0), TZ) + timedelta(hours=horas)


def tarifa_vigente(tarifas: list[dict], tipo: str, fecha: date) -> dict:
    c = [t for t in tarifas if t["tipo"] == tipo and t["desde"] <= fecha]
    return max(c, key=lambda t: t["desde"])


# ---------- visitas y eventos ----------
def _clip(x, lo, hi):
    return max(lo, min(hi, x))


def generar_visitas(rng, perfil: str, d_ini: date, d_fin: date, vacaciones: list[tuple[date, date]]):
    """Visitas (entrada, salida) del vehiculo. Pueden cruzar medianoche. Ordenadas y sin solape."""
    en_vac = lambda d: any(a <= d <= b for a, b in vacaciones)
    crudas = []
    d = d_ini
    while d <= d_fin:
        fds = d.weekday() >= 5
        festivo = d in FESTIVOS
        if en_vac(d):
            pass
        elif perfil == "oficina":
            if not fds and not festivo:
                if rng.random() < 0.93:
                    crudas.append((a_dt(d, _clip(rng.gauss(7.75, 0.4), 6.5, 9.5)),
                                   a_dt(d, _clip(rng.gauss(18.0, 0.6), 16.5, 20.0))))
            elif d.weekday() == 5 and rng.random() < 0.12:
                s = _clip(rng.gauss(10, 1.0), 8, 13)
                crudas.append((a_dt(d, s), a_dt(d, s + _clip(rng.gauss(3, 1), 1, 6))))
        elif perfil == "nocturno":
            vie_sab = d.weekday() in (4, 5)
            e = _clip(rng.gauss(22.5 if vie_sab else 20.5, 1.2), 17.5, 24.5)
            nxt = d + timedelta(days=1)
            s = rng.gauss(8.5 if nxt.weekday() >= 5 else 6.75, 0.7) + (2 if nxt in FESTIVOS else 0)
            crudas.append((a_dt(d, e), a_dt(nxt, _clip(s, 5.0, 11.0))))
        else:  # mixto / ocasional
            if rng.random() < (0.55 if fds else 0.6) and not (festivo and rng.random() < 0.5):
                s = _clip(rng.gauss(12.5, 3.0), 7.5, 19.0)
                dur = _clip(rng.lognormvariate(1.0, 0.5), 0.5, 9.0)
                crudas.append((a_dt(d, s), a_dt(d, s + dur)))
                if rng.random() < 0.3:
                    s2 = s + dur + rng.uniform(1, 3)
                    if s2 < 21.5:
                        crudas.append((a_dt(d, s2), a_dt(d, s2 + _clip(rng.lognormvariate(0.3, 0.5), 0.3, 3))))
        d += timedelta(days=1)
    crudas.sort()
    limpias, prev_fin = [], None
    for ent, sal in crudas:
        if sal - ent < timedelta(minutes=20):
            continue
        if prev_fin is not None and ent < prev_fin + timedelta(minutes=30):
            continue
        ent = ent + timedelta(seconds=rng.randint(0, 59))
        sal = sal + timedelta(seconds=rng.randint(0, 59))
        limpias.append((ent, sal))
        prev_fin = sal
    return limpias


def eventos_desde_visitas(visitas, corte: datetime):
    """Alterna ENTRADA/SALIDA por vehiculo. Lo posterior al corte se descarta (puede quedar dentro)."""
    ev = []
    for ent, sal in visitas:
        if ent > corte:
            break
        ev.append(("ENTRADA", ent))
        if sal <= corte:
            ev.append(("SALIDA", sal))
    return ev


def alternancia_valida(tipos_y_tiempos) -> bool:
    """True si empieza con ENTRADA, alterna estrictamente y los tiempos crecen."""
    prev_t, prev_f = None, None
    for tipo, t in tipos_y_tiempos:
        if prev_t is None and tipo != "ENTRADA":
            return False
        if prev_t is not None and (tipo == prev_t or t <= prev_f):
            return False
        prev_t, prev_f = tipo, t
    return True


# ---------- plan ----------
@dataclass
class PagoPlan:
    periodo_inicio: date
    periodo_fin: date
    tipo: str
    monto: int
    estado: str
    creado_en: datetime
    revisado_en: datetime
    ocr_estado: str
    monto_ocr: int | None
    fecha_pago_ocr: date | None
    motivo_rechazo: str | None
    ruta: str | None
    sha: str | None


@dataclass
class EventoPlan:
    placa_leida: str
    tipo: str
    resultado: str
    motivo: str | None
    ocurrido_en: datetime


@dataclass
class UsuarioPlan:
    n: int
    cupo_codigo: str
    tipo: str
    nombre: str
    apellido: str
    telefono: str
    uso: str
    pago: str
    placa: str
    marca: str
    color: str
    carroceria: str | None
    ancla: date
    baja: date | None = None
    vencido_desde: date | None = None  # corte: no se pagan periodos que terminen despues
    estado: str = "ACTIVO"
    pagos: list = field(default_factory=list)
    eventos: list = field(default_factory=list)
    creado_en: datetime | None = None
    baja_en: datetime | None = None


@dataclass
class Plan:
    inicio: date
    hasta: date
    usuarios: list
    desconocidos: list


def _nuevo_usuario(rng, n, cupo, ancla, usadas_placas, nombres_usados):
    tipo = cupo["tipo"]
    while True:
        nom = (rng.choice(NOMBRES), rng.choice(APELLIDOS))
        if nom not in nombres_usados:
            nombres_usados.add(nom)
            break
    if tipo == "CARRO":
        marca, carr = rng.choice(CARROS)
    else:
        marca, carr = rng.choice(MOTOS), None
    if ancla.day > 28:  # ancla <= 28 para que los periodos mensuales no se desplacen
        ancla = ancla.replace(day=28)
    return UsuarioPlan(
        n=n, cupo_codigo=cupo["codigo"], tipo=tipo, nombre=nom[0], apellido=nom[1],
        telefono=str(3000000000 + n), uso=rng.choice(PERFILES_USO) if tipo == "CARRO" else "mixto",
        pago="puntual", placa=placa_unica(rng, tipo, usadas_placas), marca=marca,
        color=rng.choice(COLORES), carroceria=carr, ancla=ancla)


def _pagos(rng, u: UsuarioPlan, tarifas, corte: datetime, hasta: date):
    k = 0
    while True:
        ini, fin = periodo(u.ancla, k)
        k += 1
        if ini > hasta:
            break
        if u.baja and fin > u.baja - timedelta(days=15):
            break
        if u.vencido_desde and fin > u.vencido_desde:
            break
        if u.pago == "tardio":
            delay = rng.randint(1, 10) if rng.random() < 0.65 else rng.randint(-2, 0)
        else:
            delay = rng.randint(-3, 0) if rng.random() < 0.93 else rng.randint(1, 4)
        if k == 1:
            delay = -rng.randint(0, 2)
        fecha = ini + timedelta(days=delay)
        monto = tarifa_vigente(tarifas, u.tipo, ini)["valor"]
        creado = a_dt(fecha, rng.uniform(8, 21.9))
        if rng.random() < 0.03:  # rechazo y reenvio
            rev = creado + timedelta(hours=rng.uniform(1, 20))
            if creado <= corte:
                u.pagos.append(PagoPlan(ini, fin, u.tipo, monto, "RECHAZADO", creado, min(rev, corte), "PARCIAL",
                                        monto - rng.choice([1000, 2000, 5000]), None,
                                        "El monto del comprobante no coincide con la tarifa", None, None))
            creado = creado + timedelta(days=rng.randint(1, 2), hours=rng.uniform(-2, 2))
        if creado > corte:
            break
        rev = min(creado + timedelta(hours=rng.uniform(1, 20)), corte)
        ocr = rng.choices(["EXITOSO", "PARCIAL", "FALLIDO"], [0.83, 0.12, 0.05])[0]
        ruta = sha = None
        if rng.random() < 0.7:
            ruta = f"simulado/{u.n}-{ini.isoformat()}.jpg"
            sha = hashlib.sha256(ruta.encode()).hexdigest()
        u.pagos.append(PagoPlan(
            ini, fin, u.tipo, monto, "APROBADO", creado, rev, ocr,
            monto if ocr in ("EXITOSO", "PARCIAL") else None,
            creado.date() if ocr == "EXITOSO" else None, None, ruta, sha))


def _lapsos(u: UsuarioPlan, hasta: date):
    """Rangos de dias [a, b] sin cobertura aprobada, dentro de la vida del usuario."""
    fin_vida = (u.baja - timedelta(days=1)) if u.baja else hasta
    ap = sorted((p for p in u.pagos if p.estado == "APROBADO"), key=lambda p: p.periodo_inicio)
    lapsos, cubierto_hasta = [], None
    for p in ap:
        pagado = p.creado_en.date()
        if pagado > p.periodo_inicio:
            lapsos.append((p.periodo_inicio, pagado - timedelta(days=1)))
        cubierto_hasta = p.periodo_fin
    cola = (cubierto_hasta + timedelta(days=1)) if cubierto_hasta else u.ancla
    if cola <= fin_vida:
        lapsos.append((cola, fin_vida))
    return lapsos


def _eventos_usuario(rng, u: UsuarioPlan, hasta, corte):
    fin_vida = (u.baja - timedelta(days=1)) if u.baja else hasta
    vacs = []
    if (fin_vida - u.ancla).days > 120:
        for _ in range(rng.randint(1, 2)):
            ini = u.ancla + timedelta(days=rng.randint(30, (fin_vida - u.ancla).days - 20))
            vacs.append((ini, ini + timedelta(days=rng.randint(7, 14))))
    visitas = generar_visitas(rng, u.uso, u.ancla, fin_vida, vacs)
    lapsos = _lapsos(u, hasta)
    en_lapso = lambda d: next((i for i, (a, b) in enumerate(lapsos) if a <= d <= b), None)
    ya_visto, validas, deuda_salidas, denegados = set(), [], set(), []
    for ent, sal in visitas:
        li = en_lapso(ent.date())
        if li is not None:
            if li not in ya_visto:
                ya_visto.add(li)
                if rng.random() < 0.5 and ent <= corte:
                    denegados.append(EventoPlan(u.placa, "ENTRADA", "DENEGADO", "USUARIO_VENCIDO", ent))
            continue
        validas.append((ent, sal))
        if en_lapso(sal.date()) is not None:
            deuda_salidas.add(sal)
    evs = []
    for tipo, t in eventos_desde_visitas(validas, corte):
        motivo = "SALIDA_CON_DEUDA" if (tipo == "SALIDA" and t in deuda_salidas) else None
        evs.append(EventoPlan(u.placa, tipo, "PERMITIDO", motivo, t))
    evs += denegados
    if u.baja and rng.random() < 0.5:
        t = a_dt(u.baja + timedelta(days=rng.randint(7, 25)), rng.uniform(8, 20))
        if t <= corte:
            evs.append(EventoPlan(u.placa, "ENTRADA", "DENEGADO", "USUARIO_DE_BAJA", t))
    evs.sort(key=lambda e: e.ocurrido_en)
    u.eventos = evs


def planificar(semilla: int, meses: int, hasta: date, tarifas: list[dict], cupos: list[dict],
               ahora: datetime | None = None) -> Plan:
    """Construye todo el escenario en memoria. cupos: [{codigo, tipo}], tarifas: [{tipo, valor, desde, id}]."""
    rng = random.Random(semilla)
    inicio = add_months(hasta, -meses)
    ahora = ahora or datetime.now(TZ)
    corte = min(ahora, a_dt(hasta, 23.99))
    cupos = sorted(cupos, key=lambda c: c["codigo"])
    n_bajas = min(rng.randint(3, 4), len(cupos))
    con_baja = set(rng.sample(range(len(cupos)), n_bajas))
    usadas, nombres, usuarios, n = set(), set(), [], 0
    for i, cupo in enumerate(cupos):
        n += 1
        u = _nuevo_usuario(rng, n, cupo, inicio + timedelta(days=rng.randint(0, 27)), usadas, nombres)
        usuarios.append(u)
        if i in con_baja:
            lo, hi = u.ancla + timedelta(days=75), hasta - timedelta(days=20)
            if lo < hi:
                u.baja = lo + timedelta(days=rng.randint(0, (hi - lo).days))
                ingreso = u.baja + timedelta(days=rng.randint(5, 25))
                if ingreso <= hasta - timedelta(days=20):
                    n += 1
                    usuarios.append(_nuevo_usuario(rng, n, cupo, ingreso, usadas, nombres))
    vigentes = [u for u in usuarios if not u.baja and (hasta - u.ancla).days > 90]
    if vigentes:
        rng.choice(vigentes).vencido_desde = hasta - timedelta(days=rng.randint(3, 14))
    for u in rng.sample(usuarios, min(2, len(usuarios))):
        u.pago = "tardio"
    for u in usuarios:
        u.creado_en = a_dt(u.ancla - timedelta(days=1), 10)
        if u.baja:
            u.baja_en = a_dt(u.baja, 10)
        _pagos(rng, u, tarifas, corte, hasta)
        _eventos_usuario(rng, u, hasta, corte)
        cubierto = any(p.estado == "APROBADO" and p.periodo_inicio <= hasta <= p.periodo_fin for p in u.pagos)
        u.estado = "ACTIVO" if (cubierto and not u.baja) else "VENCIDO"
    desconocidos = []
    for _ in range(rng.randint(4, 8)):
        d = inicio + timedelta(days=rng.randint(0, (hasta - inicio).days))
        t = a_dt(d, rng.uniform(6, 22))
        if t <= corte:
            desconocidos.append(EventoPlan(placa_unica(rng, rng.choice(["CARRO", "MOTO"]), usadas), "ENTRADA",
                                           "DENEGADO", "PLACA_DESCONOCIDA", t))
    desconocidos.sort(key=lambda e: e.ocurrido_en)
    return Plan(inicio, hasta, usuarios, desconocidos)
