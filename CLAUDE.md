# CLAUDE.md – Arbeitsregeln für das Projekt Beamer-Fotos

## Beim Start jeder Sitzung

Zuerst `docs/uebergabe/uebergabe.md` lesen (einzige Übergabedatei, wird überschrieben, etwa 60 Zeilen).

## Projektbeschreibung

**Kernsatz** (wörtlich gleich in README und Releases): _Beamer-Fotos holt Fotos von jedem Handy per QR-Code auf den Beamer und zeigt sie als Diashow – ohne App auf dem Handy, ohne Cloud._

**Für wen:** Projektinhaber mit XGIMI MoGo 4 Laser (Google TV); Fotos vom eigenen Handy und von Gästen (Android und iPhone).

**Entscheidungen des Projektinhabers (26.11.2026):**
- Übertragung per QR-Code und Browser-Seite, keine App auf den Handys.
- Aufbau wie Tabletten-Checker (GitHub Actions, Releases), Repository öffentlich.

**Bewusst nicht:** keine Cloud, kein Konto, keine Werbung, kein Tracking. Neue Berechtigungen nur nach Freigabe (der Build prüft die APK gegen eine Erlaubt-Liste: INTERNET, ACCESS_NETWORK_STATE).

## Arbeitsweise

- Vorbild sind Moltobene und Tabletten-Checker: gleiche Werkzeuge und Versionen, gleicher Build.
- Gebaut wird nur über GitHub Actions (kein Android Studio/Java auf dem PC). Jeder Push auf `main` baut; eine neue `versionName` ergibt ein Release.
- Jede Version: `versionCode` und `versionName` in `app/build.gradle.kts` erhöhen und Abschnitt `## [x.y.z] – Datum (versionCode n)` in `CHANGELOG.md` ergänzen, sonst bricht der Build ab.
- Nach jedem erfolgreichen Release die APK auf den PC holen: `powershell -ExecutionPolicy Bypass -File tools\apk-holen.ps1` (landet in `apk\`, nicht im Repository, Prüfsumme wird kontrolliert).
- Nach jedem erfolgreichen Release die APK auf den PC holen: `powershell -ExecutionPolicy Bypass -File toolspk-holen.ps1` (landet in `apk`, nicht im Repository, Prüfsumme wird kontrolliert).
- Schritt für Schritt: kleine Versionen, der Projektinhaber prüft jede auf dem Beamer.
- Projektinhaber ist kein Entwickler: Deutsch, kurz, vor größeren Entscheidungen fragen.
- Texte der App: ohne direkte Anrede, neutral und knapp.
- Signaturschlüssel: liegt außerhalb des Repositorys (`D:\Claude\Projekte\Beamer-Fotos-Signatur`) und als Secrets auf GitHub. Nie ins Repository, nie ersetzen.
- Bedienung nur mit Fernbedienung: Jedes Element muss mit den Pfeiltasten erreichbar sein und sichtbar markiert werden (`tvFocus`, `TvButton`).

## Aufbau des Codes

- `server/UploadServer.kt` – Webserver im WLAN (Port 8080–8099), Zugangscode aus dem QR-Code, Upload per PUT mit Rohdaten, Fernbedienungsbefehle; `server/Http.kt` – HTTP-Grundlagen (getestet); `server/Network.kt` – eigene IP.
- `assets/web/index.html` – Upload-Seite für die Handys (ohne Bibliotheken).
- `data/PhotoStore.kt` – Speicherorte (intern, USB über `getExternalFilesDirs`, ohne Berechtigung), Alben = Ordner, Speichern, Löschen; `data/Names.kt` – Namen bereinigen (getestet); `data/Settings.kt` – Einstellungen, Zugangscode, Drehungen, Reihenfolge (getestet).
- `image/ImageLoader.kt` – ImageDecoder (HEIC, EXIF-Drehung), Vorschaubilder mit Cache.
- `ui/HomeScreen.kt` (QR-Code + Alben), `ui/AlbumScreen.kt`, `ui/SlideshowScreen.kt` (Diashow, Menü, Bildleiste), `ui/SettingsScreen.kt`, `ui/Components.kt`; `MainActivity.kt` (Seitenwahl ohne Navigationsbibliothek, Server läuft zwischen onStart und onStop).
