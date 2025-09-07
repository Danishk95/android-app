# Project structure (Android Studio, Kotlin)
# Create a new Empty Views Activity project (Minimum SDK: Android 8.0 Oreo API 26 or lower if you need)
# Then replace/add the following files.


// -----------------------------
// app/src/main/AndroidManifest.xml
// -----------------------------
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <!-- For Android 9+ separate type declarations (optional, framework will infer). -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.App" >

        <receiver
            android:name=".power.PowerReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.ACTION_POWER_CONNECTED" />
                <action android:name="android.intent.action.ACTION_POWER_DISCONNECTED" />
            </intent-filter>
        </receiver>

        <receiver
            android:name=".power.BootReceiver"
            android:enabled="true"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.LOCKED_BOOT_COMPLETED" />
            </intent-filter>
        </receiver>

        <service
            android:name=".service.BatteryMonitorService"
            android:exported="false"
            android:foregroundServiceType="dataSync" />

        <activity
            android:name=".ui.MainActivity"
            android:exported="true" >
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>


// -----------------------------
// app/build.gradle (Module: app) – only the important parts
// -----------------------------
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.example.chargelogger'
    compileSdk 35

    defaultConfig {
        applicationId "com.example.chargelogger"
        minSdk 24
        targetSdk 35
        versionCode 1
        versionName "1.0"
    }

    buildFeatures {
        viewBinding true
    }
}

dependencies {
    implementation 'androidx.core:core-ktx:1.13.1'
    implementation 'androidx.appcompat:appcompat:1.7.0'
    implementation 'com.google.android.material:material:1.12.0'
    implementation 'androidx.recyclerview:recyclerview:1.3.2'
}


// -----------------------------
// app/src/main/res/layout/activity_main.xml
// -----------------------------
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <Button
        android:id="@+id/btnStartService"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Start Background Monitor" />

    <Button
        android:id="@+id/btnShare"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:text="Share CSV Log" />

    <TextView
        android:id="@+id/tvPath"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:text="Log file path will appear here" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rvLogs"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_marginTop="8dp"
        android:layout_weight="1" />
</LinearLayout>


// -----------------------------
// app/src/main/java/com/example/chargelogger/util/Utils.kt
// -----------------------------
package com.example.chargelogger.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Utils {
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun now(): String = sdf.format(Date())

    fun getBatteryPct(context: Context): Int {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt() else -1
    }

    fun getBatteryStatus(context: Context): Int {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    }
}


// -----------------------------
// app/src/main/java/com/example/chargelogger/util/LogStore.kt
// -----------------------------
package com.example.chargelogger.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter

class LogStore(private val context: Context) {
    private val file: File by lazy {
        File(context.filesDir, "charge_log.csv").apply {
            if (!exists()) {
                writeText("timestamp,event,battery%\n")
            }
        }
    }

    fun append(event: String, pct: Int) {
        FileWriter(file, true).use { fw ->
            fw.append("${Utils.now()},$event,$pct\n")
        }
    }

    fun readAll(): List<String> = file.readLines()

    fun filePath(): String = file.absolutePath

    fun asShareUri(): Uri {
        return FileProvider.getUriForFile(
            context,
            context.packageName + ".provider",
            file
        )
    }
}


// -----------------------------
// app/src/main/java/com/example/chargelogger/power/PowerReceiver.kt
// -----------------------------
package com.example.chargelogger.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.chargelogger.util.LogStore
import com.example.chargelogger.util.Utils

class PowerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = LogStore(context)
        val pct = Utils.getBatteryPct(context)
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> store.append("PLUGGED_IN", pct)
            Intent.ACTION_POWER_DISCONNECTED -> store.append("UNPLUGGED", pct)
        }
    }
}


// -----------------------------
// app/src/main/java/com/example/chargelogger/power/BootReceiver.kt
// -----------------------------
package com.example.chargelogger.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.chargelogger.service.BatteryMonitorService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val i = Intent(context, BatteryMonitorService::class.java)
        context.startForegroundService(i)
    }
}


// -----------------------------
// app/src/main/java/com/example/chargelogger/service/BatteryMonitorService.kt
// -----------------------------
package com.example.chargelogger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.BatteryManager
import androidx.core.app.NotificationCompat
import com.example.chargelogger.R
import com.example.chargelogger.ui.MainActivity
import com.example.chargelogger.util.LogStore

class BatteryMonitorService : Service() {
    private val channelId = "charge_monitor_channel"
    private val notifId = 101

    private lateinit var store: LogStore
    private var lastLoggedFull: Long = 0L

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val pct = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt() else -1

