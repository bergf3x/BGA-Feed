/*
 * BGL Wetter & News
 * Copyright (c) 2026 bergf3x. Alle Rechte vorbehalten.
 * Nutzung, Vervielfaeltigung und Weitergabe nur mit ausdruecklicher Erlaubnis des Urhebers.
 */
package de.bergf3x.bgawidget;

/** Kurze Versionshistorie fuer das Info-Menue. Neueste Version zuerst. Praefix: + neu, - entfernt, ~ geaendert/behoben. */
final class Changelog {
    private Changelog() { }

    static final String[][] LOG = {
            {"1.04",
                    "+ Zweiter Ersatz-Feed über jsDelivr, falls GitHub ausfällt"},
            {"1.03",
                    "+ Copyright-Hinweis in „Über die App“"},
            {"1.02",
                    "+ Über die App: Quellen, Open-Meteo und DWD als Links",
                    "~ Über die App: „Schließen“ bleibt immer sichtbar"},
            {"1.01",
                    "~ Widget: eigene Kopfzeile mit Aktualisieren und Einstellungen",
                    "~ Widget: Quelle kurz („Anzeiger“), Lupe entfernt, kein doppelter Mond",
                    "+ Widget: Hintergrund bis 0 % (ganz durchsichtig, ohne Rand)",
                    "+ Wetter-Widget: Webcam-Knopf",
                    "~ Reiter-Auswahl: einheitliche Kacheln, Vibration beim Wählen",
                    "~ Auswahl aus dem Widget öffnet nicht mehr die App",
                    "- App: Deckkraft-Regler entfernt (nur im Widget nötig)",
                    "~ Über die App: kürzeres Protokoll"},
            {"1.00",
                    "+ Direktabruf vom Handy, GitHub als Ersatz",
                    "+ 9 Quellen: Anzeiger, BGLand24, chiemgau24, Tagblatt, Merkur, ORF, BR24, Landratsamt, Nationalpark",
                    "+ Reiter: Alle · Zeitungen · BGL · Deutschland & Welt · Gemerkt",
                    "+ Wetterkarte schrumpft beim Scrollen, Ziehen zum Aktualisieren",
                    "+ „Neu seit letztem Besuch“, Hinweis ohne Verbindung",
                    "+ Einstellungen neu geordnet, iPhone-Schalter, Vibration",
                    "+ Widgets: Nachrichten, Wetter oder beides"},
    };
}
