<div align="center">

# VisionAlejandrino

<img src="https://github.com/AlejandrinoDGR/VisionAlejandrino/blob/main/assets/app-icon.png" width="150">

![GitHub License](https://img.shields.io/github/license/AlejandrinoDGR/VisionAlejandrino?style=for-the-badge) ![Android Badge](https://img.shields.io/badge/Android-7.0+-34A853?logo=android&logoColor=fff&style=for-the-badge) ![Jetpack Compose Badge](https://img.shields.io/badge/Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=fff&style=for-the-badge) ![Material 3](https://img.shields.io/badge/Material%203-ebe89d?style=for-the-badge&logo=materialdesign&logoColor=white) ![Version](https://img.shields.io/badge/Version-4.20-blue?style=for-the-badge)

**Autor:** ALEJANDRINOTECNOLOGIA

### Accede a la cámara de Android usando una API cliente WebSocket para realizar tareas de IA y visión artificial en tiempo real sobre una transmisión de video en vivo.

<img src="https://github.com/AlejandrinoDGR/VisionAlejandrino/blob/main/fastlane/metadata/android/en-US/images/phoneScreenshots/1.jpg" width="250" heigth="250"> <img src="https://github.com/AlejandrinoDGR/VisionAlejandrino/blob/main/fastlane/metadata/android/en-US/images/phoneScreenshots/2.jpg" width="250" heigth="250"> 

</div>

**VisionAlejandrino** (v4.20) transmite video en tiempo real utilizando el codec **H.264 (AVC)** acelerado por hardware. A diferencia de las versiones anteriores que enviaban JPEGs individuales, esta versión utiliza `MediaCodec` para generar un stream de video de alta fidelidad y baja latencia, optimizando drásticamente el uso de batería y CPU del dispositivo móvil.

---

## 🎓 Guía para Estudiantes Universitarios

Si estás estudiando Ingeniería, Ciencias de la Computación o IA, esta aplicación es una herramienta excelente para entender varios conceptos fundamentales:

### 1. Arquitectura de Video en Tiempo Real
La app utiliza el hardware dedicado del dispositivo para comprimir video. Aprenderás sobre unidades NAL, formatos Annex B y la importancia de los parámetros SPS/PPS para inicializar decodificadores de video.

### 2. Eficiencia de Datos
Al pasar de JPEG a H.264, el ancho de banda se reduce drásticamente (hasta un 80%) enviando solo los cambios entre frames en lugar de imágenes completas, permitiendo transmisiones de alta calidad incluso en redes WiFi estándar.

### 3. Concurrencia y Media Pipeline
Verás cómo se conectan las APIs de CameraX con MediaCodec a través de `Surfaces`, permitiendo que el video viaje por la GPU sin sobrecargar la CPU principal.

---

## 🚀 Características

- **Streaming H.264 (AVC)**: Transmisión de video comprimido por hardware (H.264 Annex B).
- **Múltiples Clientes**: Soporte para varias conexiones simultáneas.
- **Optimización Industrial**: Uso de `High Profile` para máxima nitidez y `CBR` para estabilidad.
- **Baja Latencia**: Optimizado para tareas de IA en tiempo real (YOLO, MediaPipe).
- **Control de Resolución y FPS**: Ajustes personalizables desde la interfaz.
- **Servicio en Primer Plano**: El servidor sigue activo aunque la app esté en segundo plano.
- **Control Remoto**: API JSON para controlar enfoque, brillo, zoom y linterna.

## ⚡ Consejos de Rendimiento (10/10)

Para obtener el mejor rendimiento en tareas de visión artificial:

1. **Decodificación**: Asegúrate de que tu script de Python utilice un decodificador compatible con H.264 (como PyAV o FFmpeg). Consulta `docs/Protocolo_Streaming_H264.md` para más detalles técnicos.
2. **Bitrate**: El sistema está configurado a 5Mbps para alta calidad. Si la red es inestable, puedes ajustar este valor en el código fuente.
3. **SPS/PPS**: La app inserta automáticamente cabeceras de configuración en cada frame clave, permitiendo conexiones instantáneas.

## 🕹️ Control Remoto (API de Comandos)

Puedes controlar los parámetros de la cámara enviando objetos JSON a través de la conexión WebSocket. 

### 1. Enfoque (Focus)
```json
{
  "type": "focus",
  "x": 0.5,
  "y": 0.5
}
```

### 2. Brillo / Exposición (Brightness)
```json
{
  "type": "brightness",
  "index": 1
}
```

### 3. Zoom
```json
{
  "type": "zoom",
  "ratio": 2.5
}
```

### 4. Linterna / Flash (Torch)
```json
{
  "type": "torch",
  "enabled": true
}
```

## 🛠️ Cómo Funciona

1.  **Captura de Cámara**: CameraX envía frames directamente al encoder por hardware vía Surface.
2.  **Codificación**: MediaCodec genera un stream H.264 en formato Annex B.
3.  **Servidor WebSocket**: Escucha en el puerto 8080 y transmite las unidades NAL a los clientes.

## 🐍 Ejemplo en Python (H.264)

```python
import av
import websocket

# El servidor ahora envía bytes de H.264 en formato Annex B
def on_message(ws, message):
    # Ver docs/Protocolo_Streaming_H264.md para el ejemplo completo
    # de decodificación con PyAV
    pass

ws = websocket.WebSocketApp("ws://192.168.1.100:8080", on_message=on_message)
ws.run_forever()
```

## 📜 Licencia
Este proyecto está bajo la licencia **GNU General Public License v3.0**.
