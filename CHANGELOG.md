# Änderungsprotokoll

Alle wichtigen Änderungen an der App werden hier festgehalten.
Die neueste Version steht oben. Versionsschema: Major.Minor.Patch.

## [0.10.0] – 2026-11-27 (versionCode 15)

### Neu
- **Handy als Fernbedienung:** Die Handy-Seite zeigt, was am Beamer läuft (Foto x von y, Album), eine Bildleiste zum Antippen und das aktuelle Foto zum Vergrößern mit zwei Fingern (verschieben mit einem Finger, dazu Knöpfe − / Ganzes Foto / +). Radiosender lassen sich direkt antippen.
- **Schnell senden** (ab Werk aus, Haken auf der Handy-Seite): Große Fotos (JPG, PNG, WebP) werden vor dem Senden auf 4K (3840 Pixel lange Seite, JPEG-Qualität 92) verkleinert – für den Beamer ohne sichtbaren Unterschied, deutlich schneller. Das Aufnahmedatum wird getrennt mitgeschickt und im Foto gespeichert. Kleinere Fotos und HEIC bleiben unverändert.

## [0.9.0] – 2026-11-27 (versionCode 14)

### Neu
- **Fotos je Bild:** Einstellungen → „Fotos je Bild“: „Hochformat paarweise“ zeigt zwei aufeinanderfolgende Hochformat-Fotos nebeneinander; „Collage“ zeigt bis zu 4 Fotos zusammen (2 nebeneinander, 1 groß + 2, oder 2×2). Videos stehen weiterhin allein. Bilder mit mehreren Fotos bleiben etwas länger stehen.
- **Zeitleiste:** Startseite → „Zeitleiste“: alle Fotos (eigene, Gerät, USB) nach Jahr und Monat des Aufnahmedatums, jeweils als Diashow.
- **Heute vor x Jahren:** Gibt es Fotos vom heutigen Kalendertag aus früheren Jahren, erscheint auf der Startseite ein eigener Knopf; in der Diashow steht „vor x Jahren“ beim Datum.

## [0.8.2] – 2026-11-27 (versionCode 13)

### Neu
- **USB-Stick als Speicher:** Wird jetzt über die Speicherverwaltung von Android erkannt. Fotos und Videos liegen sichtbar unter `Pictures/Beamer-Fotos`, Musik unter `Music/Beamer-Fotos` auf dem Stick (bleibt beim Deinstallieren erhalten, am PC sofort zu finden).
- **Alles verschieben:** Einstellungen → „Alle Fotos und Musik verschieben nach …“ (z. B. vom Beamer auf den USB-Stick); neue Fotos werden dann dort gespeichert.
- **Musik verwalten:** Gesendete Musik lässt sich auf der Musikseite einzeln oder ganz löschen und zwischen Beamer und USB-Stick verschieben.
- Erscheint der Stick nicht, zeigen die Einstellungen, welche Speicher Android meldet.

## [0.8.1] – 2026-11-27 (versionCode 12)

### Behoben
- Handy-Seite in Edge/Chrome (Android): „🎵 Musik“ öffnete „No access“ (Mikrofonzugriff verlangt). Jetzt öffnet sich die allgemeine Dateiauswahl ohne Aufnahme.

## [0.8.0] – 2026-11-27 (versionCode 11)

### Neu
- **Bilderrahmen:** Einstellungen → Bilderrahmen: Diashow startet nach 2, 5, 10 oder 30 Minuten ohne Bedienung von selbst, auf Wunsch auch gleich beim Öffnen der App; Uhrzeit und Datum in der Diashow einblendbar.
- **Fortsetzen:** Jede Diashow (je Album, Absender, „Alle“) beginnt dort, wo sie zuletzt war. Startseite: „Weiter: <Album> (Foto x/y)“.
- **Favoriten:** In der Diashow ▲ → „☆ Favorit“; im Album über „Auswählen“. Startseite: „★ Favoriten“ als eigene Diashow.
- **Ausblenden:** „Nicht mehr zeigen“ in der Diashow bzw. „Ausblenden“ im Album – das Foto bleibt gespeichert, erscheint aber nicht mehr in der Diashow (im Album blass mit „ausgeblendet“). Einstellungen → „Ausgeblendete Fotos wieder zeigen“.
- **Doppelte Fotos:** Schickt jemand ein Foto, das schon auf dem Beamer ist (gleicher Inhalt), wird es nicht noch einmal gespeichert; die Handy-Seite meldet „schon vorhanden“.

## [0.7.1] – 2026-11-27 (versionCode 10)

### Behoben
- Handy-Seite in Firefox: „🎬 Videos“ und „🎵 Musik“ meldeten weiterhin „No camera access“. Firefox öffnet dort jetzt die allgemeine Dateiauswahl ohne Aufnahme; nicht passende Dateien werden mit Hinweis ausgelassen. Chrome und Safari unverändert.

## [0.7.0] – 2026-11-27 (versionCode 9)

### Neu
- **„Automatisch verbessern“ deutlich umfassender:** je Foto Weißabgleich (Farbstich weg, Stimmung bleibt zur Hälfte), Tonwertkorrektur, Aufhellen zu dunkler bzw. Abdunkeln zu heller Fotos, kräftigere blasse Farben und Schärfen. Die Fotos werden dafür beim Laden berechnet (dauert einen Moment), die Dateien selbst bleiben unverändert.
- **Schärfe** als eigener Regler (Bild-Menü und Einstellungen), auch ohne „Automatisch“.
- **Musik im Diashow-Menü:** ▲ → Menüpunkt „Musik“: ▲ ▼ schaltet durch die gemerkten Radiosender (bei eigener Musik durch die Stücke), OK hält an bzw. spielt weiter; ohne Musik startet OK den ersten Sender.

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
