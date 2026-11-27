# Beamer-Fotos – Übergabe (einzige Datei, wird laufend überschrieben)

Stand: 27.11.2026 · `main` = Version 0.2.0 (versionCode 2) · Regeln in `CLAUDE.md`, Verlauf in Git und `CHANGELOG.md`.

## Projekt in drei Sätzen
Beamer-Fotos: Android-TV-App (Kotlin, Compose, `com.beamerfotos.app`, ab Android 9) für den XGIMI MoGo 4 Laser. Handys (iPhone und Android) senden Fotos per QR-Code über eine Browser-Seite an einen kleinen Webserver in der App; die App zeigt sie als Diashow. Repository `boerni328-bene/Beamer-Fotos` (öffentlich), Build nur über GitHub Actions, Installation über die App „Downloader“.

## Stand
- 0.1.0 vom Inhaber auf dem Beamer getestet: „funktionierte hervorragend“.
- 0.2.0 (Organisieren):
  - Handy-Seite mit den Feldern Name (Absender) und Album. Ohne Name wird „iPhone/Android + Kennung“ verwendet, ohne Album „Fotos <Datum>“.
  - Gespeichert wird unter `<Speicher>/Fotos/<Album>/<Absender>/<Datei>`. Fotos direkt im Album haben den Absender „“.
  - Album-Ansicht:
    - Filter nach Absender; die Diashow übernimmt den Filter (`Screen.Show.sender`).
    - Auswahlmodus: Alle, Löschen, Verschieben (in ein vorhandenes oder neues Album; zwischen intern und USB wird kopiert).
    - Umbenennen, über `TextInputDialog`. Der neue Schlüssel wird im Stapel ersetzt (`onRenamed`).
  - Diashow: Zoom-Modus über das Menü. ▲▼ gedrückt halten zoomt stufenlos (1–6×, `withFrameNanos`), OK wechselt zum Verschieben, Zurück beendet den Zoom.
  - Drehungen wandern beim Verschieben und Umbenennen mit (`moveRotation`).
- Noch nicht auf dem Gerät geprüft: Texteingabe (Bildschirmtastatur) und gehaltene Tasten (KeyUp) auf der XGIMI-Fernbedienung.

## Entscheidungen (Inhaber)
- 26.11.: QR-Code und Browser, keine Handy-App; Aufbau wie Tabletten-Checker; Repository öffentlich.
- 27.11.:
  - Berechtigung „Medien lesen“ ist freigegeben (READ_MEDIA_IMAGES/VIDEO/AUDIO, bis Android 12 READ_EXTERNAL_STORAGE). Die Erlaubt-Liste in `build.yml` dabei ergänzen.
  - Musik nur innerhalb der App, kein Hintergrunddienst.
  - Trennung nach Handy innerhalb eines Albums.
  - Reihenfolge wie vorgeschlagen.

## Nächste Versionen (Reihenfolge freigegeben)
1. 0.3.0 Gerät und USB:
   - Fotos über MediaStore zeigen (auch Wechseldatenträger), als eigene Quelle bzw. eigene Alben je Ordner, nur lesend.
   - Die Berechtigung erst beim Öffnen dieser Quelle anfragen.
2. 0.4.0 Videos:
   - mp4, mov, mkv, avi mit Media3 ExoPlayer, avi über den Extractor ab Media3 1.4.
   - Upload-Seite: `accept` um `video/*` erweitern, `Names.isImage` zu `isMedia` umbauen.
   - In der Diashow das Video bis zum Ende abspielen.
3. 0.5.0 Bild verbessern: Helligkeit, Kontrast, Sättigung, Wärme, Auto (ColorMatrix), je Foto oder für alle.
4. 0.6.0 Musik:
   - lokale Dateien und Radio (Stream-URL, Eingabe auch über die Handy-Seite), mit Media3.
   - läuft auch ohne Diashow innerhalb der App.

Ideen: Fotos auf dem Handy verkleinern; Zoom-Tasten auf der Handy-Seite. Offene Frage: Lizenz (Moltobene: GPL-3.0)?

## Arbeitsweise
- Push auf `main` baut; neue `versionName` + CHANGELOG-Abschnitt ergibt ein Release (mit `beamer-fotos.apk` ohne Versionsnummer für einen festen Download-Link).
- Nach jedem Release `tools\apk-holen.ps1` ausführen → Kopie in `apk\` (nicht im Repository).
- Fehler: `gh run view <id> --log-failed`.
- Signaturschlüssel: `D:\Claude\Projekte\Beamer-Fotos-Signatur` (Fingerabdruck steht in `build.yml`).
