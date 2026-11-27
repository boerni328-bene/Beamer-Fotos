# Beamer-Fotos

_Beamer-Fotos holt Fotos von jedem Handy per QR-Code auf den Beamer und zeigt sie als Diashow – ohne App auf dem Handy, ohne Cloud._

Gebaut für den XGIMI MoGo 4 Laser, läuft auf jedem Gerät mit Google TV oder Android TV ab Android 9.

## So funktioniert es

1. App **Beamer-Fotos** auf dem Beamer öffnen. Links erscheint ein QR-Code.
2. Handy ins **selbe WLAN** wie der Beamer.
3. QR-Code mit der Kamera scannen (iPhone: Kamera-App, Android: Kamera oder Google Lens).
4. Im Browser Namen und auf Wunsch ein Album eingeben, **Fotos auswählen** – fertig. Die Fotos landen auf dem Beamer, getrennt nach Absender.

Es wird keine App auf dem Handy gebraucht. Jeder Gast kann mitmachen. Die Upload-Seite dient auch als Fernbedienung für die Diashow.

## Diashow mit der Fernbedienung

| Taste | Wirkung |
|---|---|
| ◀ ▶ | voriges / nächstes Foto |
| OK | Pause / weiter |
| ▲ | Menü: Pause, Drehen, Zoom (stufenlos bis 6×), Anzeigedauer, Übergang, Bildgröße, Infos, QR-Code, Foto löschen |
| ▼ | Bildleiste: zu einem beliebigen Foto springen |
| Zurück | Menü schließen bzw. Diashow beenden |

Zoom: ▲ und ▼ gedrückt halten zoomt fließend, OK wechselt zum Verschieben mit den Pfeiltasten (und zurück), Zurück beendet den Zoom.

## Fotos ordnen

Im Album oben nach Absender filtern (die Diashow zeigt dann nur dessen Fotos). **Auswählen** → Fotos mit OK markieren → **Löschen** oder **Verschieben** (in ein anderes oder neues Album). Außerdem: **Umbenennen** und **Album löschen**.

Weitere Einstellungen (Startseite → Einstellungen): Reihenfolge (Aufnahmezeit, Eingang, Zufall), Hintergrund bei Hochformat, Wiederholen, neue Fotos sofort zeigen (Live-Diashow für Feiern), Speicherort intern oder USB-Stick, neuer QR-Code, alle Fotos löschen.

## Installation auf dem Beamer

Einmalig:

1. Auf dem Beamer **Einstellungen → System → Info** öffnen und **siebenmal auf „Android TV OS-Build“** klicken. Dann sind die Entwickleroptionen frei.
2. Im **Play Store** die App **„Downloader“** (von AFTVnews) installieren.
3. **Einstellungen → Apps → Sicherheit und Einschränkungen → Unbekannte Quellen**: „Downloader“ erlauben.

Installieren und später aktualisieren:

4. „Downloader“ öffnen und diese Adresse eingeben:
   `https://github.com/boerni328-bene/Beamer-Fotos/releases/latest/download/beamer-fotos.apk`
5. „Installieren“ wählen. Die App erscheint danach in der App-Liste des Beamers.

Ein Update wird genauso installiert; die Fotos bleiben dabei erhalten.

## Gut zu wissen

- Der Beamer nimmt Fotos nur an, solange die App geöffnet ist.
- iPhone-Fotos (HEIC) wandelt das iPhone beim Senden automatisch in JPG um.
- Fotos auf dem USB-Stick liegen im Ordner `Android/data/com.beamerfotos.app/files/Fotos`. Beim Deinstallieren der App löscht Android diesen Ordner.
- Ohne den Code aus dem QR-Code nimmt der Beamer nichts an. „Neuen QR-Code erzeugen“ in den Einstellungen sperrt alte Links.
- Kein Internet nötig außer dem WLAN zu Hause, kein Konto, keine Cloud, keine Werbung.

## Entwicklung

Kotlin und Jetpack Compose. Gebaut wird nur über GitHub Actions (siehe `.github/workflows/build.yml`); jeder Push auf `main` baut, eine neue `versionName` ergibt ein Release.
