#!/usr/bin/env python3
"""Wochenzentrale für Windows: deine Woche als Live-Desktop-Hintergrund.

Liest deine Kalender über die privaten iCal-Adressen (nur lesen), zeichnet sie im gewählten
Stil und setzt das Bild jede Minute als Hintergrund über alle Monitore.

  Wochenzentrale.exe            läuft im Hintergrund (aktualisiert jede Minute)
  Wochenzentrale.exe --install  kopiert sich nach %LOCALAPPDATA%, startet mit Windows, öffnet die Einstellungen
  Wochenzentrale.exe --once     zeichnet einmal und beendet sich
  Wochenzentrale.exe --stop     beendet die laufende Instanz
  Wochenzentrale.exe --uninstall  Autostart entfernen
  Wochenzentrale.exe --preview DATEI.png [--size 2560x1440] [--ics DATEI.ics ...]  nur Bild erzeugen (Test)
"""
from __future__ import annotations

import ctypes
import datetime as dt
import json
import math
import os
import random
import re
import shutil
import sys
import time
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

try:
    from zoneinfo import ZoneInfo
    TZ = ZoneInfo("Europe/Vienna")
except Exception:  # pragma: no cover - ohne tzdata
    TZ = dt.timezone(dt.timedelta(hours=1))

APP = "Wochenzentrale"
IS_WIN = sys.platform == "win32"
HOME = Path(os.environ.get("LOCALAPPDATA") or Path.home() / ".local" / "share") / APP
CONFIG = HOME / "config.json"
CACHE = HOME / "cache"
STOPFILE = HOME / "stop"

DEFAULT_CONFIG = {
    "_hilfe": "Trage bei 'url' die privaten iCal-Adressen deiner Kalender ein (Google Kalender > Einstellungen > Kalender > "
              "'Privatadresse im iCal-Format'). kind: own, fh, work, family, private, moodle, holiday. "
              "theme: system, mondsilber, platin, obsidian, leere, grimoire, glut, frost, hain, klassisch. "
              "monitors: Layout je Monitor (Hauptmonitor zuerst): week, 3days, day.",
    "theme": "system",
    "glow": None,
    "caps": None,
    "fill": 0.22,
    "by_subject": True,
    "monitors": ["week", "3days"],
    "refresh_minutes": 10,
    "calendars": [
        {"name": "Mein Kalender", "kind": "own", "url": ""},
        {"name": "FH-Stundenplan", "kind": "fh", "url": ""},
        {"name": "Moodle", "kind": "moodle", "url": ""},
        {"name": "Dienstplan", "kind": "work", "url": ""},
        {"name": "Arbeit", "kind": "work", "url": ""},
        {"name": "Familie", "kind": "family", "url": ""},
        {"name": "Privat", "kind": "private", "url": ""},
    ],
}

# ------------------------------------------------------------------------------------------------ Themes


def C(v: int) -> tuple:
    return ((v >> 16) & 255, (v >> 8) & 255, v & 255)


CATS = ["sleep", "fh", "work", "gym", "study", "people", "proj", "misc"]


@dataclass
class Theme:
    id: str
    name: str
    bg_top: int
    bg_bottom: int
    panel: int
    line: int
    text: int
    mute: int
    accent: int
    now: int
    palette: list
    frame: str
    font: str
    fx: str
    glow: bool
    caps: bool


THEMES = {t.id: t for t in [
    Theme("system", "System-Fenster", 0x05070D, 0x0A1430, 0x0A1124, 0x16223F, 0xDDEBFF, 0x6E86AD, 0x6FD3FF, 0xFF4D6D,
          [0x7C8CFF, 0x4FC3FF, 0x5CFFB0, 0xFF5C7A, 0xFFD84D, 0xC77DFF, 0x39F0E0, 0x8FA3BF], "brackets", "mono", "stars", True, True),
    Theme("mondsilber", "Mondsilber", 0x0B0C10, 0x17191F, 0x14161B, 0x2A2D35, 0xF2F4F8, 0x9AA0AC, 0xE6E9F0, 0xFF4D5E,
          [0x8E9BFF, 0x5AB8FF, 0x4CE0A0, 0xFF6B6B, 0xFFD25A, 0xE58CFF, 0x3FE0E0, 0xC0C6D0], "brackets", "sans", "stars", True, True),
    Theme("platin", "Platin", 0x121316, 0x1E2026, 0x191B20, 0x30333B, 0xF7F8FA, 0xA3A8B3, 0xC9CED8, 0x5AC8FA,
          [0x9AA6FF, 0x6EC1FF, 0x5FE3A6, 0xFF7A7A, 0xF5CF5F, 0xE09AF5, 0x52D9D9, 0xB9BFC9], "engraved", "serif", "circle", False, False),
    Theme("obsidian", "Obsidian", 0x000000, 0x070707, 0x0E0E0E, 0x222222, 0xFFFFFF, 0x8C8C8C, 0xFFFFFF, 0xFF3B30,
          [0x7D8BFF, 0x3FA9FF, 0x30D98A, 0xFF5252, 0xFFCC33, 0xD97CFF, 0x2EE6E6, 0xBDBDBD], "round", "sans", "none", False, False),
    Theme("leere", "Leere", 0x07040C, 0x170A26, 0x120A1E, 0x2A1B3D, 0xEDE3FF, 0x8E7FAD, 0xB47CFF, 0xFF5FA2,
          [0x9A8CFF, 0x8F7BFF, 0x6FE3C1, 0xFF6FA3, 0xE8C46A, 0xD58CFF, 0x7FD6FF, 0x9C93B5], "brackets", "sans", "circle", True, False),
    Theme("grimoire", "Grimoire", 0x1C130B, 0x0B0704, 0x21160C, 0x3A2A18, 0xF3E6C8, 0xA89270, 0xE3B866, 0xE0604A,
          [0xB7A6E0, 0x7FA7D9, 0x9CCB86, 0xD9785B, 0xE8C064, 0xC995C9, 0x7CC2B4, 0xB8A88A], "engraved", "serif", "circle", False, False),
    Theme("glut", "Drachenglut", 0x160604, 0x240B05, 0x1C0B06, 0x3A1A10, 0xFFE9DC, 0xB98A76, 0xFF7A3D, 0xFFD24A,
          [0xA99BD6, 0x7FB0E8, 0x9BD67F, 0xFF6A3D, 0xFFC247, 0xF28CB8, 0x6FD0C8, 0xC7A79A], "round", "serif", "embers", True, False),
    Theme("frost", "Frostrunen", 0x061019, 0x0D2433, 0x0B1A26, 0x1C3446, 0xE8FBFF, 0x7FA3B5, 0xA8F0FF, 0xFF7A9C,
          [0xB5C3FF, 0x8FD6FF, 0x9FF0D0, 0xFF9FB5, 0xF5E6A0, 0xD9C2FF, 0xA8F0FF, 0xA9BCC9], "brackets", "sans", "snow", True, True),
    Theme("hain", "Elfenhain", 0x06120D, 0x0E2219, 0x0C1A13, 0x1E3A2B, 0xEAF7EC, 0x86A894, 0x7FE3A8, 0xF2C14E,
          [0xB9B0E8, 0x8CC8E8, 0x9FE39A, 0xF0A07A, 0xE8D77A, 0xE7A8D8, 0x7FE3C4, 0xA9B8A6], "engraved", "serif", "stars", False, False),
    Theme("klassisch", "Klassisch", 0x0F1117, 0x0F1117, 0x161922, 0x262A36, 0xE8EAF0, 0x8A91A5, 0xFF5D62, 0xFF5D62,
          [0xAAB3F0, 0x9CC3FF, 0x8FE0AE, 0xFFB39F, 0xFFD966, 0xE0B4F5, 0x7FE0E8, 0xB8BECE], "round", "sans", "none", False, False),
]}

