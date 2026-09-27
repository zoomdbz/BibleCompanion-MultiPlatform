package com.dividesbyzer0.biblecompanion

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** One local alarm, refreshed after delivery, reboot, clock changes and preferences. */
object DailyVerseNotificationScheduler {
    const val CHANNEL = "daily_verse"
    private const val NOTIFICATION_ID = 4701
    private const val ALARM_REQUEST = 4702
    const val DELIVER = "com.dividesbyzer0.biblecompanion.DAILY_VERSE"
    private val executor = Executors.newSingleThreadExecutor()
    private val generation = AtomicLong()

    fun allowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)
            if (channel?.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, ALARM_REQUEST,
        Intent(context, DailyVerseNotificationReceiver::class.java).setAction(DELIVER),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun synchronize(context: Context, prefs: PrefsState) {
        val token = generation.incrementAndGet()
        val app = context.applicationContext
        executor.execute {
            if (token == generation.get()) runCatching { synchronizeNow(app, prefs) }
                .onFailure { Log.w("DailyVerse", "Could not refresh reminder", it) }
        }
    }

    private fun synchronizeNow(context: Context, prefs: PrefsState) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pending = alarmIntent(context)
        if (!prefs.dailyVerseNotifications || !allowed(context)) {
            alarm.cancel(pending)
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            return
        }
        if (Build.VERSION.SDK_INT >= 26) {
            val labels = DailyVerseNotifications.labels(context, prefs.appLanguage)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, labels.title, NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val minutes = prefs.dailyVerseNotificationMinuteOfDay.coerceIn(0, 1439)
        val now = System.currentTimeMillis()
        fun onDay(offset: Int) = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, offset)
            set(Calendar.HOUR_OF_DAY, minutes / 60)
            set(Calendar.MINUTE, minutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val today = onDay(0)
        val next = if (today.timeInMillis > now) today else onDay(1)
        // This reminder does not require exact-alarm special access. Android may
        // defer delivery for power management; the UI states that limitation.
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pending)
    }

    fun handle(context: Context, deliver: Boolean, finished: () -> Unit) {
        val app = context.applicationContext
        executor.execute {
            val token = generation.get()
            try {
                val prefs = PrefsRepo(app).initialSnapshot()
                if (deliver && prefs.dailyVerseNotifications && allowed(app)) {
                    val now = Calendar.getInstance()
                    val content = DailyVerseNotifications.forDate(app, prefs,
                        now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))
                    // A settings change must not publish an old-language verse or
                    // re-enable a reminder while its content is being prepared.
                    val latest = PrefsRepo(app).initialSnapshot()
                    if (content != null && token == generation.get() &&
                        latest.dailyVerseNotifications && latest.appLanguage == prefs.appLanguage &&
                        latest.internalBibleVersion == prefs.internalBibleVersion &&
                        latest.divineName == prefs.divineName &&
                        latest.dailyVerseNotificationMinuteOfDay == prefs.dailyVerseNotificationMinuteOfDay) post(app, content)
                }
            } catch (error: Exception) {
                Log.w("DailyVerse", "Could not prepare today's reminder", error)
            } finally {
                // A bad asset must not stop all future reminders.
                if (token == generation.get()) runCatching {
                    synchronizeNow(app, PrefsRepo(app).initialSnapshot())
                }.onFailure { Log.w("DailyVerse", "Could not schedule the next reminder", it) }
                finished()
            }
        }
    }

    private fun post(context: Context, content: DailyVerseNotificationContent) {
        if (!allowed(context)) return
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, content.title, NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.Builder().scheme("biblecompanion").authority("open")
                .appendQueryParameter("route", content.route).build()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(context, 4710, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val copy = Intent(context, CopyDailyVerseActivity::class.java).apply {
            putExtra("text", content.shareText)
            putExtra("label", content.title)
            putExtra("copied", content.copiedLabel)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val copyPending = PendingIntent.getActivity(context, 4711, copy,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val share = Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, content.title)
            putExtra(Intent.EXTRA_TEXT, content.shareText)
        }, content.shareLabel).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val sharePending = PendingIntent.getActivity(context, 4712, share,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_daily_verse)
            .setContentTitle(content.title)
            .setContentText(content.reference)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.shareText))
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0, content.copyLabel, copyPending)
            .addAction(0, content.shareLabel, sharePending)
            .build()
        // Permission can change between the earlier check and this call.
        try { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
        catch (_: SecurityException) { /* The OS permission remains authoritative. */ }
    }
}

class DailyVerseNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val valid = setOf(DailyVerseNotificationScheduler.DELIVER, Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)
        if (intent.action !in valid) return
        val pending = goAsync()
        DailyVerseNotificationScheduler.handle(context,
            intent.action == DailyVerseNotificationScheduler.DELIVER) { pending.finish() }
    }
}

/** Non-exported foreground action: Android restricts background clipboard access. */
class CopyDailyVerseActivity : Activity() {
    private var copied = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = intent.getStringExtra("copied").orEmpty()
            textSize = 18f
            setPadding(32, 48, 32, 48)
        })
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || copied) return
        copied = true
        val text = intent.getStringExtra("text")
        if (!text.isNullOrBlank()) {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(
                ClipData.newPlainText(intent.getStringExtra("label").orEmpty(), text))
        }
        finish()
    }
}
