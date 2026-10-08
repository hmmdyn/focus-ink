package com.dain.focusink.system

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.dain.focusink.FocusInkApp
import com.dain.focusink.MainActivity
import com.dain.focusink.R
import com.dain.focusink.core.AppState
import com.dain.focusink.core.Dates
import com.dain.focusink.core.Focus
import com.dain.focusink.core.Settings
import com.dain.focusink.core.Stats
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object Notifications {
    const val CH_SESSION = "session"
    const val CH_REMINDER = "reminder"
    const val ID_SESSION = 1
    const val ID_REVIEW = 2
    const val ID_SUNSET = 3

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_SESSION, "집중 블록 종료", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_REMINDER, "리뷰·선셋 알림", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notify(ctx: Context, id: Int, channel: String, title: String, text: String) {
        if (!canNotify(ctx)) return
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            ctx.getSystemService(NotificationManager::class.java).notify(id, n)
        } catch (_: SecurityException) {
        }
    }
}

object Alarms {
    const val ACTION_SESSION_END = "com.dain.focusink.SESSION_END"
    const val ACTION_REVIEW = "com.dain.focusink.REVIEW"
    const val ACTION_SUNSET = "com.dain.focusink.SUNSET"

    private fun pending(ctx: Context, action: String, req: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, req,
            Intent(ctx, AlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    private fun setExact(ctx: Context, at: Long, pi: PendingIntent) {
        val am = am(ctx)
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun scheduleSessionEnd(ctx: Context, at: Long) = setExact(ctx, at, pending(ctx, ACTION_SESSION_END, 1))

    fun cancelSessionEnd(ctx: Context) = am(ctx).cancel(pending(ctx, ACTION_SESSION_END, 1))

    fun nextOccurrence(hhmm: String, zone: ZoneId = ZoneId.systemDefault()): Long {
        val t = Dates.parseTime(hhmm)
        var dt = LocalDate.now(zone).atTime(t)
        if (!dt.isAfter(LocalDateTime.now(zone))) dt = dt.plusDays(1)
        return dt.atZone(zone).toInstant().toEpochMilli()
    }

    fun scheduleDaily(ctx: Context, s: Settings) {
        val review = pending(ctx, ACTION_REVIEW, 2)
        val sunset = pending(ctx, ACTION_SUNSET, 3)
        if (!s.remindersEnabled || !s.onboarded) {
            am(ctx).cancel(review)
            am(ctx).cancel(sunset)
            return
        }
        setExact(ctx, nextOccurrence(s.reviewTime), review)
        setExact(ctx, nextOccurrence(s.sunsetTime), sunset)
    }

    fun rescheduleAll(ctx: Context, state: AppState) {
        scheduleDaily(ctx, state.settings)
        val a = state.active
        if (a != null && Focus.endsAt(a) > System.currentTimeMillis()) scheduleSessionEnd(ctx, Focus.endsAt(a))
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext as FocusInkApp
        val s = app.repo.state.value
        val now = System.currentTimeMillis()
        when (intent.action) {
            Alarms.ACTION_SESSION_END -> {
                val a = s.active ?: return
                if (Focus.remainingMinutes(a, now) <= 1) {
                    Notifications.notify(
                        ctx, Notifications.ID_SESSION, Notifications.CH_SESSION,
                        "블록 완료 · ${a.label}",
                        "${a.plannedMinutes}분을 채웠어요. 회고를 적고 ${s.settings.breakMinutes}분 쉬세요. 폰은 보지 않기.",
                    )
                }
            }
            Alarms.ACTION_REVIEW -> {
                if (!Stats.hasReview(s, Dates.today(now))) {
                    Notifications.notify(
                        ctx, Notifications.ID_REVIEW, Notifications.CH_REMINDER,
                        "저녁 리뷰", "오늘 결과 확인 → 미완료 정리 → 내일 Top 3 (3분)",
                    )
                }
                Alarms.scheduleDaily(ctx, s.settings)
            }
            Alarms.ACTION_SUNSET -> {
                Notifications.notify(
                    ctx, Notifications.ID_SUNSET, Notifications.CH_REMINDER,
                    "디지털 선셋", "폰은 주차장에. 화면을 끄고 책이나 일기로 마무리하세요.",
                )
                Alarms.scheduleDaily(ctx, s.settings)
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = ctx.applicationContext as FocusInkApp
        Alarms.rescheduleAll(ctx, app.repo.state.value)
    }
}