SUBJECTS = [  # Name, Farbe, Muster
    ("Mathe", 0xFFB84D, r"MACS|Mathe"),
    ("Datenmanagement", 0x4DD2FF, r"DMNMT|Datenmanagement|SQL|Datenbank"),
    ("Infrastruktur", 0x7CFF8A, r"INFRA|Infrastruktur|Netzwerk"),
    ("C", 0xFF6B9E, r"PROZD|Prozedural|\bC\b"),
    ("Web", 0xB48CFF, r"WTBIF|Webtech|Web-|PHP|HTML"),
    ("KoKo", 0xFF8F5A, r"KOKO|Kompetenz"),
    ("Englisch", 0x5AE6C8, r"ENG1|English|Englisch"),
    ("Security+", 0xFF5A5A, r"Security\+|Sec\+|CompTIA"),
]
SILVER = 0xC9CED6


@dataclass
class Look:
    theme: Theme
    frame: str
    font: str
    fx: str
    glow: bool
    caps: bool
    fill: float
    by_subject: bool


def look_from(cfg: dict) -> Look:
    t = THEMES.get(cfg.get("theme", "system"), THEMES["system"])
    pick = lambda k, d: d if cfg.get(k) is None else cfg[k]
    return Look(t, pick("frame", t.frame), pick("font", t.font), pick("fx", t.fx), pick("glow", t.glow),
                pick("caps", t.caps), float(cfg.get("fill", 0.22)), bool(cfg.get("by_subject", True)))


# ------------------------------------------------------------------------------------------------ Logik (wie in der App)

SLEEP_RE = re.compile(r"🛏|😴|Bett|Schlaf|Nap|Wecker|Aufstehen", re.I)
GYM_RE = re.compile(r"🏋|🏃|Gym|Lauf|Run|Stretch|🧘", re.I)
STUDY_RE = re.compile(r"📚|🧠|Anki|Lern|Mathe|Security|Prüfung|Study", re.I)
PEOPLE_RE = re.compile(r"👨‍👩‍👧|Family|Familie|❤|💑|Freundin", re.I)
PROJ_RE = re.compile(r"💻|Claude|Projekt|Coding", re.I)
FH_RE = re.compile(r"MACS1|DMNMT|INFRA|PROZD|WTBIF|KOKO|ENG1|BIF|FH ")
WORK_RE = re.compile(r"Arbeit|Dienst|Schicht|T-Systems|Raben", re.I)
EXAM_RE = re.compile(r"prüfung|pruefung|klausur|\btest\b|abschlusstest|teilprüfung|zwischentest|minitest|abgabe|endet|deadline|peer review|🎯", re.I)
NOT_EXAM_RE = re.compile(r"^📚|probetest|mock|vorbereitung|nachbereitung", re.I)
IMPORTANT_RE = re.compile(r"make-up|mock|probetest|mündlich|muendlich", re.I)


def base_title(t: str) -> str:
    return re.sub(r"^[✓✗]\s*", "", t)


def status_of(t: str) -> str:
    return "done" if t.startswith("✓") else "missed" if t.startswith("✗") else "open"


def classify(title: str, kind: str) -> str:
    t = base_title(title)
    if SLEEP_RE.search(t):
        return "sleep"
    if GYM_RE.search(t):
        return "gym"
    if STUDY_RE.search(t) and not FH_RE.search(t) and kind != "fh":
        return "study"
    if PEOPLE_RE.search(t) or kind == "family":
        return "people"
    if PROJ_RE.search(t):
        return "proj"
    if FH_RE.search(t) or kind == "fh":
        return "fh"
    if WORK_RE.search(t) or kind == "work":
        return "work"
    return "misc"


def is_exam(title: str) -> bool:
    t = base_title(title)
    return bool(EXAM_RE.search(t)) and not NOT_EXAM_RE.search(t)


def is_important(title: str) -> bool:
    return is_exam(title) or bool(IMPORTANT_RE.search(title))


def subject_of(title: str):
    for name, col, pat in SUBJECTS:
        if re.search(pat, title, re.I):
            return name, col
    return None


def short_title(title: str, compact: bool) -> str:
    t = re.sub(r"^[^\w]+", "", base_title(title).strip())
    t = re.sub(r"\s*-\s*BIF-\w+", "", t)
    t = re.sub(r"^([A-Z0-9]{3,})-(ILV|LAB|VO|UE|SE)\b", r"\1 \2", t)
    if compact:
        for a, b in (("Mathematik für Computer Science", "Mathe"), ("Mathe für Informatik", "Mathe"), ("Thema ", "T"),
                     ("Prozedurale Sprachen", "C"), ("Datenmanagement", "DB"), ("Webtechnologien", "Web")):
            t = t.replace(a, b)
        t = re.sub(r"\s*·\s*", " ", t)
    return t or title


