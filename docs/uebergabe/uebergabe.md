# Beamer-Fotos – Übergabe (einzige Datei, wird laufend überschrieben)

Stand: 27.11.2026 · `main` = Version 0.6.0 (versionCode 7) · Regeln in `CLAUDE.md`, Verlauf in Git und `CHANGELOG.md`.

## Projekt in drei Sätzen
Beamer-Fotos: Android-TV-App (Kotlin, Compose, `com.beamerfotos.app`, ab Android 9) für den XGIMI MoGo 4 Laser. Handys (iPhone und Android) senden Fotos, Videos und Musik per QR-Code über eine Browser-Seite an einen kleinen Webserver in der App; die App zeigt sie als Diashow, spielt Musik und Radio. Repository `boerni328-bene/Beamer-Fotos` (öffentlich), Build nur über GitHub Actions, Installation über die App „Downloader“.

## Stand
- Vom Inhaber geprüft: 0.1.0 und 0.2.0. Auftrag am 27.11.: 0.3.0 bis 0.6.0 ohne Rückfrage bauen, danach testet er alles zusammen.
- Nicht auf dem Gerät geprüft: 0.2.1 bis 0.6.0. Für 0.4.0 gibt es kein eigenes Release, weil der Build am WAKE_LOCK scheiterte; die Änderungen stecken in 0.5.0.
- 0.3.0, Gerät und USB:
  - `data/MediaLibrary` liest Fotos und Videos über den MediaStore (`DATA`-Pfad, „external“ deckt alle Speicher ab) und bildet je Ordner ein Album (`Location.readOnly`, `Photo.readOnly`).
  - `ui/DeviceScreen` mit Berechtigungsabfrage; „Alle zeigen“ läuft über `Screen.Show("media:<root>")`.
  - Fremde Fotos sind nur lesbar; „In Album kopieren“ nutzt `PhotoStore.move` mit Kopie, im Hintergrund.
- 0.4.0, Videos:
  - `Names.isVideo`/`isMedia`, Upload bis 8 GB.
  - Vorschau über `MediaMetadataRetriever`, Abspielzeichen im Raster.
  - `ui/VideoPlayer` (Media3 ExoPlayer + PlayerView). In der Diashow bis zum Ende abspielen, bei Fehlern ein Hinweis und dann weiter; ⏪⏩ springen 10 s.
- 0.5.0, Bild verbessern:
  - Die Farbmatrix steckt in `data/Picture` (getestet). „Automatisch“ arbeitet mit Grenzwerten aus dem Vorschaubild (`ImageLoader.levels`).
  - Regler in `ui/PicturePanel`, erreichbar über Menü → Bild und über die Einstellungen. Gilt für alle Fotos, nicht für Videos.
- 0.6.0, Musik:
  - `music/MusicPlayer`: ein ExoPlayer für die ganze App. Er pausiert in `onStop` und läuft in `onStart` weiter; bei Videos geht die Lautstärke auf 0 (`duck`).
  - `music/RadioStore`: Sender in den Prefs, Suche über radio-browser.info, .pls/.m3u werden aufgelöst. HLS-Streams laufen über `media3-exoplayer-hls`, für http-Streams ist `usesCleartextTraffic` gesetzt.
  - `music/MusicLibrary`: gesendete Musik in `<Speicher>/Musik` und MediaStore-Audio (READ_MEDIA_AUDIO).
  - Seite `ui/MusicScreen`. Handy-Seite: Musik ⏯/⏭, Sender hinzufügen (`POST /api/radio`), Musik senden.
- Berechtigungen: INTERNET, ACCESS_NETWORK_STATE, READ_MEDIA_IMAGES/VIDEO/AUDIO, READ_EXTERNAL_STORAGE (max 32). WAKE_LOCK aus Media3 wird mit `tools:node="remove"` entfernt.

## Entscheidungen (Inhaber)
- 26.11.: QR-Code und Browser, keine Handy-App; Aufbau wie Tabletten-Checker; Repository öffentlich.
- 27.11.:
  - „Medien lesen“ ist freigegeben.
  - Musik nur innerhalb der App, kein Hintergrunddienst.
  - Trennung nach Handy innerhalb eines Albums.
  - Versionen bis 0.6.0 ohne Rückfrage.

## Offen / nächste Schritte
1. Inhaber testet 0.6.0 auf dem Beamer. Besonders prüfen:
   - Berechtigungsdialoge, USB-Stick (wird er im MediaStore gefunden?), Videos in MKV und AVI.
   - Bildregler mit der Fernbedienung, Radio-Suche und -Wiedergabe, Musik während Videos.
2. Risiken:
   - Erkennt der MoGo 4 den USB-Stick nicht im MediaStore, wäre die Alternative die Ordnerauswahl per Storage Access Framework.
   - Codecs von AVI- und MKV-Dateien.
   - PlayerView (SurfaceView) mit Compose-Einblendungen.
3. Ideen: Bildeinstellungen je Foto; Zoom-Tasten auf der Handy-Seite; Fotos auf dem Handy verkleinern. Offene Frage: Lizenz (Moltobene: GPL-3.0)?

## Arbeitsweise
- Push auf `main` baut; neue `versionName` + CHANGELOG-Abschnitt ergibt ein Release (mit `beamer-fotos.apk` ohne Versionsnummer für einen festen Download-Link).
- Nach jedem Release `tools\apk-holen.ps1` ausführen → Kopie in `apk\` (nicht im Repository).
- Fehler: `gh run view <id> --log-failed`. Neue Bibliotheken bringen manchmal Berechtigungen mit: der Build prüft gegen ERLAUBT in `build.yml`.
- Signaturschlüssel: `D:\Claude\Projekte\Beamer-Fotos-Signatur` (Fingerabdruck steht in `build.yml`).
