package de.bergf3x.bgawidget;

/** Kurze Versionshistorie fuer das Info-Menue. Neueste Version zuerst. Praefix: + neu, - entfernt, ~ geaendert/behoben. */
final class Changelog {
    private Changelog() { }

    static final String[][] LOG = {
            {"1.03",
                    "~ Änderungsliste im Info-Menü ergänzt (aufklappbar)"},
            {"1.02",
                    "+ Info-Menü mit Version und Datum",
                    "+ Versionsnummer wird bei jedem Build hochgezählt, APK-Name enthält die Version",
                    "+ Pfeil ▾ an jedem Themen-Reiter mit Untermenü, in App und Widget",
                    "+ Schließen-Knopf in der Webcam-Blase",
                    "~ Webcam-Liste: Live-Bild und Nur Webseite klar abgesetzt",
                    "~ Webcam-Liste springt beim Zurück an die alte Stelle"},
            {"1.00",
                    "+ Eigenständige App mit „Widget hinzufügen“",
                    "+ Webcams als Live-Bild in der Blase, Vollbild mit Zoom",
                    "+ Neues Icon und Name „BGL Wetter & News“"},
    };
}