@dataclass
class Ev:
    title: str
    start: dt.datetime
    end: dt.datetime
    cat: str
    all_day: bool = False
    kind: str = "own"


def color_for(lk: Look, ev_title: str, cat: str) -> int:
    if cat == "sleep":
        return SILVER
    if lk.by_subject and cat in ("fh", "study"):
        sub = subject_of(ev_title)
        if sub:
            return mix(sub[1], lk.theme.text, 0.12)
    return lk.theme.palette[CATS.index(cat)]


def mix(a: int, b: int, t: float) -> int:
    ca, cb = C(a), C(b)
    r = [int(ca[i] * (1 - t) + cb[i] * t) for i in range(3)]
    return (r[0] << 16) | (r[1] << 8) | r[2]


def rgba(c: int, a: float = 1.0) -> tuple:
    return C(c) + (max(0, min(255, int(a * 255))),)


def place_day(evs: list[Ev], day: dt.date):
    """Termine eines Tages in Minuten, mit Spalten bei Überlappung."""
    a = dt.datetime.combine(day, dt.time(0), TZ)
    b = a + dt.timedelta(days=1)
    items = []
    for e in sorted((e for e in evs if not e.all_day and e.end > a and e.start < b and e.end > e.start),
                    key=lambda e: (e.start, -e.end.timestamp())):
        s = 0 if e.start <= a else e.start.hour * 60 + e.start.minute
        en = 1440 if e.end >= b else e.end.hour * 60 + e.end.minute
        items.append([e, s, en, 0, 1])
    out, cluster, cend = [], [], 0
    def flush():
        cols = []
        for it in cluster:
            c = next((i for i, x in enumerate(cols) if x <= it[1]), -1)
            if c < 0:
                c = len(cols); cols.append(it[2])
            else:
                cols[c] = it[2]
            it[3] = c
        for it in cluster:
            it[4] = len(cols); out.append(tuple(it))
    for it in items:
        if cluster and it[1] >= cend:
            flush(); cluster = []
        cluster.append(it)
        cend = it[2] if len(cluster) == 1 else max(cend, it[2])
    if cluster:
        flush()
    return out


def countdowns(evs: list[Ev], now: dt.datetime, n: int = 4):
    seen, out = set(), []
    for e in sorted(evs, key=lambda e: e.start):
        if not is_exam(e.title) or e.end < now:
            continue
        name = short_title(e.title.replace("🎯", "").strip(), False)
        if name.lower() in seen:
            continue
        seen.add(name.lower())
        out.append((name, (e.start.astimezone(TZ).date() - now.date()).days))
        if len(out) >= n:
            break
    return out


def days_label(d: int) -> str:
    return "heute" if d <= 0 else "morgen" if d == 1 else f"in {d} Tagen"


# ------------------------------------------------------------------------------------------------ iCal lesen

def _unfold(text: str) -> list[str]:
    out = []
    for line in text.replace("\r\n", "\n").replace("\r", "\n").split("\n"):
        if line[:1] in (" ", "\t") and out:
            out[-1] += line[1:]
        else:
            out.append(line)
    return out


def _prop(line: str):
    m = re.match(r"([A-Za-z0-9-]+)((?:;[^:]*)?):(.*)", line)
    if not m:
        return None
    params = {}
    for p in m.group(2).split(";")[1:]:
        if "=" in p:
            k, v = p.split("=", 1)
            params[k.upper()] = v.strip('"')
    return m.group(1).upper(), params, m.group(3)


WIN_TZ = {"W. Europe Standard Time": "Europe/Vienna", "Central Europe Standard Time": "Europe/Vienna",
          "Romance Standard Time": "Europe/Paris", "GMT Standard Time": "Europe/London", "UTC": "UTC"}


def _dt(value: str, params: dict):
    value = value.strip()
    if params.get("VALUE") == "DATE" or re.fullmatch(r"\d{8}", value):
        return dt.datetime.strptime(value[:8], "%Y%m%d").replace(tzinfo=TZ), True
    base = dt.datetime.strptime(value[:15], "%Y%m%dT%H%M%S")
    if value.endswith("Z"):
        return base.replace(tzinfo=dt.timezone.utc).astimezone(TZ), False
    tzid = params.get("TZID")
    if tzid:
        try:
            return base.replace(tzinfo=ZoneInfo(WIN_TZ.get(tzid, tzid))).astimezone(TZ), False
        except Exception:
            pass
    return base.replace(tzinfo=TZ), False


def _duration(v: str) -> dt.timedelta:
    m = re.fullmatch(r"([+-])?P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?", v.strip())
    if not m:
        return dt.timedelta(0)
    sign = -1 if m.group(1) == "-" else 1
    w, d, h, mi, s = (int(x or 0) for x in m.groups()[1:])
    return sign * dt.timedelta(weeks=w, days=d, hours=h, minutes=mi, seconds=s)


WD = {"MO": 0, "TU": 1, "WE": 2, "TH": 3, "FR": 4, "SA": 5, "SU": 6}


def _occurrences(start: dt.datetime, rule: str, lo: dt.datetime, hi: dt.datetime, limit: int = 2000):
    r = dict(p.split("=", 1) for p in rule.split(";") if "=" in p)
    freq = r.get("FREQ", "WEEKLY")
    interval = int(r.get("INTERVAL", 1))
    count = int(r["COUNT"]) if "COUNT" in r else None
    until = _dt(r["UNTIL"], {})[0] if "UNTIL" in r else None
    byday = [WD[d[-2:]] for d in r.get("BYDAY", "").split(",") if d[-2:] in WD]
    n = 0
    if freq == "WEEKLY":
        days = sorted(byday) if byday else [start.weekday()]
        week0 = start - dt.timedelta(days=start.weekday())
        k = 0
        while k < limit:
            for wd in days:
                occ = week0 + dt.timedelta(weeks=k * interval, days=wd)
                if occ < start:
                    continue
                if (until and occ > until) or (count is not None and n >= count) or occ > hi:
                    return
                n += 1
                yield occ
            k += 1
        return
    step = {"DAILY": dt.timedelta(days=interval)}.get(freq)
    occ = start
    for _ in range(limit):
        if (until and occ > until) or (count is not None and n >= count) or occ > hi:
            return
        n += 1
        yield occ
        if step:
            occ = occ + step
        elif freq == "MONTHLY":
            y, mth = divmod(occ.month - 1 + interval, 12)
            try:
                occ = occ.replace(year=occ.year + y, month=mth + 1)
            except ValueError:
                return
        elif freq == "YEARLY":
            try:
                occ = occ.replace(year=occ.year + interval)
            except ValueError:
                return
        else:
            return


