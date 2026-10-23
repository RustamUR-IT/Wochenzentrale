# Wochenzentrale für Windows

Deine Woche als Live-Hintergrund über alle Monitore, im selben Stil wie die Handy-App. Liest deine Kalender über die privaten iCal-Adressen (nur lesen), aktualisiert sich jede Minute.

## Installieren
1. `Wochenzentrale.exe` aus dem neuesten Release `windows-N` herunterladen.
2. Einmal mit `--install` starten (oder `install.bat` doppelklicken). Das Programm kopiert sich nach `%LOCALAPPDATA%\Wochenzentrale`, startet ab jetzt mit Windows und öffnet `config.json`.
3. In `config.json` bei jedem Kalender die `url` eintragen, speichern. Spätestens nach einer Minute erscheint der Hintergrund.

## Einstellungen (`config.json`)
- `theme`: system, mondsilber, platin, obsidian, leere, grimoire, glut, frost, hain, klassisch
- `monitors`: Layout je Monitor, Hauptmonitor zuerst: `week`, `3days`, `day`
- `glow`, `caps`, `frame` (round/brackets/engraved), `font` (sans/serif/mono), `fx` (none/stars/embers/snow/circle): leer lassen für die Vorgabe des Stils
- `fill`: Farbstärke der Blöcke (0.08–0.45), `by_subject`: Farbe je Fach

Die iCal-Adressen sind geheim: Sie stehen nur in deiner lokalen `config.json`, nie im Repo.

## Beenden / Entfernen
`Wochenzentrale.exe --stop` beendet, `--uninstall` entfernt den Autostart.
