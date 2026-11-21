package de.bergf3x.bgawidget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Wecker-Takt, Neustart des Handys und App-Update: Abruf starten und den naechsten Wecker setzen. */
public class UpdateReceiver extends BroadcastReceiver {
    static final String TICK = "de.bergf3x.bgawidget.TICK";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        Scheduler.schedule(ctx);
        Updater.runAsync(ctx, false, goAsync());
    }
}