def parse_ics(text: str, kind: str, lo: dt.datetime, hi: dt.datetime) -> list[Ev]:
    out, events, cur = [], [], None
    for line in _unfold(text):
        if line == "BEGIN:VEVENT":
            cur = {"EXDATE": []}
        elif line == "END:VEVENT" and cur is not None:
            events.append(cur); cur = None
        elif cur is not None:
            p = _prop(line)
            if not p:
                continue
            name, params, value = p
            if name == "EXDATE":
                for v in value.split(","):
                    cur["EXDATE"].append(_dt(v, params)[0])
            else:
                cur[name] = (params, value)
    overridden = {}
    for e in events:
        if "RECURRENCE-ID" in e:
            overridden.setdefault(e.get("UID", ({}, ""))[1], set()).add(_dt(e["RECURRENCE-ID"][1], e["RECURRENCE-ID"][0])[0])
    for e in events:
        if "DTSTART" not in e or e.get("STATUS", ({}, ""))[1].upper() == "CANCELLED":
            continue
        start, all_day = _dt(e["DTSTART"][1], e["DTSTART"][0])
        if "DTEND" in e:
            end = _dt(e["DTEND"][1], e["DTEND"][0])[0]
        elif "DURATION" in e:
            end = start + _duration(e["DURATION"][1])
        else:
            end = start + (dt.timedelta(days=1) if all_day else dt.timedelta(0))
        title = e.get("SUMMARY", ({}, "(ohne Titel)"))[1].replace("\\,", ",").replace("\\;", ";").replace("\\n", " ").replace("\\\\", "\\")
        dur = end - start
        if not all_day and dur >= dt.timedelta(days=1) and start.hour == 0 and start.minute == 0 and end.hour == 0 and end.minute == 0:
            all_day = True
        starts = [start]
        if "RRULE" in e and "RECURRENCE-ID" not in e:
            skip = set(e["EXDATE"]) | overridden.get(e.get("UID", ({}, ""))[1], set())
            starts = [o for o in _occurrences(start, e["RRULE"][1], lo - dur, hi) if o not in skip]
        for s in starts:
            if s + dur >= lo and s <= hi:
                out.append(Ev(title, s, s + dur, classify(title, kind), all_day, kind))
    return out


def fetch_all(cfg: dict, lo: dt.datetime, hi: dt.datetime, force: bool = False) -> list[Ev]:
    CACHE.mkdir(parents=True, exist_ok=True)
    evs = []
    for i, cal in enumerate(cfg.get("calendars", [])):
        url = (cal.get("url") or "").strip()
        if not url:
            continue
        f = CACHE / f"cal{i}.ics"
        stale = not f.exists() or time.time() - f.stat().st_mtime > cfg.get("refresh_minutes", 10) * 60
        if force or stale:
            try:
                req = urllib.request.Request(url.replace("webcal://", "https://"), headers={"User-Agent": APP})
                with urllib.request.urlopen(req, timeout=30) as r:
                    f.write_bytes(r.read())
            except Exception as ex:  # alte Kopie weiterverwenden
                log(f"Kalender '{cal.get('name')}' nicht erreichbar: {ex}")
        if f.exists():
            try:
                evs += parse_ics(f.read_text(encoding="utf-8", errors="replace"), cal.get("kind", "own"), lo, hi)
            except Exception as ex:
                log(f"Kalender '{cal.get('name')}' nicht lesbar: {ex}")
    return dedupe(evs)


def dedupe(evs: list[Ev]) -> list[Ev]:
    """Gleiche Termine aus mehreren Kalendern (z. B. Dienstplan und Arbeit) nur einmal."""
    seen, uniq = set(), []
    for e in evs:
        k = (base_title(e.title).strip().lower(), e.start, e.end)
        if k not in seen:
            seen.add(k); uniq.append(e)
    return uniq


# ------------------------------------------------------------------------------------------------ Zeichnen

FONT_FILES = {
    ("sans", False): ["segoeui.ttf", "DejaVuSans.ttf"], ("sans", True): ["segoeuib.ttf", "DejaVuSans-Bold.ttf"],
    ("serif", False): ["georgia.ttf", "DejaVuSerif.ttf"], ("serif", True): ["georgiab.ttf", "DejaVuSerif-Bold.ttf"],
    ("mono", False): ["consola.ttf", "DejaVuSansMono.ttf"], ("mono", True): ["consolab.ttf", "DejaVuSansMono-Bold.ttf"],
}
_font_cache: dict = {}


def font(style: str, bold: bool, size: float):
    key = (style, bold, int(size))
    if key in _font_cache:
        return _font_cache[key]
    dirs = [Path(os.environ.get("WINDIR", "C:/Windows")) / "Fonts", Path("/usr/share/fonts/truetype/dejavu")]
    f = None
    for name in FONT_FILES[(style, bold)]:
        for d in dirs:
            if (d / name).exists():
                f = ImageFont.truetype(str(d / name), max(8, int(size)))
                break
        if f:
            break
    _font_cache[key] = f or ImageFont.load_default()
    return _font_cache[key]


def fit_text(d: ImageDraw.ImageDraw, text: str, f, width: float) -> str:
    if d.textlength(text, font=f) <= width:
        return text
    while text and d.textlength(text + "…", font=f) > width:
        text = text[:-1]
    return text + "…" if text else ""


