# Wochenzentrale

Native Android-App (Kotlin, Compose). Zeigt deinen Tag und deine Woche als Zeitraster mit Jetzt-Linie.

- Liest die Kalender direkt vom Handy (Google, FH-Stundenplan, Dienstplan, deine Plan-Blöcke). Kein Login, kein Token, nichts verlässt das Handy.
- Tag- und Wochenansicht, Farben nach Art: Schlaf, FH, Arbeit, Training, Lernen, Familie/Freundin, Projekt.
- Homescreen-Widget "Heute" (nächste Termine).
- Live-Hintergrundbild mit dem Tagesplan (Einstellungen, Hintergrund, Live-Hintergrund, Wochenzentrale).

## Bauen
Repo `RustamUR-IT/Wochenzentrale` anlegen, Dateien pushen. GitHub Actions führt Tests und `assembleDebug` aus und legt auf `main` ein Release `build-N` mit `Wochenzentrale.apk` an. APK aufs Handy laden und installieren.

## Planer
Das Planer-Skript schreibt weiter die `[auto-plan]`-Blöcke in Google Calendar. Die App zeigt sie an, sobald Google sie aufs Handy synchronisiert hat.
