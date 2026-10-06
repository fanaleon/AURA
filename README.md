# Slop

Asistente por voz para Android con tu modelo como widget animado. Funciona gratis con Gemini.

- **Widget animado**: la modelo "respira", cada tanto la cruza un destello, el borde naranja late,
  el micrófono emite anillos y rotan frases que invitan a tocarla. Todo corre dentro del launcher,
  sin que la app esté abierta.
- **Reacciona al toque**: el widget se hunde y hace una onda al tocarlo; se abre la asistente,
  pregunta "¿Qué necesitás?" en voz alta y se queda escuchando. En la app también podés tocar a la
  modelo (o el micrófono) para hablarle o interrumpirla.
- **Conversación por voz**: te escucha con el reconocimiento de voz del celu y te contesta hablando.
  Con "Manos libres" vuelve a escuchar sola después de cada respuesta (decí "chau" o "gracias" para cortar).
- **Internet**: busca clima, noticias, precios, resultados, etc. y muestra de qué páginas sacó la respuesta.
- **Idioma**: español o inglés (voz, reconocimiento, textos de la app y del widget).

## Con qué piensa

En ajustes elegís el "cerebro":

| Cerebro | Costo | Internet |
| --- | --- | --- |
| **Gemini** (el que viene elegido) | Gratis, con un tope de consultas por día | Con una key gratuita de Tavily: 1.000 búsquedas por mes |
| **Claude** | Pago por consulta | Incluido en la API de Claude, con un pequeño costo por búsqueda |

Con Gemini gratis, Google puede usar lo que le decís para mejorar sus productos. Si eso te
molesta para algún tema, usá Claude o no se lo cuentes.

## Las keys gratuitas

1. **Gemini**: entrá a aistudio.google.com con tu cuenta de Google › *Get API key* › *Create API key*.
2. **Tavily** (opcional, para internet): registrate en app.tavily.com; la key aparece en el panel.
   No pide tarjeta.

Las dos se pegan en los ajustes de la app y quedan guardadas solo en el celu.

## Compilar el APK con GitHub Actions

1. Subí los cambios a la rama `main` del repo (o lanzá el workflow a mano con *Run workflow*).
2. En la pestaña **Actions** corre "Build APK".
3. El APK queda en **Releases** (`Slop build N`). Bajalo al celu e instalalo.

Todas las builds se firman con la misma clave de prueba (`app/debug.keystore`), así que cada APK
nuevo se instala encima del anterior sin perder los ajustes.

Si el build falla, los errores del compilador aparecen arriba de todo en la corrida, como anotaciones.

### Si venías de "Aura"

Slop es una app distinta para Android (`app.slop` en vez de `app.aura`): se instala al lado de la
anterior y no hereda sus ajustes. Desinstalá Aura, instalá Slop, cargá las keys y volvé a poner el widget.

## Primer uso

1. Abrí la app. Se abre el panel de ajustes: elegí idioma, pegá la **API key de Gemini** y, si
   querés que busque en internet, la de **Tavily**.
2. Aceptá el permiso de micrófono la primera vez que le hables.
3. Mantené apretado un lugar vacío de la pantalla de inicio › Widgets › **Slop**, y soltalo donde quieras.
   Podés cambiarle el tamaño: la foto se reencuadra sola.

## Ajustes

| Ajuste | Qué hace |
| --- | --- |
| Idioma | Español / English. Cambia voz, reconocimiento y textos. |
| Cerebro | Gemini (gratis) o Claude (pago). Cada uno guarda su propia key. |
| API key | La de la IA elegida. |
| API key de Tavily | Solo con Gemini. Sin ella responde igual, pero sin buscar en internet. |
| Buscar en internet | Le permite buscar datos actuales. También se prende y apaga con el botón "Web" de arriba. |
| Modelo | "Inteligente" o "Rápida" (responde antes). Con Gemini, si una llega al límite gratis sigue con la otra. |
| Nombre de la asistente | Cómo se llama y se presenta. |
| Widget animado | Apagalo para un widget fijo que gasta menos batería. |

## Si algo no anda

- **"Llegaste al límite gratis de Gemini"**: se acabó el cupo del momento. Suele alcanzar con esperar
  unos minutos; el cupo diario se renueva solo. Los topes de tu cuenta se ven en aistudio.google.com.
- **"La API key de Gemini no es válida o venció"**: creá una nueva en aistudio.google.com y cargala en ajustes.
- **El botón "Web" no se prende**: con Gemini falta la key de Tavily.
- **"No busqué en internet: se acabaron las búsquedas gratis de Tavily"**: vuelven el mes siguiente.
  Mientras tanto responde con lo que sabe.
- **No habla o habla en otro idioma**: Ajustes del celu › Salida de texto a voz › instalá la voz en español
  (motor "Servicios de voz de Google").
- **No escucha**: revisá el permiso de micrófono y que "Servicios de voz de Google" o la app de Google estén activos.
- **El widget no se mueve**: algunos launchers pausan las animaciones con el ahorro de batería activado.
- **Con Claude, dice que no pudo usar internet**: la búsqueda web está desactivada para tu cuenta en
  platform.claude.com (Settings › Capabilities). Mientras tanto responde igual, sin buscar.

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
- `Assistant.kt` — el ciclo escuchar › pensar › hablar, y a qué IA le pregunta.
- `Voice.kt` — reconocimiento de voz y texto a voz.
- `GeminiApi.kt` — llamada a Gemini (modelos `gemini-3.8-flash` y `gemini-3.5-flash-lite`). Cuando
  Gemini pide buscar, la app consulta Tavily y le devuelve los resultados.
- `Tavily.kt` — búsqueda en internet para Gemini.
- `ClaudeApi.kt` — llamada a Claude con su búsqueda web (modelos `claude-sonnet-5-5` y `claude-haiku-4-5-20251001`).
- `Api.kt` — lo que comparten: tipos, errores y los pedidos HTTP.
- `Texts.kt` — todos los textos en español e inglés, y las instrucciones para la IA.
- `AvatarWidget.kt` + `res/layout/widget_avatar.xml` + `res/anim/w_*.xml` — el widget y sus animaciones.
- `SettingsSheet.kt`, `Prefs.kt`, `Views.kt` — ajustes y vistas propias (micrófono, resplandor).

No tiene palabra de activación ("Hey Slop"): se activa tocando el widget, a la modelo o el micrófono.