def wrap(d: ImageDraw.ImageDraw, text: str, f, width: float, max_lines: int) -> list[str]:
    words, lines, cur = text.split(), [], ""
    for w in words:
        cand = (cur + " " + w).strip()
        if d.textlength(cand, font=f) <= width:
            cur = cand
        else:
            if cur:
                lines.append(cur)
            cur = w
        if len(lines) >= max_lines:
            break
    if cur and len(lines) < max_lines:
        lines.append(cur)
    used = " ".join(lines).split()
    if len(used) < len(words) and lines:
        lines[-1] = fit_text(d, lines[-1] + " " + " ".join(words[len(used):]), f, width)
    return [fit_text(d, ln, f, width) for ln in lines[:max_lines]]


def background(im: Image.Image, lk: Look, s: float):
    w, h = im.size
    t = lk.theme
    top, bot = C(t.bg_top), C(t.bg_bottom)
    grad = Image.new("RGB", (1, 256))
    for y in range(256):
        k = y / 255
        grad.putpixel((0, y), tuple(int(top[i] * (1 - k) + bot[i] * k) for i in range(3)))
    im.paste(grad.resize((w, h)), (0, 0))
    d = ImageDraw.Draw(im, "RGBA")
    rnd = random.Random(7)
    minute = int(time.time() // 60)
    if lk.fx == "stars":
        for i in range(int(160 * w * h / (1920 * 1080))):
            x, y = rnd.random() * w, rnd.random() * h
            a = (0.15 + 0.45 * rnd.random()) * (0.6 + 0.4 * (((minute + i) % 5) / 4))
            r = (0.8 + rnd.random() * 2.2) * s
            d.ellipse((x - r, y - r, x + r, y + r), fill=rgba(t.accent if i % 4 == 0 else 0xFFFFFF, a))
    elif lk.fx in ("embers", "snow"):
        for i in range(int(110 * w * h / (1920 * 1080))):
            x, sp = rnd.random() * w, 0.3 + rnd.random()
            y = (rnd.random() * h + (-1 if lk.fx == "embers" else 1) * minute * sp * 6 * s) % h
            col = (0xFFD27A if i % 3 == 0 else t.accent) if lk.fx == "embers" else 0xFFFFFF
            r = (1 + rnd.random() * 2.5) * s
            d.ellipse((x - r, y - r, x + r, y + r), fill=rgba(col, 0.2 + 0.5 * rnd.random()))
    elif lk.fx == "circle":
        cx, cy, r = w * 0.5, h * 0.55, min(w, h) * 0.46
        col = rgba(t.accent, 0.11)
        lw = max(1, int(2 * s))
        for k in (1, 0.92, 0.55):
            d.ellipse((cx - r * k, cy - r * k, cx + r * k, cy + r * k), outline=col, width=lw)
        rot = (minute % 360) * 0.5
        for i in range(36):
            a = math.radians(i * 10 + rot)
            r1 = r * (0.86 if i % 3 == 0 else 0.92)
            d.line((cx + math.cos(a) * r1, cy + math.sin(a) * r1, cx + math.cos(a) * r, cy + math.sin(a) * r), fill=col, width=lw)
        for k in (0, 1):
            pts = [(cx + math.cos(math.radians(-90 + k * 60 + i * 120 - rot)) * r * 0.86,
                    cy + math.sin(math.radians(-90 + k * 60 + i * 120 - rot)) * r * 0.86) for i in range(4)]
            d.line(pts, fill=col, width=lw)
    # Vignette
    vg = Image.radial_gradient("L").resize((w, h))
    vg = vg.point(lambda v: 0 if v < 150 else int((v - 150) / 105 * 150))
    im.paste((0, 0, 0), (0, 0, w, h), vg)


def draw_block(d: ImageDraw.ImageDraw, r: tuple, ev: Ev, lk: Look, s: float, compact: bool, now: dt.datetime):
    x0, y0, x1, y1 = r
    t = lk.theme
    sleep = ev.cat == "sleep"
    important = not sleep and is_important(ev.title)
    is_now = ev.start <= now < ev.end and not sleep
    past = ev.end < now
    fg = color_for(lk, ev.title, ev.cat)
    pad_x, pad_y = 8 * s, 5 * s
    tw = x1 - x0 - pad_x - 6 * s
    if tw < 12:
        return
    title = short_title(ev.title, compact)
    if lk.caps:
        title = title.upper()
    size = (15 if compact else 19) * s
    f = font(lk.font, True, size)
    longest = max(title.split(), key=len) if title.split() else title
    while size > (11 if compact else 13) * s and d.textlength(longest, font=f) > tw:
        size -= 1 * s
        f = font(lk.font, True, size)
    line_h = size * 1.2
    y1 = max(y1, y0 + line_h + 2 * pad_y)
    rad = {"round": 7, "brackets": 2, "engraved": 4}[lk.frame] * s
    box = (x0, y0, x1, y1)
    if sleep:
        d.rounded_rectangle(box, rad, fill=(0, 0, 0, 255), outline=rgba(SILVER, 0.55), width=max(1, int(1.2 * s)))
    else:
        if lk.glow or important or is_now:
            g = t.text if is_now else t.now if important else fg
            k = 1.8 if (is_now or important) else 1
            for wpx, a in ((7, 0.10), (4, 0.20)):
                e = wpx * s / 2
                d.rounded_rectangle((x0 - e, y0 - e, x1 + e, y1 + e), rad + e, outline=rgba(g, a * k), width=max(1, int(wpx * s / 2)))
        d.rounded_rectangle(box, rad, fill=rgba(mix(t.panel, fg, min(lk.fill + (0.15 if is_now else 0), 0.6))))
        lw = max(1, int(1 * s))
        if lk.frame == "round":
            d.rectangle((x0, y0 + 3 * s, x0 + 3 * s, y1 - 3 * s), fill=rgba(fg))
        elif lk.frame == "brackets":
            d.rectangle(box, outline=rgba(fg, 0.35), width=lw)
            k = min(10 * s, (x1 - x0) / 3, (y1 - y0) / 3)
            bw = max(1, int(2 * s))
            for (ax, ay, bx, by) in ((x0, y0, x0 + k, y0), (x0, y0, x0, y0 + k), (x1, y0, x1 - k, y0), (x1, y0, x1, y0 + k),
                                     (x0, y1, x0 + k, y1), (x0, y1, x0, y1 - k), (x1, y1, x1 - k, y1), (x1, y1, x1, y1 - k)):
                d.line((ax, ay, bx, by), fill=rgba(fg), width=bw)
        else:
            d.rounded_rectangle(box, rad, outline=rgba(t.accent, 0.85), width=max(1, int(1.5 * s)))
            i = 3 * s
            if y1 - y0 > 4 * i and x1 - x0 > 4 * i:
                d.rounded_rectangle((x0 + i, y0 + i, x1 - i, y1 - i), rad, outline=rgba(fg, 0.5), width=lw)
            cx, dd = (x0 + x1) / 2, 3 * s
            d.polygon([(cx, y0 - dd), (cx + dd, y0), (cx, y0 + dd), (cx - dd, y0)], fill=rgba(t.accent))
        if important:
            d.rounded_rectangle(box, rad, outline=rgba(t.now), width=max(1, int(2 * s)))
        if is_now:
            d.rounded_rectangle(box, rad, outline=rgba(t.text), width=max(2, int(2.5 * s)))
    max_lines = max(1, int((y1 - y0 - pad_y) / line_h))
    lines = wrap(d, title, f, tw, max_lines)
    if (y1 - y0) > line_h * 4 and len(lines) < max_lines:
        lines.append(f"{ev.start:%H:%M}–{ev.end:%H:%M}")
    for i, ln in enumerate(lines):
        is_time = i == len(lines) - 1 and re.fullmatch(r"\d\d:\d\d–\d\d:\d\d", ln)
        d.text((x0 + pad_x, y0 + pad_y + i * line_h), ln, font=font(lk.font, not is_time, size * (0.85 if is_time else 1)),
               fill=rgba(fg, 0.8 if is_time else 1))
    st = status_of(ev.title)
    if past and not sleep:
        d.rounded_rectangle(box, rad, fill=(0, 0, 0, 95))
    if st != "open":
        bs = min(18 * s, y1 - y0 - 4)
        bx, by = x1 - bs - 4 * s, y0 + 4 * s
        d.ellipse((bx, by, bx + bs, by + bs), fill=rgba(0x3DDC84 if st == "done" else t.now))
        d.text((bx + bs / 2, by + bs / 2), "✓" if st == "done" else "✕", font=font("sans", True, bs * 0.8), fill=(0, 0, 0, 255), anchor="mm")
    elif is_now and x1 - x0 > 50 * s:
        tag = "JETZT" if lk.caps else "Jetzt"
        tf = font(lk.font, True, 12 * s)
        tw2 = d.textlength(tag, font=tf) + 12 * s
        th = 18 * s
        tr = (x1 - tw2 - 5 * s, y0 - th / 2, x1 - 5 * s, y0 + th / 2)
        d.rounded_rectangle(tr, th / 2, fill=rgba(t.text))
        d.text(((tr[0] + tr[2]) / 2, (tr[1] + tr[3]) / 2), tag, font=tf, fill=(0, 0, 0, 255), anchor="mm")
    elif important and y1 - y0 > 22 * s:
        bs = min(16 * s, y1 - y0 - 4)
        cx, cy = x1 - bs / 2 - 4 * s, y0 + bs / 2 + 4 * s
        d.ellipse((cx - bs / 2, cy - bs / 2, cx + bs / 2, cy + bs / 2), fill=rgba(t.now))
        d.text((cx, cy), "!", font=font("sans", True, bs * 0.8), fill=(0, 0, 0, 255), anchor="mm")


def draw_schedule(im: Image.Image, area: tuple, days: list[dt.date], evs: list[Ev], lk: Look, s: float, now: dt.datetime,
                  title: str):
    x, y, w, h = area
    d = ImageDraw.Draw(im, "RGBA")
    t = lk.theme
    compact = len(days) > 3
    # Kopf: Titel und Countdown
    ft = font(lk.font, True, 30 * s)
    head = title.upper() if lk.caps else title
    d.text((x + 6 * s, y), head, font=ft, fill=rgba(t.text))
    cx = x + d.textlength(head, font=ft) + 28 * s
    cfont = font(lk.font, True, 15 * s)
    for name, days_left in countdowns(evs, now):
        sub = subject_of(name)
        col = mix(sub[1], t.text, 0.12) if sub else t.accent
        tail = f" · {days_label(days_left)}"
        name_fit = fit_text(d, name.upper() if lk.caps else name, cfont, 330 * s)
        label = name_fit + (tail.upper() if lk.caps else tail)
        lw = d.textlength(label, font=cfont) + 24 * s
        if cx + lw > x + w:
            break
        urgent = days_left <= 3
        r = (cx, y + 4 * s, cx + lw, y + 34 * s)
        rad = 8 * s if lk.frame == "round" else 3 * s
        d.rounded_rectangle(r, rad, fill=rgba(col, 0.32 if urgent else 0.16), outline=rgba(col, 0.9), width=max(1, int((2 if urgent else 1) * s)))
        d.text((r[0] + 12 * s, (r[1] + r[3]) / 2), label, font=cfont, fill=rgba(t.text if urgent else col), anchor="lm")
        cx = r[2] + 8 * s
    # Raster
    head_h = 50 * s + (44 * s if len(days) > 1 else 0)
    label_w = 44 * s
    left, right = x + label_w, x + w
    top, bottom = y + head_h, y + h
    timed = [e for e in evs if not e.all_day]
    rel = [e for e in timed if e.cat != "sleep" and e.start.date() in days]
    t0 = max(5, min(8, min((e.start.hour for e in rel), default=8)))
    t1 = min(24, max(21, max(((e.end.hour + (1 if e.end.minute else 0)) if e.end.date() == e.start.date() else 24 for e in rel), default=21)))
    ph = (bottom - top) / (t1 - t0)
    hf = font(lk.font, False, 13 * s)
    for hr in range(t0, t1):
        yy = top + (hr - t0) * ph
        d.line((left, yy, right, yy), fill=rgba(t.line), width=1)
        d.text((x + 6 * s, yy), f"{hr:02d}", font=hf, fill=rgba(t.mute), anchor="lm")
    col_w = (right - left) / len(days)
    dn = ["Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"]
    df = font(lk.font, True, (16 if compact else 19) * s)
    for di, day in enumerate(days):
        cx = left + di * col_w
        if len(days) > 1:
            lab = f"{dn[day.weekday()]} {day.day}.{day.month}."
            lab = lab.upper() if lk.caps else lab
            today = day == now.date()
            d.text((cx + 6 * s, top - 12 * s), lab, font=df, fill=rgba(t.now if today else t.text), anchor="lb")
            if today:
                d.line((cx + 6 * s, top - 6 * s, cx + col_w - 8 * s, top - 6 * s), fill=rgba(t.now), width=max(2, int(2.5 * s)))
            hol = next((e for e in evs if e.all_day and e.kind == "holiday" and e.start.date() <= day < e.end.date()), None)
            if hol:
                hf2 = font(lk.font, False, 11 * s)
                d.text((cx + 6 * s, top - 30 * s), fit_text(d, short_title(hol.title, True), hf2, col_w - 12 * s), font=hf2,
                       fill=rgba(t.accent, 0.9), anchor="lb")
        for ev, fm, tm, col, cols in place_day(timed, day):
            a2, b2 = max(fm / 60, t0), min(tm / 60, t1)
            if b2 <= a2:
                continue
            cw = col_w / cols
            r = (cx + col * cw + 2, top + (a2 - t0) * ph + 1.5, cx + (col + 1) * cw - 3, top + (b2 - t0) * ph - 1.5)
            draw_block(d, r, ev, lk, s, compact, now)
        # Fristen ohne Dauer: Markierungslinie
        for ev in timed:
            if ev.end - ev.start <= dt.timedelta(minutes=1) and is_exam(ev.title) and ev.start.date() == day:
                hh = ev.start.hour + ev.start.minute / 60 or 24
                if t0 <= hh <= t1:
                    yy = top + (hh - t0) * ph
                    xx = cx
                    while xx < cx + col_w:
                        d.line((xx, yy, min(xx + 7 * s, cx + col_w), yy), fill=rgba(t.now), width=max(2, int(2 * s)))
                        xx += 12 * s
                    tf = font(lk.font, True, 11 * s)
                    txt = fit_text(d, ("FRIST: " if lk.caps else "Frist: ") + short_title(ev.title, True), tf, col_w - 14 * s)
                    tr = (cx + 2 * s, yy - 9 * s, cx + 2 * s + d.textlength(txt, font=tf) + 10 * s, yy + 9 * s)
                    d.rounded_rectangle(tr, 4 * s, fill=rgba(t.now))
                    d.text((tr[0] + 5 * s, yy), txt, font=tf, fill=(0, 0, 0, 255), anchor="lm")
    # Jetzt-Linie
    if now.date() in days:
        ti = days.index(now.date())
        m = now.hour + now.minute / 60
        if t0 <= m <= t1:
            yy = top + (m - t0) * ph
            if lk.glow:
                d.line((left + ti * col_w, yy, left + (ti + 1) * col_w, yy), fill=rgba(t.now, 0.3), width=max(3, int(7 * s)))
            d.line((left + ti * col_w, yy, left + (ti + 1) * col_w, yy), fill=rgba(t.now), width=max(2, int(2.5 * s)))
            r = 5 * s
            d.ellipse((left + ti * col_w - r, yy - r, left + ti * col_w + r, yy + r), fill=rgba(t.now))


def render_monitor(im: Image.Image, rect: tuple, layout: str, evs: list[Ev], lk: Look, now: dt.datetime):
    x, y, w, h = rect
    s = min(w / 1920, h / 1080) * 1.0
    today = now.date()
    if layout == "week":
        mon = today - dt.timedelta(days=today.weekday())
        days = [mon + dt.timedelta(days=i) for i in range(7)]
        title = f"KW {today.isocalendar()[1]} · {days[0]:%d.%m.}–{days[-1]:%d.%m.}"
    elif layout == "day":
        days = [today]
        title = ["Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag"][today.weekday()] + f", {today:%d.%m.}"
    else:
        days = [today + dt.timedelta(days=i) for i in range(3)]
        title = f"Heute und die nächsten 2 Tage"
    margin_x, margin_top, margin_bottom = 40 * s, 36 * s, 70 * s  # unten Platz für die Taskleiste
    draw_schedule(im, (x + margin_x, y + margin_top, w - 2 * margin_x, h - margin_top - margin_bottom), days, evs, lk, s, now, title)


# ------------------------------------------------------------------------------------------------ Windows

def monitors() -> list[tuple]:
    """Monitore als (x, y, w, h) in Pixeln, Hauptmonitor zuerst."""
    if not IS_WIN:
        return [(0, 0, 2560, 1440)]
    try:
        ctypes.windll.shcore.SetProcessDpiAwareness(2)
    except Exception:
        try:
            ctypes.windll.user32.SetProcessDPIAware()
        except Exception:
            pass
    from ctypes import wintypes
    res = []

    class MONITORINFO(ctypes.Structure):
        _fields_ = [("cbSize", wintypes.DWORD), ("rcMonitor", wintypes.RECT), ("rcWork", wintypes.RECT), ("dwFlags", wintypes.DWORD)]

    def cb(hmon, hdc, lprect, lparam):
        mi = MONITORINFO(); mi.cbSize = ctypes.sizeof(MONITORINFO)
        ctypes.windll.user32.GetMonitorInfoW(hmon, ctypes.byref(mi))
        r = mi.rcMonitor
        res.append(((r.left, r.top, r.right - r.left, r.bottom - r.top), bool(mi.dwFlags & 1)))
        return 1

    proto = ctypes.WINFUNCTYPE(ctypes.c_int, ctypes.c_void_p, ctypes.c_void_p, ctypes.POINTER(wintypes.RECT), ctypes.c_double)
    ctypes.windll.user32.EnumDisplayMonitors(0, 0, proto(cb), 0)
    res.sort(key=lambda m: (not m[1], m[0][0]))
    return [m[0] for m in res] or [(0, 0, 1920, 1080)]


def set_wallpaper(path: Path):
    if not IS_WIN:
        return
    import winreg
    with winreg.OpenKey(winreg.HKEY_CURRENT_USER, r"Control Panel\Desktop", 0, winreg.KEY_SET_VALUE) as k:
        winreg.SetValueEx(k, "WallpaperStyle", 0, winreg.REG_SZ, "22")  # über alle Monitore spannen
        winreg.SetValueEx(k, "TileWallpaper", 0, winreg.REG_SZ, "0")
    ctypes.windll.user32.SystemParametersInfoW(20, 0, str(path), 3)


def render_all(cfg: dict, evs: list[Ev], now: dt.datetime, mons: list[tuple]) -> Image.Image:
    lk = look_from(cfg)
    vx = min(m[0] for m in mons); vy = min(m[1] for m in mons)
    vw = max(m[0] + m[2] for m in mons) - vx; vh = max(m[1] + m[3] for m in mons) - vy
    im = Image.new("RGB", (vw, vh), C(lk.theme.bg_bottom))
    layouts = cfg.get("monitors") or ["week"]
    for i, (mx, my, mw, mh) in enumerate(mons):
        part = Image.new("RGB", (mw, mh))
        s = min(mw / 1920, mh / 1080)
        background(part, lk, s)
        render_monitor(part, (0, 0, mw, mh), layouts[i] if i < len(layouts) else layouts[-1], evs, lk, now)
        im.paste(part, (mx - vx, my - vy))
    return im


def log(msg: str):
    try:
        HOME.mkdir(parents=True, exist_ok=True)
        with open(HOME / "log.txt", "a", encoding="utf-8") as f:
            f.write(f"{dt.datetime.now():%Y-%m-%d %H:%M:%S} {msg}\n")
    except Exception:
        pass


def load_config() -> dict:
    if not CONFIG.exists():
        HOME.mkdir(parents=True, exist_ok=True)
        CONFIG.write_text(json.dumps(DEFAULT_CONFIG, ensure_ascii=False, indent=2), encoding="utf-8")
    return json.loads(CONFIG.read_text(encoding="utf-8"))


def tick(cfg: dict, n: int, force: bool = False):
    now = dt.datetime.now(TZ)
    lo = dt.datetime.combine(now.date() - dt.timedelta(days=now.weekday() + 1), dt.time(0), TZ)
    hi = lo + dt.timedelta(days=100)
    evs = fetch_all(cfg, lo, hi, force)
    im = render_all(cfg, evs, now, monitors())
    out = HOME / f"wallpaper_{n % 2}.png"  # abwechselnd, damit Windows nicht das alte Bild zwischenspeichert
    im.save(out)
    set_wallpaper(out)


def install():
    HOME.mkdir(parents=True, exist_ok=True)
    exe = Path(sys.executable if getattr(sys, "frozen", False) else sys.argv[0]).resolve()
    target = HOME / exe.name
    if exe != target:
        shutil.copy2(exe, target)
    load_config()
    if IS_WIN:
        import winreg
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, r"Software\Microsoft\Windows\CurrentVersion\Run", 0, winreg.KEY_SET_VALUE) as k:
            cmd = f'"{target}"' if getattr(sys, "frozen", False) else f'"{sys.executable}" "{target}"'
            winreg.SetValueEx(k, APP, 0, winreg.REG_SZ, cmd)
        os.startfile(str(CONFIG))  # Einstellungen zum Ausfüllen öffnen
        os.startfile(str(target)) if getattr(sys, "frozen", False) else None
    print(f"Installiert nach {target}. Einstellungen: {CONFIG}")


