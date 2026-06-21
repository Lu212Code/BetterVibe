# BetterVibe

**KI-gestützter Coding-Assistent** – Overlay & CLI mit Multi-Provider-Unterstützung.

BetterVibe ist ein AI Coding Assistant, der als transparentes JavaFX-Overlay oder als Terminal-CLI läuft. Er überwacht dein Projekt, reviewt Code-Diffs automatisch, beantwortet Fragen zum Code und merkt sich den Kontext über Sessions hinweg.

## Features

- **Overlay & CLI** – Wahl zwischen einem stets im Vordergrund schwebenden JavaFX-Overlay oder einer Terminal-CLI (`--mode overlay|cli`).
- **Multi-Provider AI** – Unterstützt OpenAI, Google Gemini und lokales Ollama. Beliebig kombinier- und wechselbar.
- **Automatischer Code-Review** – Globaler Hotkey (Ctrl+F9) oder Slash-Befehl `/review` analysiert Git-Diffs und Dateiänderungen mit AI-Kontext.
- **Auto-Review-Modus** – Überwacht das Projekt auf Änderungen und reviewt automatisch (konfigurierbar: `auto`/`manual`).
- **Chat mit AI** – Stelle Fragen zum Projekt. Der Kontext umfasst Projektstruktur, indexierte Klassen, TODOs, Chathistorie und uncommittete Diffs.
- **Projekt-Indexierung** – Scannt Quellcodedateien, extrahiert Klassen und Methoden und reichert AI-Anfragen mit Strukturwissen an.
- **Session-Memory** – Behält die letzten Nachrichten im Prompt; komprimiert ältere Nachrichten automatisch per AI in Zusammenfassungen.
- **Expense-Tracking** – Erfasst Tokens und Kosten pro API-Call. Abrufbar via `/cost`.
- **TODO-Scanner** – Findet `TODO`, `FIXME`, `HACK`, `XXX` und weitere Markierungen im Projekt (`/todos`).
- **Globale Hotkeys** – Systemweite Tastenkürzel (konfigurierbar via `/config bind`).
- **Multi-Project-Management** – SQLite speichert projektspezifische Ziele, Provider und Modi.
- **Goal-Tracking** – Setze ein Projektziel (`/goal <text>`), das in jeden System-Prompt eingefügt wird.
- **i18n** – Englisch und Deutsch, zur Laufzeit umschaltbar.

## Technologien

| Technologie | Version | Zweck |
|---|---|---|
| Java | 17 | Sprachlevel |
| JavaFX | 21.0.6 | Overlay-UI (Controls, FXML, Swing, Web) |
| Lanterna | 3.1.2 | Terminal-UI |
| JNativeHook | 2.2.2 | Globale Tastatur-Hooks |
| SQLite (xerial JDBC) | 3.49.1.0 | Lokale Datenbank |
| OkHttp | 4.12.0 | HTTP-Client für AI-Provider |
| Gson | 2.12.1 | JSON-Parsing |
| JLine | 3.30.12 | Terminal-Input |
| JUnit Jupiter | 5.12.2 | Tests |
| Maven | – | Build-System |

## Installation

### Voraussetzungen

- Java 17 oder höher
- Maven (zum Bauen)
- Optional: Git (für Diff-Funktionalität)

### Bauen

```bash
git clone <repository-url>
cd BetterVibe
mvn clean package
```

Das Ergebnis ist eine Fat-JAR unter `target/BetterVibe-0.0.1-SNAPSHOT.jar` mit allen Abhängigkeiten.

## Usage

### Grundlegender Start

```bash
java -jar target/BetterVibe-0.0.1-SNAPSHOT.jar --project /pfad/zu/deinem/projekt
```

### Mit API-Keys

```bash
java -jar target/BetterVibe-0.0.1-SNAPSHOT.jar \
  --project /pfad/zu/deinem/projekt \
  --openai-key sk-... \
  --gemini-key dein-gemini-key \
  --ollama-url http://localhost:11434
```

### CLI-Modus

```bash
java -jar target/BetterVibe-0.0.1-SNAPSHOT.jar --mode cli --project /pfad/zu/deinem/projekt
```

### Per Maven-Plugin (JavaFX)

```bash
mvn javafx:run -Djavafx.mainClass=dev.lu212.bv.BetterVibeApp
```

Falls `--project` nicht angegeben wird, verwendet BetterVibe das aktuelle Arbeitsverzeichnis.

## Slash-Befehle

| Befehl | Beschreibung |
|---|---|
| `/review` | Code-Review der aktuellen Änderungen auslösen |
| `/status` | Status des Projekts anzeigen |
| `/goal <text>` | Projektziel setzen |
| `/config` | Konfiguration anzeigen/ändern |
| `/todos` | TODO-Kommentare im Projekt auflisten |
| `/reindex` | Projekt neu indexieren |
| `/clear` | Chat-Verlauf löschen |
| `/help` | Hilfe anzeigen |

## Konfiguration

BetterVibe speichert die Konfiguration in:
- **JSON-Config:** `~/.bettervibe/config.json`
- **SQLite-Datenbank:** `~/.bettervibe/bettervibe.db`

## Globale Hotkeys

| Hotkey | Aktion |
|---|---|
| `Ctrl+F9` | Code-Review auslösen |
| `Ctrl+Shift+F11` | Overlay ein-/ausblenden |

Die Hotkeys sind via `/config bind` anpassbar.

## Projektstruktur

```
dev.lu212.bv
├── BetterVibeApp.java       – Einstiegspunkt
├── AppDefaults.java         – Standardpfade, Version
├── config/AppConfig.java    – JSON-Konfiguration
├── ai/                      – AI-Provider, Memory, Expense-Tracking
│   ├── providers/           – OpenAI, Gemini, Ollama
│   ├── memory/              – Langzeitgedächtnis
│   └── expense/             – Kostenverfolgung
├── db/                      – SQLite-Repositories
├── ui/                      – Overlay & CLI
│   ├── overlay/             – JavaFX-Overlay
│   ├── cli/                 – Terminal-CLI
│   └── shared/              – Geteilte UI-Komponenten
├── input/                   – Globales Hotkey-Handling
├── indexer/                 – Projekt-Indexierung
├── watch/                   – Dateisystem-Überwachung
├── util/                    – Git, Token-Zähler, Datei-Utils
└── i18n/                    – Sprachressourcen (DE/EN)
```

## Lizenz

Proprietär.
