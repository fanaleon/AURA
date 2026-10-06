# Aura

Asistente por voz para Android con tu modelo como widget animado.

- **Widget animado**: la modelo "respira", cada tanto la cruza un destello, el borde naranja late,
  el micrófono emite anillos y rotan frases que invitan a tocarla. Todo corre dentro del launcher,
  sin que la app esté abierta.
- **Reacciona al toque**: el widget se hunde y hace una onda al tocarlo; se abre la asistente,
  pregunta "¿Qué necesitás?" en voz alta y se queda escuchando. En la app también podés tocar a la
  modelo (o el micrófono) para hablarle o interrumpirla.
- **Conversación por voz**: te escucha con el reconocimiento de voz del celu y te contesta hablando.
  Con "Manos libres" vuelve a escuchar sola después de cada respuesta (decí "chau" o "gracias" para cortar).
- **Internet**: usa la búsqueda web de Claude para clima, noticias, precios, resultados, etc.
  y muestra de qué páginas sacó la respuesta.
- **Idioma**: español o inglés (voz, reconocimiento, textos de la app y del widget).

## Compilar el APK con GitHub Actions

1. Creá un repo en GitHub y subí **todo** el contenido de esta carpeta, incluida la carpeta oculta
   `.github` y el archivo `app/debug.keystore`.
   - Si ya habías subido la versión anterior de Aura: borrá del repo las carpetas
     `app/src/main/java/app/aura/ui` y `app/src/main/java/app/aura/widget`, que ya no existen,
     y reemplazá el resto.
2. En la pestaña **Actions** corre solo "Build APK" (o lanzalo con *Run workflow*).
3. El APK queda en **Releases** (`Aura build N`). Bajalo al celu e instalalo.

Todas las builds se firman con la misma clave de prueba (`app/debug.keystore`), así que cada APK
nuevo se instala encima del anterior sin perder los ajustes. Esa clave queda dentro del repo:
conviene que el repo sea privado.

Si el build falla, abrí el paso "Build debug APK", copiá el error y pasámelo.

## Primer uso

1. Abrí la app. Se abre el panel de ajustes: pegá tu **API key de Claude** y elegí el idioma.
   - La key se crea en platform.claude.com › API keys. Cada consulta se cobra a esa cuenta.
   - Al crearla elegí **un solo workspace** (por ejemplo, Default): las keys que sirven para varios
     workspaces no funcionan con esta app.
   - Si le ponés vencimiento, cuando venza la app va a pedirte una nueva.
2. Aceptá el permiso de micrófono la primera vez que le hables.
3. Mantené apretado un lugar vacío de la pantalla de inicio › Widgets › **Aura**, y soltalo donde quieras.
   Podés cambiarle el tamaño: la foto se reencuadra sola.

## Ajustes

| Ajuste | Qué hace |
| --- | --- |
| Idioma | Español / English. Cambia voz, reconocimiento y textos. |
| Buscar en internet | Le permite buscar datos actuales. Cada búsqueda suma un pequeño costo. También se prende y apaga con el botón "Web" de arriba. |
| Nombre de la asistente | Cómo se llama y se presenta. |
| Modelo | "Inteligente" (Claude Sonnet) o "Rápida" (Claude Haiku: responde antes y cuesta menos). |
| Widget animado | Apagalo para un widget fijo que gasta menos batería. |

## Si algo no anda

- **No habla o habla en otro idioma**: Ajustes del celu › Salida de texto a voz › instalá la voz en español
  (motor "Servicios de voz de Google").
- **No escucha**: revisá el permiso de micrófono y que "Servicios de voz de Google" o la app de Google estén activos.
- **Dice que no pudo usar internet**: la búsqueda web está desactivada para tu cuenta en
  platform.claude.com (Settings › Capabilities). Mientras tanto responde igual, sin buscar.
- **"La API key no es válida o venció"**: creá una key nueva en platform.claude.com y cargala en ajustes.
- **El widget no se mueve**: algunos launchers pausan las animaciones con el ahorro de batería activado.

## Cambiar la foto

Reemplazá estos archivos en `app/src/main/res/drawable-nodpi/` manteniendo nombre y proporción:

| Archivo | Tamaño | Uso |
| --- | --- | --- |
| `avatar_full.jpg` | 850 × 1915 | Fondo de la app (cuerpo entero, con aire arriba de la cabeza) |
| `w_tall.jpg` | 540 × 960 | Widget alto |
| `w_mid.jpg` | 540 × 675 | Widget intermedio |
| `w_square.jpg` | 520 × 520 | Widget cuadrado |
| `w_wide.jpg` | 850 × 425 | Widget apaisado |
| `ic_launcher_bg.jpg` | 432 × 432 | Ícono de la app (cara centrada) |

Si en `avatar_full.jpg` la cabeza queda a otra altura, ajustá `PHOTO_HAIR_TOP` y `PHOTO_FACE`
al final de `MainActivity.kt`.

## Cómo está armado

La app no usa bibliotecas externas: solo el framework de Android y Kotlin.

- `MainActivity.kt` — pantalla, chat y animaciones.
- `Assistant.kt` — el ciclo escuchar › pensar › hablar.
- `Voice.kt` — reconocimiento de voz y texto a voz.
- `ClaudeApi.kt` — llamada a Claude con búsqueda web (modelos `claude-sonnet-5-5` y `claude-haiku-4-5-20251001`).
- `Texts.kt` — todos los textos en español e inglés, y las instrucciones para Claude.
- `AvatarWidget.kt` + `res/layout/widget_avatar.xml` + `res/anim/w_*.xml` — el widget y sus animaciones.
- `SettingsSheet.kt`, `Prefs.kt`, `Views.kt` — ajustes y vistas propias (micrófono, resplandor).

No tiene palabra de activación ("Hey Aura"): se activa tocando el widget, a la modelo o el micrófono.
