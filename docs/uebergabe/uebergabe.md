# Beamer-Fotos – Übergabe (einzige Datei, wird laufend überschrieben)

Stand: 27.11.2026 · `main` = Version 0.10.1 (versionCode 16) · Regeln und Code-Aufbau in `CLAUDE.md`, Verlauf in Git und `CHANGELOG.md`.

## Projekt in drei Sätzen
Beamer-Fotos: Android-TV-App (Kotlin, Compose, `com.beamerfotos.app`, ab Android 9) für den XGIMI MoGo 4 Laser. Handys senden Fotos, Videos und Musik per QR-Code über eine Browser-Seite an einen kleinen Webserver in der App; die App zeigt sie als Diashow, spielt Musik und Radio. Repository `boerni328-bene/Beamer-Fotos` (öffentlich), Build nur über GitHub Actions, Installation über die App „Downloader“.

## Stand
- Vom Inhaber geprüft: bis 0.7.0 („Alles klappt“). 0.8.1: Musik-Auswahl in Edge scheiterte an der fehlenden Mikrofon-Berechtigung von Edge; der Inhaber hat sie erteilt, das Problem ist erledigt.
- Ungeprüft auf dem Beamer: 0.7.1, 0.8.0, 0.8.2, 0.9.0, 0.10.0 (alle Builds grün, APKs in `apk\`).
- 0.8.0: Bilderrahmen (Leerlauf in `App()`, `startWithShow`, `Clock`), Fortsetzen (`resumeKey`, `saveResume`), Favoriten und Ausblenden (Prefs-Sets), Duplikate per SHA-256.
- 0.8.2, USB-Speicher:
  - `PhotoStore.locations()` über `StorageManager.storageVolumes`. Ab API 30 Fotos in `<Stick>/Pictures/Beamer-Fotos` und Musik in `<Stick>/Music/Beamer-Fotos` (`Location.musicRoot`, `PUBLIC_FOLDER`), sonst `Android/data`.
  - MediaLibrary und MusicLibrary blenden diese Pfade aus. Temp-Dateien behalten die Endung (`.<n>.part.jpg`), da FUSE nur passende Typen erlaubt.
  - `moveAllTo` (Einstellungen „Alle Fotos und Musik verschieben nach …“), `moveMusic`/`deleteMusic` (Musikseite). `storageReport()` zeigt in den Einstellungen, was Android meldet, falls der Stick fehlt.
  - Risiko: Schreibrechte auf dem Stick (FUSE). Falls es scheitert, wäre die Alternative MediaStore-Insert oder SAF.
- 0.9.0:
  - `data/Layouts` (getestet): Einstellung „Fotos je Bild“ (`Layout.SINGLE/PAIRS/COLLAGE`, Pref „anordnung“). Paare nur bei zwei Hochformaten (`ImageLoader.isPortrait`, Kopf + EXIF, gemerkt), Collage bis 4, Videos allein.
  - In der Diashow `groupAt`/`groupFrom`, `previousGroup`, `drawGroup` und `drawPhoto(area)`. Zoom und Drehen nur bei einzelnen Fotos. Mehrfachbilder bleiben 1,5× so lange stehen. LRU der großen Bilder 10.
  - `data/Timeline` (getestet), `ui/TimelineScreen`: Schlüssel „zeit:JJJJ“, „zeit:JJJJ-MM“, „heute“ als Diashow-Quelle. Startseite: „Zeitleiste“ und „Heute vor x Jahren“.
- 0.10.0:
  - Handy-Fernbedienung: `RemoteCommand` ist jetzt ein sealed interface (`GoTo`, `ZoomTo`). `UploadServer.show` (`ShowState`) wird von der Diashow gesetzt.
  - Endpunkte `/api/show`, `/api/thumb?i=` (JPEG aus dem Vorschau-Cache, `ImageLoader.thumbJpeg`), `/api/stations`; Befehle `goto`, `zoom`, `station`.
  - Handy-Seite: Bildleiste (40 um das aktuelle Foto), Fingerzoom-Vorschau (16:9, Verschiebung als Anteil wie `pan`), Senderknöpfe, Abfrage alle 2,5 s.
  - „Schnell senden“ (Handy-Seite, localStorage „fastSend“, ab Werk aus): JPG/PNG/WebP mit langer Seite > 3840 werden per Canvas auf 3840 verkleinert (JPEG 0,92). Das EXIF-Datum liest `exifDate()` aus; der Server schreibt es als `exifdate` per ExifInterface zurück.
  - Am PC mit dem Testserver geprüft (`scratchpad/mock.ps1`, Port 8765, Code test42), auf Handy und Beamer noch nicht.

## Entscheidungen (Inhaber)
- 26.11.: QR-Code und Browser, keine Handy-App; Aufbau wie Tabletten-Checker; Repository öffentlich.
- 27.11.:
  - „Medien lesen“ ist freigegeben. Musik nur in der App. Trennung nach Handy im Album.
  - Ideen 1, 2, 4, 5, 6, 8, 10 umgesetzt; 7 nur als Schalter, ab Werk aus.
  - 0.9.0 und 0.10.0 ohne Rückfrage gebaut.
  - Alles wahlweise auf dem USB-Stick; gesendete Musik löschen und verschieben.

## Offen / nächste Schritte
0. USB-Stick: Inhaber-Test 0.10.0 ergab nur „Intern“. Android meldet den Stick (Transcend, wechselbar, mounted, /storage/BE0ACC490ACBFC81, vermutlich NTFS), aber `mkdirs` für Pictures/ und Android/data scheitert: Apps dürfen auf Google TV wohl nur lesen. 0.10.1 hat einen Test „USB-Stick freigeben“ (`data/UsbAccess`, SAF-Ordnerauswahl, Testdatei per DocumentsContract). Ergebnis abwarten. Klappt es, das Speichern auf dem Stick auf SAF umbauen (Tree-URI merken, Schreiben über DocumentsContract, Lesen weiter über den Pfad). Sonst den Stick mit exFAT formatieren oder nur lesen.
1. Rückmeldung des Inhabers zu 0.8.2 bis 0.10.0 abwarten. Besonders prüfen:
   - USB-Stick erscheint und ist beschreibbar.
   - Collage-Speicher und -Geschwindigkeit auf dem MoGo 4.
   - Fingerzoom auf dem iPhone.
   - Schnell senden auf dem iPhone (HEIC bleibt Original).
2. Risiken: AVI/MKV-Codecs, PlayerView mit Compose-Einblendungen, viele Fotos in `ShowState` (Liste wird bei jedem Wechsel kopiert).
3. Ideen: Bildeinstellungen je Foto. Offene Frage: Lizenz (Moltobene: GPL-3.0)?
