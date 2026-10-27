# Wochenzentrale für Windows

Deine Woche als Live-Hintergrund über alle Monitore, im selben Stil wie die Handy-App. Liest deine Kalender über die privaten iCal-Adressen (nur lesen), aktualisiert sich jede Minute.

## Installieren
1. `Wochenzentrale.exe` aus dem neuesten Release `windows-N` herunterladen.
2. Liegt eine vorbereitete `config.json` auf dem Desktop oder in Downloads, übernimmt das Programm sie beim Start automatisch und räumt sie vom Desktop weg (Kopie in `%LOCALAPPDATA%\Wochenzentrale\config.importiert.json`).
3. Einmal mit `--install` starten (oder `install.bat` doppelklicken). Das Programm kopiert sich nach `%LOCALAPPDATA%\Wochenzentrale` und startet ab jetzt mit Windows. Ohne vorbereitete Datei öffnet es `config.json` zum Ausfüllen.

## Einstellungen (`config.json`)
- `theme`: system, mondsilber, platin, obsidian, leere, grimoire, glut, frost, hain, klassisch
- `monitors`: Layout je Monitor, Hauptmonitor zuerst: `week`, `3days`, `day`
- `glow`, `caps`, `frame` (round/brackets/engraved), `font` (sans/serif/mono), `fx` (none/stars/embers/snow/circle): leer lassen für die Vorgabe des Stils
- `margin_top`, `margin_bottom`, `margin_left`, `margin_right`: Ränder in Pixeln (bezogen auf 1080p), z. B. für Seelen-Toolbar oben und Dock unten
- `fill`: Farbstärke der Blöcke (0.08–0.45), `by_subject`: Farbe je Fach

Die iCal-Adressen sind geheim: Sie stehen nur in deiner lokalen `config.json`, nie im Repo.

## Beenden / Entfernen
`Wochenzentrale.exe --stop` beendet, `--uninstall` entfernt den Autostart.
