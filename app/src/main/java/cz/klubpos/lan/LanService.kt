package cz.klubpos.lan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import cz.klubpos.KlubPosApp

/** Keeps the register reachable for waiters while the screen is off (foreground service + Wi-Fi lock). */
class LanService : Service() {
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("lan", "Příjem objednávek", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, packageManager.getLaunchIntentForPackage(packageName), PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = Notification.Builder(this, "lan").setContentTitle("ZapoKredit přijímá objednávky")
            .setContentText("Číšníci a PC se mohou připojit").setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(open).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(1, n)
        @Suppress("DEPRECATION")
        wifiLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "zapokredit").apply { setReferenceCounted(false); acquire() }
        (application as KlubPosApp).lan.start()
        return START_STICKY
    }

    override fun onDestroy() {
        (application as KlubPosApp).lan.stop()
        runCatching { wifiLock?.release() }
        super.onDestroy()
    }

    companion object {
        fun start(c: Context) = runCatching {
            val i = Intent(c, LanService::class.java)
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
        }
        fun stop(c: Context) = runCatching { c.stopService(Intent(c, LanService::class.java)) }
    }
}
