"""Cache en memoria con TTL; el reloj es inyectable para poder probarlo."""
import threading
import time


class CacheTTL:
    def __init__(self, ttl_segundos: float, reloj=time.monotonic):
        self._ttl = ttl_segundos
        self._reloj = reloj
        self._datos: dict = {}
        self._lock = threading.Lock()
        self._claves: dict = {}   # un lock por clave: single-flight

    def _vigente(self, clave):
        hit = self._datos.get(clave)
        if hit is not None and self._reloj() - hit[0] < self._ttl:
            return hit
        return None

    def obtener(self, clave, fabrica):
        """Devuelve el valor vigente o lo calcula con fabrica() y lo guarda.
        Single-flight: con cache frio, las llamadas concurrentes a la misma clave esperan una sola carga."""
        with self._lock:
            hit = self._vigente(clave)
            if hit is not None:
                return hit[1]
            lock_clave = self._claves.setdefault(clave, threading.Lock())
        with lock_clave:
            with self._lock:
                hit = self._vigente(clave)
            if hit is not None:
                return hit[1]
            valor = fabrica()   # si falla, no se guarda y la siguiente espera su turno y reintenta
            with self._lock:
                self._datos[clave] = (self._reloj(), valor)
            return valor

    def vaciar(self):
        with self._lock:
            self._datos.clear()