def uninstall():
    if IS_WIN:
        import winreg
        try:
            with winreg.OpenKey(winreg.HKEY_CURRENT_USER, r"Software\Microsoft\Windows\CurrentVersion\Run", 0, winreg.KEY_SET_VALUE) as k:
                winreg.DeleteValue(k, APP)
        except OSError:
            pass
    STOPFILE.write_text("stop")
    print("Autostart entfernt und Programm beendet. Den Ordner kannst du löschen:", HOME)


def main():
    args = sys.argv[1:]
    if "--install" in args:
        return install()
    if "--uninstall" in args:
        return uninstall()
    if "--stop" in args:
        HOME.mkdir(parents=True, exist_ok=True)
        STOPFILE.write_text("stop")
        return
    if "--preview" in args:
        out = Path(args[args.index("--preview") + 1])
        size = args[args.index("--size") + 1] if "--size" in args else "2560x1440"
        w, h = (int(v) for v in size.split("x"))
        cfg = dict(DEFAULT_CONFIG)
        if "--theme" in args:
            cfg["theme"] = args[args.index("--theme") + 1]
        if "--layout" in args:
            cfg["monitors"] = [args[args.index("--layout") + 1]]
        now = dt.datetime.fromisoformat(args[args.index("--now") + 1]).replace(tzinfo=TZ) if "--now" in args else dt.datetime.now(TZ)
        lo, hi = now - dt.timedelta(days=10), now + dt.timedelta(days=100)
        evs = []
        for i, a in enumerate(args):
            if a == "--ics":
                kind, path = args[i + 1].split("=", 1) if "=" in args[i + 1] else ("own", args[i + 1])
                evs += parse_ics(Path(path).read_text(encoding="utf-8"), kind, lo, hi)
        render_all(cfg, dedupe(evs), now, [(0, 0, w, h)]).save(out)
        print("gespeichert:", out)
        return
    cfg = load_config()
    if "--once" in args:
        return tick(cfg, 0, force=True)
    # Dauerbetrieb, nur eine Instanz
    if IS_WIN:
        ctypes.windll.kernel32.CreateMutexW(None, False, "Global\\Wochenzentrale")
        if ctypes.windll.kernel32.GetLastError() == 183:
            return
    if STOPFILE.exists():
        STOPFILE.unlink()
    n = 0
    while not STOPFILE.exists():
        try:
            if CONFIG.stat().st_mtime > time.time() - 65:
                cfg = load_config()  # Einstellungen geändert: neu laden
            tick(cfg, n, force=(n == 0))
        except Exception as ex:
            log(f"Fehler: {ex!r}")
        n += 1
        time.sleep(60 - time.time() % 60 + 1)  # kurz nach jeder vollen Minute
    STOPFILE.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
