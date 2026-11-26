# Escáner Policlínico (Android) — versión 1.0.0-beta1

App Android para escanear documentos de un paciente, armar **un solo PDF con su nombre** y enviarlo
por el **Wi-Fi del hospital** al PC donde corre [Unir PDF](https://github.com/DrazerCode/Unir-PDF).
El PDF no pasa por internet: va directo del teléfono al PC.

## Qué hace
1. Escribes el **nombre del paciente**.
2. Pulsas **Escanear**. Se abre el escáner de documentos de Google, que:
   - detecta los bordes de la hoja, la **recorta y la endereza** automáticamente,
   - permite **girar**, **recortar a mano**, aplicar **filtros** (blanco y negro / mejorar texto),
     **repetir** o **borrar** una foto antes de aceptar,
   - también permite importar fotos desde la galería.
3. De vuelta en la app ves la lista de páginas y puedes:
   - **ordenar** con ▲ ▼,
   - **repetir** una página (⟳) sin perder las demás,
   - **quitar** una página (🗑),
   - **agregar** más páginas.
4. **Enviar al PC**: arma el PDF (una hoja A4 por página, igual que Unir PDF) y lo sube a Unir PDF.
   En el PC queda en `Escritorio\Escaneos\<paciente>\<paciente>.pdf` y aparece en la lista de Unir PDF.
5. Tras un envío correcto se borra todo del teléfono y queda listo para el siguiente paciente.

El botón **Compartir** (⇪) es un respaldo para enviar el PDF con otra app (correo, Drive…).
Úsalo solo si la política del hospital lo permite: son datos clínicos.

## Requisitos
- Teléfono Android 8.0 o superior, con **Google Play Services** actualizado.
- La primera vez, el teléfono necesita internet unos minutos para que Google descargue el módulo del escáner
  (se hace solo al instalar la app). Después funciona sin internet.
- En el PC: Unir PDF con la función de recepción de escaneos abierta (muestra su dirección, por ejemplo
  `192.168.1.50:8080`). El teléfono y el PC deben estar en la **misma red**.

## Configurar la primera vez
Al abrir la app aparece **PC con Unir PDF**: escribe la dirección que muestra Unir PDF (IP y puerto) y pulsa
**Probar conexión**. Si dice "Conectado con Unir PDF en …", pulsa **Guardar**.
Puedes cambiarla después con el engranaje ⚙ (arriba a la derecha).

Si no conecta:
- Revisa que el teléfono esté en el Wi-Fi del hospital (no en datos móviles ni en una red de invitados).
- En el PC, el Firewall de Windows debe permitir a Unir PDF en redes privadas (Windows lo pregunta la primera vez).
- Algunas redes Wi-Fi aíslan a los equipos entre sí; en ese caso hay que pedir a informática que lo permita.

## Instalar sin Android Studio (lo más fácil)
Cada cambio en GitHub compila la app automáticamente:
1. En GitHub, entra a la pestaña **Actions** del repositorio → la última ejecución de **Compilar APK** con ✅.
2. Abajo, en **Artifacts**, descarga **EscanerPoliclinico-apk** (es un .zip con `app-debug.apk`).
3. Copia `app-debug.apk` al teléfono y ábrelo. Android pedirá permitir "instalar apps desconocidas"
   para el explorador de archivos; acéptalo.

## Compilar con Android Studio (en el PC)
1. Instala [Android Studio](https://developer.android.com/studio) (versión Ladybug 2024.2 o más nueva).
2. **File → New → Project from Version Control…** y pega `https://github.com/DrazerCode/Scanner.git`
   (o descarga el código como .zip y usa **File → Open**).
3. Espera a que termine "Gradle sync" (la primera vez descarga varias cosas, tarda unos minutos).
4. En el teléfono activa las **Opciones de desarrollador** (Ajustes → Acerca del teléfono → tocar 7 veces
   "Número de compilación") y dentro activa **Depuración USB**.
5. Conecta el teléfono por USB, acepta el aviso "¿Permitir depuración USB?", elígelo arriba en Android Studio
   y pulsa ▶ **Run**.

Desde la línea de comandos: `gradlew.bat assembleDebug` (Windows) o `./gradlew assembleDebug`;
el APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Cómo se comunica con Unir PDF
Solo HTTP dentro de la red local:
- `GET  http://<PC>:8080/api/ping` → `{"ok":true,"app":"UnirPDF","equipo":"<nombre del PC>"}`
- `POST http://<PC>:8080/api/subir` (multipart): campo `paciente` (texto) y campo `pdf` (archivo PDF)
  → `{"ok":true,"archivo":"<nombre guardado>"}` o `{"ok":false,"error":"…"}`.

Si escribes la IP sin puerto, la app usa 8080.

## Privacidad
- Las fotos y el PDF se guardan solo en la memoria privada de la app y se borran al enviar,
  al pulsar "Nuevo paciente" o al volver a abrir la app.
- La app no tiene copia de seguridad en la nube (`allowBackup=false`) y no envía nada a internet.

## Estructura del código
- `MainActivity.kt` — abre el escáner de Google y la opción Compartir.
- `Pantalla.kt` — pantalla principal, lista de páginas y ajustes.
- `EscanerViewModel.kt` — estado: páginas, paciente, envío.
- `GeneradorPdf.kt` — arma el PDF (A4, JPEG dentro del PDF para que pese poco).
- `Servidor.kt` — conexión con Unir PDF.

Tecnología: Kotlin + Jetpack Compose + [ML Kit Document Scanner](https://developers.google.com/ml-kit/vision/doc-scanner).

---
APP desarrollada por TMO Martin Paves Ibaceta
