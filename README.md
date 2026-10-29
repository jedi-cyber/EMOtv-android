# EMOtv Android

Aplicativo Android nativo (Kotlin y Jetpack Compose) de EMOtv, proyecto de innovación de
la Facultad de Psicología de la UNHEVAL. Complementa al sistema web para consultar lo ya
registrado: el Usuario o el Docente inicia sesión y ve el resumen del día, sus sesiones con
su línea de tiempo y sus métricas, los reportes y las estadísticas agregadas.

- El Usuario redacta el reporte de sus sesiones, lo envía a revisión y responde a las
  observaciones editándolo y enviándolo de nuevo.
- El Docente ve los reportes de su grupo que esperan su revisión. Los comenta, los
  devuelve con observaciones o los aprueba.

**El aplicativo no conduce sesiones ni usa la cámara.** Las sesiones se conducen solo
desde el Analizador de la web, así que ninguna imagen de una persona pasa por el
dispositivo. Las personas evaluadas no se registran: cada sesión se identifica por su código
aleatorio. EMOtv estima una expresión; no diagnostica. El aplicativo sigue la revisión 4 del
contrato (`docs/mobile-api.md`).

## Requisitos

- Android Studio con el JDK incluido, o un JDK 17 o posterior.
- Un teléfono, una tablet o un emulador con Android 8.0 (API 26) o posterior.
- El servidor de EMOtv en funcionamiento, en la misma red que el dispositivo.

## Permisos

El aplicativo pide un solo permiso:

| Permiso | Para qué |
| --- | --- |
| `INTERNET` | Comunicarse con el servidor de EMOtv |

AndroidX agrega también `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. Es un permiso interno
del propio aplicativo (nivel de firma): no se le muestra a la persona ni da acceso a nada
del dispositivo.

## Configurar la dirección del servidor

La URL del servidor no se versiona: se lee de `local.properties`, en la raíz del proyecto
(Android Studio crea ese archivo y Git lo ignora).

```properties
# Versión de depuración: puede usar HTTP en la red local.
emotv.apiUrl.debug=http://192.168.1.50:8000/
# Versión final: debe usar HTTPS (la compilación falla si no).
emotv.apiUrl.release=https://192.168.1.50/
```

1. Averigua la IP del equipo que ejecuta el servidor en la red local:
   - Windows: `ipconfig` y busca «Dirección IPv4» del adaptador wifi o Ethernet.
   - Linux o macOS: `ip addr` o `ifconfig`.
2. Escribe esa IP en `local.properties`, con el puerto en el que escucha el servidor y la
   barra final. Sin `emotv.apiUrl.debug`, la versión de depuración usa
   `http://10.0.2.2:8000/`, que en el emulador apunta al equipo anfitrión.
3. Comprueba desde el navegador de la tablet que `http(s)://IP:puerto/mobile/status`
   responde. Si no responde, revisa que la tablet esté en la misma red y que el
   cortafuegos del equipo permita conexiones entrantes en ese puerto.
4. Vuelve a compilar: la URL queda fija en el APK (`BuildConfig.API_BASE_URL`). Si la IP
   del servidor cambia, hay que compilar de nuevo. Conviene reservarle una IP fija en el
   router.

Al abrirse, el aplicativo consulta `GET /mobile/status`, comprueba la versión mínima y
avisa si no hay conexión. En la versión de depuración, ese aviso muestra también la
dirección del servidor que se intentó.

### Con la tablet conectada por USB (desarrollo)

El `docker-compose.yml` del servidor publica la web y la API solo en `127.0.0.1:8080` del
equipo, así que la tablet no la alcanza por la red. Por USB, `adb reverse` hace que el
`127.0.0.1:8080` de la tablet llegue al del equipo:

