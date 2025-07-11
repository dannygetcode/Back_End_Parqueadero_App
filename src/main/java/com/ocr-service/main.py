from flask import Flask, request, jsonify
import pytesseract
from PIL import Image
import re
from dateutil import parser as date_parser


pytesseract.pytesseract.tesseract_cmd = r"C:\Program Files\Tesseract-OCR\tesseract.exe"

app = Flask(__name__)

def extraer_valor(linea: str) -> int | None:
    for patron in [r'\$\s?([\d\.]+)(?:,\d{2})?']:
        if m := re.search(patron, linea.replace(' ', ''), re.IGNORECASE):
            # "80.000,00" → "80000"
            return int(m.group(1).replace('.', ''))
    return None


def extraer_fecha(linea: str) -> str | None:
    """
    1) Busca patrones de fecha con nombre de mes en español o numérico.
    2) Usa dateutil.parser para convertirlo a datetime.
    3) Devuelve la fecha en ISO (YYYY-MM-DD).
    """
    # busquemos primero texto con 'de' (e.g. "6 de Junio de 2025")
    m = re.search(
        r'\b\d{1,2}\s+de\s+(?:enero|febrero|marzo|abril|mayo|junio|julio|agosto|'
        r'septiembre|octubre|noviembre|diciembre)\s+de\s+\d{4}\b',
        linea, re.IGNORECASE)
    if not m:
        # si no, caigamos a dd/mm/yyyy o dd-mm-yyyy
        m = re.search(r'\b\d{1,2}[/-]\d{1,2}[/-]\d{4}\b', linea)
    if not m:
        # o mes abreviado: "16 Jun 2025"
        m = re.search(r'\b\d{1,2}\s+[A-Za-z]{3,9}\s+\d{4}\b', linea)

    if m:
        # dateutil entiende tanto español como numérico
        dt = date_parser.parse(m.group(0), dayfirst=True, fuzzy=True)
        return dt.date().isoformat()  # e.g. "2025-06-06"
    return None


def procesar_lineas(texto: str) -> dict:
    """
    Recorre línea por línea y extrae:
      - fecha: la primera coincidencia válida
      - valor: la primera coincidencia válida
    """
    resultado = {}
    for linea in texto.splitlines():
        linea = linea.strip()
        if not linea:
            continue

        # Extraer fecha si aún no está
        if 'fecha' not in resultado:
            if f := extraer_fecha(linea):
                resultado['fecha'] = f

        # Extraer valor si aún no está
        if 'valor' not in resultado:
            if v := extraer_valor(linea):
                resultado['valor'] = v


        # Si ya tenemos ambos, podemos salir
        if 'fecha' in resultado and 'valor' in resultado:
            break

    return resultado

@app.route("/ocr", methods=["POST"])
def ocr():
    if 'image' not in request.files:
        return jsonify({"error": "No image uploaded"}), 400

    # Carga la imagen y obtiene texto crudo
    img = Image.open(request.files['image'].stream)
    texto = pytesseract.image_to_string(img)
    print("=== TEXTO OCR ===\n", texto)  # Debug en consola

    # Procesa solo fecha y valor
    datos = procesar_lineas(texto)
    return jsonify(datos)

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
