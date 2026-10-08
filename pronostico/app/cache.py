"""Cache en memoria con TTL; el reloj es inyectable para poder probarlo."""
import threading
import time


class CacheTTL:
    def __init__(self, ttl_segundos: float, reloj=time.monotonic):
        self._ttl = ttl_segundos
        self._reloj = reloj
        self._datos: dict = {}
        self._lock = threading.Lock()

    def obtener(self, clave, fabrica):
        """Devuelve el valor vigente o lo calcula con fabrica() y lo guarda."""
        with self._lock:
            ahora = self._reloj()
            hit = self._datos.get(clave)
            if hit is not None and ahora - hit[0] < self._ttl:
                return hit[1]
        valor = fabrica()
        with self._lock:
            self._datos[clave] = (self._reloj(), valor)
        return valor

    def vaciar(self):
        with self._lock:
            self._datos.clear()