```properties
emotv.apiUrl.debug=http://127.0.0.1:8080/
```

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse tcp:8080 tcp:8080
```

Hay que repetir `adb reverse` cada vez que se reconecta el cable o se reinicia la tablet.
`10.0.2.2` solo existe en el emulador; en una tablet física no lleva a ningún lado.

### Si sigue diciendo que no hay conexión

| Causa | Cómo se nota | Qué hacer |
| --- | --- | --- |
| La URL apunta a `10.0.2.2` en una tablet física | Siempre «No hay conexión» | Usar `adb reverse` o la IP del equipo |
| Puerto equivocado | «No hay conexión» | La web de Docker escucha en `8080`; la API (`8000`) no se publica fuera de Docker |
| El servidor escucha solo en `127.0.0.1` | Funciona en el equipo, no desde la tablet por wifi | `adb reverse`, o publicar el puerto en la red (`WEB_BIND_ADDRESS`) |
| Contenedores anteriores a la API móvil | «El servidor no respondió como se esperaba»: `/mobile/status` devuelve la página web | Reconstruir los contenedores del servidor |
| HTTPS con certificado no reconocido (Caddy `tls internal`, autofirmado) | «No hay conexión» | Ver la sección siguiente |

### Por qué el servidor debe ofrecer HTTPS

- Por el dispositivo pasan la contraseña de la cuenta, sus tokens, las respuestas de los
  cuestionarios y el texto de los reportes. Sin cifrado, cualquier equipo de la misma red
  wifi puede leerlos o modificarlos.
- El servidor, en producción, rechaza toda ruta `/mobile` que no llegue por HTTPS
  (`403 https_required`).
- La versión final del aplicativo solo permite HTTPS (`network_security_config.xml` no
  admite tráfico sin cifrar) y solo confía en los certificados de autoridades del
  sistema. La versión de depuración permite HTTP solo para el desarrollo local.

Un certificado autofirmado, o el de una autoridad instalada a mano en la tablet, no basta
para la versión final, porque Android no confía en las autoridades que instala la persona.
En la red local hay dos opciones:

- Un nombre de dominio con un certificado de una autoridad reconocida (por ejemplo, Let's
  Encrypt con la validación por DNS) que resuelva a la IP local del servidor, con Caddy o
  nginx delante de la API. En ese caso, `emotv.apiUrl.release` usa el nombre, no la IP.
- El proxy TLS de la institución, si ya publica el servidor con un certificado válido.

Para una prueba rápida en la red local se puede usar la versión de depuración con HTTP,
pero solo con datos de prueba, nunca con personas evaluadas reales.

## Compilar y probar

Desde la raíz del proyecto (PowerShell):

```powershell
.\gradlew.bat testDebugUnitTest            # pruebas unitarias (API falsa, sin servidor)
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug                # app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat connectedDebugAndroidTest    # pruebas de interfaz, con un dispositivo conectado
.\gradlew.bat installDebug                 # instala la versión de depuración
```

Ninguna prueba llama al servidor real. Las pruebas de interfaz incluyen
`DeviceStorageTest`, que revisa que en el almacenamiento del aplicativo no haya imágenes,
que cerrar sesión borre el token y que se borre el hash del PIN de versiones anteriores.
Conviene ejecutarla también después de una demostración.

## APK firmado para la demostración

La versión final se compila con R8: minifica y ofusca el código, quita lo que no se usa y
elimina las llamadas a `android.util.Log`, así que la versión final no escribe registros
de depuración. Las reglas están en `app/src/main/keepRules/rules.keep`.

**El archivo de firma y sus contraseñas nunca entran al repositorio.** `.gitignore`
excluye `keystore.properties`, `*.jks` y `*.keystore`. Guarda el archivo de firma fuera de
la carpeta del proyecto y haz una copia de seguridad: si se pierde, las tablets no podrán
actualizar el aplicativo sin desinstalarlo.

1. Crea el archivo de firma una sola vez, fuera del repositorio. `keytool` viene con el JDK
   (en Android Studio, `jbr\bin\keytool.exe`) y pide las contraseñas sin mostrarlas:

   ```powershell
   keytool -genkeypair -v -keystore C:\firmas\emotv-release.jks -alias emotv -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Crea `keystore.properties` en la raíz del proyecto (no se versiona) con la ruta y las
   contraseñas que elegiste:

   ```properties
   storeFile=C:/firmas/emotv-release.jks
   storePassword=<contraseña del almacén>
   keyAlias=emotv
   keyPassword=<contraseña de la clave>
   ```

3. Revisa que `emotv.apiUrl.release` en `local.properties` apunte al servidor con HTTPS.
4. Compila:

   ```powershell
   .\gradlew.bat assembleRelease
   ```

   El APK queda en `app\build\outputs\apk\release\app-release.apk`. Sin
   `keystore.properties`, Gradle genera `app-release-unsigned.apk`, que no se puede
   instalar.

5. Comprueba la firma e instala con la tablet conectada por USB (depuración USB activada):

   ```powershell
   & "$env:LOCALAPPDATA\Android\Sdk\build-tools\<versión>\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-release.apk
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\release\app-release.apk
   ```

   También se puede copiar el APK a la tablet e instalarlo desde el gestor de archivos,
   permitiendo la instalación desde esa fuente.

R8 deja `app\build\outputs\mapping\release\mapping.txt`, que sirve para leer las trazas de
un fallo de esa compilación. Guárdalo junto al APK, pero no lo publiques.

## Privacidad en el dispositivo

- Sin cámara: el aplicativo no captura ni recibe imágenes de personas.
- Los datos de las sesiones, los reportes y las estadísticas viven solo en memoria y se
  descartan al cerrar la pantalla o la sesión.
- En el dispositivo solo queda el token de renovación, cifrado con una clave del Android
  Keystore, y se borra al cerrar sesión. Al abrirse, el aplicativo borra el hash del PIN
  que guardaban las versiones anteriores.
- Sin copias de seguridad (`allowBackup="false"` y reglas de exclusión), sin caché de
  disco HTTP y, en la versión final, con `FLAG_SECURE` en las pantallas con sesiones o
  reportes. La versión de depuración permite capturas de pantalla para documentar el
  proyecto; por eso nunca se usa con datos de personas evaluadas reales.
- Sin SDKs de analítica, publicidad ni reporte de fallos.
