"""Tipos de datos compartidos (sin dependencias de BD)."""
from dataclasses import dataclass, field
from datetime import date, datetime
from typing import NamedTuple, Optional


class Pago(NamedTuple):
    inicio: date
    fin: date
    pagado_el: date          # fecha local de creacion del pago aprobado


class Visita(NamedTuple):
    usuario_id: Optional[int]
    vehiculo_id: int
    tipo_vehiculo: str
    entrada: datetime        # hora local, sin zona
    salida: Optional[datetime]   # None = sigue dentro


@dataclass
class Datos:
    cupos: list = field(default_factory=list)       # [(codigo, tipo)]
    usuarios: list = field(default_factory=list)    # [(id, estado, cupo, tipo)] con cupo y sin baja
    pagos: dict = field(default_factory=dict)       # usuario_id -> [Pago] ordenados por inicio
    eventos: list = field(default_factory=list)     # [(vehiculo_id, usuario_id, tipo_vehiculo, tipo, ts_local)]
