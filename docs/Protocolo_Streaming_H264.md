# Protocolo de Streaming H.264 (VisionAlejandrino)

Este documento detalla la estructura técnica de los datos de video enviados por la aplicación Android para que el equipo de servidor/Python pueda configurar correctamente el decodificador.

## 1. Empaquetamiento de Mensajes (WebSocket)
*   **Contenido:** Cada mensaje enviado a través del WebSocket contiene **un conjunto de NAL Units (Network Abstraction Layer)** correspondientes a un frame o a información de configuración.
*   **Fragmentación:** No se envían NAL units individuales en mensajes separados si pertenecen al mismo "pago" del encoder. El `MediaCodec` entrega un `ByteBuffer` completo y la app lo retransmite íntegro.

## 2. Formato de Encapsulamiento (Annex B)
*   **Prefijo:** La aplicación utiliza el formato **Annex B**.
*   **Start Codes:** Cada NAL Unit está precedida por un código de inicio de 4 bytes: `00 00 00 01` (o en algunos casos de 3 bytes `00 00 01`).
*   **Identificación:** El servidor debe buscar estos prefijos para separar las unidades NAL si el decodificador no lo hace automáticamente.
*   **AVCC vs Annex B:** **NO** se utiliza el formato AVCC (longitud de 4 bytes). Se ha preferido Annex B por ser el estándar nativo que entrega el hardware de Android y por su compatibilidad directa con la mayoría de los reproductores de stream crudo.

## 3. Parámetros de Configuración (SPS y PPS)
*   **Transmisión:** Los parámetros **SPS (Sequence Parameter Set)** y **PPS (Picture Parameter Set)** NO se envían como mensajes de texto ni por un canal separado.
*   **Inclusión en el Stream:** El `MediaCodec` genera estos parámetros al inicio de la transmisión y cada vez que hay un "Keyframe" (I-Frame). Están incluidos dentro de los datos binarios del WebSocket, marcados con sus respectivos Start Codes (`00 00 00 01`).
*   **Importancia:** El decodificador en Python (PyAV/FFmpeg) debe recibir primero estos datos para conocer la resolución y el perfil de color antes de poder decodificar el primer frame de imagen.

## 4. Configuración del Decoder en Python (Recomendación)
Para decodificar correctamente estos datos en el servidor, se recomienda inicializar el objeto de la siguiente manera:

```python
import av
import numpy as np

# Inicializar un decodificador para H.264 crudo
codec = av.CodecContext.create('h264', 'r')

def procesar_mensaje_binario(datos_binarios):
    # Crear un paquete a partir de los bytes recibidos (Annex B)
    packets = codec.parse(datos_binarios)
    for packet in packets:
        frames = codec.decode(packet)
        for frame in frames:
            # Convertir a formato OpenCV
            img = frame.to_ndarray(format='bgr24')
            # PROCESAR IMAGEN...
```

## 5. Especificaciones Técnicas del Encoder
*   **MIME Type:** `video/avc` (H.264)
*   **Bitrate Mode:** Variable con prioridad en latencia.
*   **I-Frame Interval:** 1 segundo (esto significa que el SPS/PPS se retransmite al menos una vez por segundo para permitir que nuevos clientes se conecten al vuelo).
*   **Color Format:** `COLOR_FormatSurface` (YUV 420 nativo).

---
*Este documento es parte de la documentación técnica de VisionAlejandrino.*