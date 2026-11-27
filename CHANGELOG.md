# Änderungsprotokoll

Alle wichtigen Änderungen an der App werden hier festgehalten.
Die neueste Version steht oben. Versionsschema: Major.Minor.Patch.

## [0.6.1] – 2026-11-27 (versionCode 8)

### Behoben
- Handy-Seite: Beim Auswählen erschien in manchen Browsern (z. B. Firefox) „No camera access“, weil ein Knopf Fotos, Videos und Musik zugleich anbot. Jetzt gibt es getrennte Knöpfe: „📷 Fotos auswählen“, „🎬 Videos“ und „🎵 Musik“.

## [0.6.0] – 2026-11-27 (versionCode 7)

### Neu
- **Musik und Radio:** Startseite → „Musik“. Läuft mit oder ohne Diashow, solange die App geöffnet ist; bei Videos pausiert die Musik automatisch. Pause, Weiter, Zufall, eigene Lautstärke.
- **Radio:** Sender über das freie Verzeichnis radio-browser.info suchen und merken, oder auf der Handy-Seite mit Name und Stream-Adresse hinzufügen.
- **Eigene Musik:** Musikstücke (MP3, M4A, FLAC, OGG, WAV …) über die Handy-Seite senden; Musik auf dem Beamer und dem USB-Stick nach Alben (neue Berechtigung „Musik“, wird auf der Musikseite angefragt).
- In der Diashow: ▲ → „Musik: Pause/weiter“ und „Nächstes Stück“. Auf der Handy-Seite: Musik ⏯ und ⏭.

### Geändert
- Die von der Abspiel-Bibliothek mitgebrachte Berechtigung WAKE_LOCK wird entfernt (nicht nötig).

## [0.5.0] – 2026-11-27 (versionCode 6)

### Neu
- **Bild verbessern:** In der Diashow ▲ → „Bild“ (oder Einstellungen → Bild): Helligkeit, Kontrast, Farbsättigung und Farbwärme mit ◀ ▶ einstellen, die Wirkung ist sofort zu sehen. „Automatisch verbessern“ gleicht flaue, zu dunkle oder zu helle Fotos einzeln aus (Tonwertkorrektur und etwas mehr Farbe). „Zurücksetzen“ stellt alles auf neutral. Gilt für alle Fotos; die Dateien selbst bleiben unverändert, Videos werden nicht verändert.

## [0.4.0] – 2026-11-27 (versionCode 5)

### Neu
- **Videos:** MP4, MOV, MKV, AVI (auch WEBM, 3GP). Von der Handy-Seite senden („Fotos oder Videos auswählen“, bis 8 GB je Datei) oder vom Beamer und USB-Stick zeigen. In der Diashow läuft jedes Video bis zum Ende mit Ton, dann geht es weiter; OK hält an, ⏪ ⏩ auf der Fernbedienung springen 10 Sekunden. Im Raster tragen Videos ein Abspielzeichen.
- Welche Videos abspielbar sind, hängt von den Decodern des Beamers ab; nicht abspielbare Videos werden mit einem Hinweis übersprungen.

## [0.3.0] – 2026-11-27 (versionCode 4)

### Neu
- **Fotos vom Beamer und vom USB-Stick:** Startseite → „Gerät & USB-Stick“. Zeigt alle Fotos, die schon auf dem Beamer oder einem eingesteckten USB-Stick liegen, nach Ordnern; je Speicher „Alle zeigen“ als Diashow. Diese Fotos werden nur gezeigt, nie verändert oder gelöscht.
- **In Album kopieren:** In einem solchen Ordner „Auswählen“ → Fotos markieren → „In Album kopieren“ (vorhandenes oder neues eigenes Album).
- Neue Berechtigung „Fotos und Videos lesen“; der Beamer fragt beim ersten Öffnen von „Gerät & USB-Stick“ einmal nach.

## [0.2.1] – 2026-11-27 (versionCode 3)

### Behoben
- Handy-Seite: Der in 0.1.0 eingegebene Name landete im Feld „Album“, das Feld „Name“ blieb leer und am Beamer erschien „Android …“. Der alte Name wird jetzt ins Feld „Name“ übernommen. Ohne Namen lassen sich keine Fotos mehr auswählen; unter den Feldern steht, als wer und in welches Album gesendet wird.

### Neu
- **Name ändern:** Im Album einen Absender wählen und „Name ändern“ drücken, z. B. „Android K7Q“ in „Anna“. Gibt es den Namen schon, werden beide zusammengefasst.

## [0.2.0] – 2026-11-27 (versionCode 2)

### Neu
- **Fotos nach Handy getrennt:** Auf der Handy-Seite gibt es jetzt zwei Felder: Name (von wem die Fotos kommen) und Album. Ohne Album landen die Fotos in „Fotos <heutiges Datum>“. Im Album lässt sich oben nach Absender filtern; die Diashow zeigt dann nur dessen Fotos.
- **Fotos auswählen:** Im Album „Auswählen“ drücken, Fotos mit OK markieren (oder „Alle“), dann löschen oder in ein anderes bzw. neues Album verschieben.
- **Album umbenennen.**
- **Stufenloser Zoom bis 6×:** In der Diashow ▲ → „Zoom“. ▲ und ▼ gedrückt halten zoomt fließend, OK wechselt zum Verschieben mit den Pfeiltasten, Zurück beendet den Zoom.
- Hinweis bei neuen Fotos und Infozeile in der Diashow nennen den Absender.

## [0.1.0] – 2026-11-26 (versionCode 1)

### Neu
- **Fotos per QR-Code senden:** Der Beamer zeigt einen QR-Code. Handy ins selbe WLAN, Code mit der Kamera scannen, Fotos auswählen – sie landen direkt auf dem Beamer. Klappt mit iPhone und Android, ohne App auf dem Handy. Jeder Name oder Anlass wird zu einem eigenen Album.
- **Speicherort:** intern oder auf einem eingesteckten USB-Stick (in den Einstellungen wählbar).
- **Diashow:** Anzeigedauer 3 s bis 60 s oder von Hand, Übergänge (Blende, Schieben, Blende mit langsamem Zoom, keiner), Reihenfolge nach Aufnahmezeit, Eingang oder Zufall, ganzes Foto oder Bild füllen, unscharfer Hintergrund bei Hochformat, Album und Datum einblenden, Wiederholen.
- **Bedienung mit der Fernbedienung:** ◀ ▶ blättern, OK Pause, ▲ Menü (Drehen, Zoom bis 3×, Dauer, Übergang, Foto löschen …), ▼ Bildleiste zum Springen.
- **Live-Diashow:** Neue Fotos erscheinen sofort in der laufenden Diashow; auf Wunsch QR-Code in der Ecke, damit Gäste jederzeit Fotos dazugeben.
- **Handy als Fernbedienung:** Auf der Upload-Seite lässt sich die Diashow starten, anhalten und weiterschalten.
