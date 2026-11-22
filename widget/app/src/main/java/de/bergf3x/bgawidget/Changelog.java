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
            {"1.09",
                    "+ Vorschaubilder in der Nachrichtenliste (App und Widget) und im Vorschau-Fenster; Schalter Aus / Nur WLAN / Immer unter Darstellung",
                    "+ Quellen-Wächter: unter Info > Quellen steht je Quelle, ob sie sauber läuft (Abruf, Menge, Bilder, Neues); bei Ausfall Hinweis oben",
                    "~ Bessere Einsortierung: Straftaten und Notfälle landen in Blaulicht statt in Leute, Wirtschaft und Wissen erkennt man auch am Titel"},
            {"1.08",
                    "~ „Übernehmen“ und „Fertig“ sind zunächst grau und werden erst hell, wenn etwas geändert wurde (App und Widget, alle Menüs)"},
            {"1.07",
                    "~ Einheitliche Buttons: Übernehmen und Abbrechen bei Einstellungen und Auswahlfenstern",
                    "+ Aktualisierung: Auswahl gilt erst nach Übernehmen, neuer Schalter „Ausgeglichen“ (3 Std)",
                    "~ Hinweis hinter den Intervallen: 3 Std ist die sparsamste Automatik, Manuell geht immer"},
            {"1.06",
                    "~ Widget: Nachrichten haben Vorrang, Wetter passt sich der Größe an",
                    "+ Widget klein: Wetter in einer Zeile, Gemerkt und Webcam oben",
                    "+ Widget mittel: zusätzlich die nächsten drei Tage",
                    "+ Pfeil in der Wetterzeile klappt die Details auf und zu",
                    "+ Widget-Menü „Aktualisierung“: Nachrichten und Wetter je Automatisch (15 Min bis 3 Std) oder Manuell",
                    "~ Weniger Akku: kein Neuabruf mehr beim Reiterwechsel, unveränderte Seiten werden nicht neu geladen, ohne Netz kein Abruf, ohne Benachrichtigungen kein Aufwecken",
                    "~ App unverändert: aktualisiert wie bisher beim Öffnen und durch Herunterziehen"},
            {"1.05",
                    "~ Startkarte „Widget“: Auswahl Nachrichten, Wetter oder Beides zum Aufklappen",
                    "+ Hinweis-Karte für Benachrichtigungen, Akku-Optimierung und Autostart",
                    "+ Erkennt den Hersteller und öffnet direkt die passende Systemseite"},
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
