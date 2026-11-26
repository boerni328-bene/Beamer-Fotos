# Beamer-Fotos – Übergabe (einzige Datei, wird laufend überschrieben)

Stand: 26.11.2026 · `main` = Version 0.1.0 (versionCode 1) · Regeln in `CLAUDE.md`, Verlauf in Git und `CHANGELOG.md`.

## Projekt in drei Sätzen
Beamer-Fotos: Android-TV-App (Kotlin, Compose, `com.beamerfotos.app`, ab Android 9) für den XGIMI MoGo 4 Laser. Handys (iPhone und Android) senden Fotos per QR-Code über eine Browser-Seite an einen kleinen Webserver in der App; die App zeigt sie als Diashow. Repository `boerni328-bene/Beamer-Fotos` (öffentlich), Build nur über GitHub Actions, Installation über die App „Downloader“.

## Stand 0.1.0
- Release v0.1.0 gebaut (Lint, Tests, Signatur, Berechtigungen grün), Link `…/releases/latest/download/beamer-fotos.apk` geprüft.
- Upload-Seite mit Testserver im Browser geprüft (3 Fotos, Album, Fernbedienung). App selbst noch nie auf einem Gerät gelaufen.
- Upload: QR-Code mit `http://<IP>:<Port>/?t=<Zugangscode>`; Seite lädt Fotos einzeln per PUT hoch, Name = Album, Wiederholung bei Abbruch.
- Speicher: intern oder USB (App-Ordner auf dem Stick); Wahl in den Einstellungen.
- Diashow: Dauer, 4 Übergänge, Reihenfolge, Ganz/Füllen, unscharfer Hintergrund, Infos, Wiederholen, Live-Einfügen neuer Fotos, QR in der Ecke, Drehen, Zoom 1–3× mit Verschieben, Foto löschen, Bildleiste.
- Handy-Seite: Fernbedienung (zurück, Pause, weiter, Diashow starten).

## Entscheidungen (Inhaber, 26.11.2026)
- Übertragung per QR-Code und Browser, keine Handy-App.
- Aufbau wie Tabletten-Checker, Repository öffentlich.

## Offen / nächste Schritte
1. Inhaber installiert 0.1.0 auf dem Beamer und prüft: QR-Code, Upload von iPhone und Android, Diashow mit der Fernbedienung, USB-Stick.
2. Ideen für später: Videos, Hintergrundmusik, Fotos von einem USB-Stick zeigen, die nicht über die App kamen (bräuchte Berechtigung), Fotos auf dem Handy verkleinern für schnelleres Senden.
Offene Fragen: Lizenz (Moltobene: GPL-3.0)?

## Arbeitsweise
- Push auf `main` baut; neue `versionName` + CHANGELOG-Abschnitt ergibt ein Release (mit `beamer-fotos.apk` ohne Versionsnummer für einen festen Download-Link).
- Fehler: `gh run view <id> --log-failed`.
- Signaturschlüssel: `D:\Claude\Projekte\Beamer-Fotos-Signatur` (Fingerabdruck steht in `build.yml`).