            val isFull = pct == 100 || status == BatteryManager.BATTERY_STATUS_FULL
            if (isFull) {
                val now = System.currentTimeMillis()
                // Avoid spamming: only log once every 10 minutes for FULL
                if (now - lastLoggedFull > 10 * 60 * 1000) {
                    store.append("FULL_100", pct)
                    lastLoggedFull = now
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = LogStore(this)
        createNotification()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(notifId, buildNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(batteryReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "Charging Monitor", NotificationManager.IMPORTANCE_MIN)
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle("Charging monitor running")
            .setContentText("Logging plug/unplug and 100% events")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}


// -----------------------------
// app/src/main/java/com/example/chargelogger/ui/MainActivity.kt
// -----------------------------
package com.example.chargelogger.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chargelogger.databinding.ActivityMainBinding
import com.example.chargelogger.service.BatteryMonitorService
import com.example.chargelogger.util.LogStore

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var store: LogStore

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) Toast.makeText(this, "Notifications permission denied", Toast.LENGTH_SHORT).show()
        startMonitor()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = LogStore(this)

        binding.rvLogs.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        refreshList()

        binding.tvPath.text = "Log path: ${store.filePath()}"

        binding.btnStartService.setOnClickListener {
            ensurePermissionAndStart()
        }

        binding.btnShare.setOnClickListener {
            shareCsv()
        }
    }

    private fun refreshList() {
        val all = store.readAll()
        val adapter = SimpleStringAdapter(all)
        binding.rvLogs.adapter = adapter
    }

    private fun ensurePermissionAndStart() {
        if (Build.VERSION.SDK_INT >= 33) {
            when (PackageManager.PERMISSION_GRANTED) {
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) -> startMonitor()
                else -> requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else startMonitor()
    }

    private fun startMonitor() {
        val i = Intent(this, BatteryMonitorService::class.java)
        ContextCompat.startForegroundService(this, i)
        Toast.makeText(this, "Background monitor started", Toast.LENGTH_SHORT).show()
    }

    private fun shareCsv() {
        val uri = store.asShareUri()
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Share charge_log.csv"))
    }
}


// -----------------------------
// app/src/main/java/com/example/chargelogger/ui/SimpleStringAdapter.kt
// -----------------------------
package com.example.chargelogger.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SimpleStringAdapter(private val items: List<String>) : RecyclerView.Adapter<SimpleStringAdapter.VH>() {
    class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false) as TextView
        return VH(tv)
    }
    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.tv.text = items[position]
    }
    override fun getItemCount(): Int = items.size
}


// -----------------------------
// app/src/main/res/xml/file_paths.xml
// -----------------------------
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <files-path name="internal" path="." />
</paths>


// -----------------------------
// app/src/main/res/drawable/ic_stat_name.xml (simple vector icon)
// -----------------------------
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#000000"
        android:pathData="M7,2h10v2H7zM6,6h12v2H6zM5,10h14v10H5z" />
</vector>


// -----------------------------
// app/src/main/res/xml/provider_paths.xml (Provider config referenced by FileProvider)
// -----------------------------
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <files-path name="files" path="." />
</paths>


// -----------------------------
// app/src/main/res/xml/filepaths.xml (alias if needed)
// -----------------------------
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <files-path name="files" path="." />
</paths>


// -----------------------------
// app/src/main/res/xml/provider.xml (choose one unique file and reference it in manifest below)
// -----------------------------
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <files-path name="share" path="." />
</paths>


// -----------------------------
// app/src/main/res/xml (Manifest provider declaration snippet)
// -----------------------------
<!-- Add inside <application> in AndroidManifest to enable FileProvider sharing -->
<!--
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.provider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/provider" />
</provider>
-->


// -----------------------------
// app/src/main/res/values/strings.xml (make sure app_name exists)
// -----------------------------
<resources>
    <string name="app_name">Charge Logger</string>
</resources>


// -----------------------------
// Notes
// -----------------------------
// 1) PowerReceiver logs PLUGGED_IN / UNPLUGGED with timestamp + battery%.
// 2) BatteryMonitorService (foreground) listens ACTION_BATTERY_CHANGED and logs FULL_100 once when 100% is reached.
// 3) BootReceiver restarts the foreground service after reboot so 100% events are still captured.
// 4) Logs are stored at: filesDir/charge_log.csv (first line is header). Share via button.
// 5) On Android 13+ the app asks for POST_NOTIFICATIONS permission the first time you tap "Start Background Monitor".
