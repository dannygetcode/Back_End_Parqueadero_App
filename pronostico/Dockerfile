FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /srv
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app
COPY scripts ./scripts
COPY metricas.json ./metricas.json
RUN useradd --system --no-create-home --uid 10001 pronostico
USER pronostico
EXPOSE 8000
# 0.0.0.0 solo dentro de la red interna de compose: el servicio no tiene autenticacion.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
