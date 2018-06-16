//
//   Calendar Notifications Plus  
//   Copyright (C) 2016 Sergey Parshin (s.parshin.sc@gmail.com)
//
//   This program is free software; you can redistribute it and/or modify
//   it under the terms of the GNU General Public License as published by
//   the Free Software Foundation; either version 3 of the License, or
//   (at your option) any later version.
// 
//   This program is distributed in the hope that it will be useful,
//   but WITHOUT ANY WARRANTY; without even the implied warranty of
//   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//   GNU General Public License for more details.
//
//   You should have received a copy of the GNU General Public License
//   along with this program; if not, write to the Free Software Foundation,
//   Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
//

package com.github.quarck.calnotifyng

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import com.github.quarck.calnotifyng.prefs.PreferenceUtils
import com.github.quarck.calnotifyng.utils.PersistentStorageBase
import com.github.quarck.calnotifyng.utils.toIntOrNull


data class NotificationSettingsSnapshot
(
        val enableNotificationMute: Boolean,
        val appendEmptyAction: Boolean,
        val useAlarmStream: Boolean
)

class Settings(context: Context) : PersistentStorageBase(context) {

    var devModeEnabled: Boolean
        get() = getBoolean(DEVELOPER_MODE_KEY, false)
        set(value) = setBoolean(DEVELOPER_MODE_KEY, value)

    var notificationAddEmptyAction: Boolean
        get() = getBoolean(NOTIFICATION_ADD_EMPTY_ACTION_KEY, false)
        set(value) = setBoolean(NOTIFICATION_ADD_EMPTY_ACTION_KEY, value)

    var viewAfterEdit: Boolean
        get() = getBoolean(VIEW_AFTER_EDIT_KEY, true)
        set(value) = setBoolean(VIEW_AFTER_EDIT_KEY, value)

    var snoozePresetsRaw: String
        get() = getString(SNOOZE_PRESET_KEY, DEFAULT_SNOOZE_PRESET)
        set(value) = setString(SNOOZE_PRESET_KEY, value)

    val snoozePresets: LongArray
        get() {
            var ret = PreferenceUtils.parseSnoozePresets(snoozePresetsRaw)

            if (ret == null)
                ret = PreferenceUtils.parseSnoozePresets(DEFAULT_SNOOZE_PRESET)

            if (ret == null || ret.size == 0)
                ret = Consts.DEFAULT_SNOOZE_PRESETS

            return ret;
        }

    var notificationUseAlarmStream: Boolean
        get() = getBoolean(USE_ALARM_STREAM_FOR_NOTIFICATION_KEY, false)
        set(value) = setBoolean(USE_ALARM_STREAM_FOR_NOTIFICATION_KEY, value)

    val remindersEnabled: Boolean
        get() = getBoolean(ENABLE_REMINDERS_KEY, false)

    var remindersIntervalMillisPattern: LongArray
        get() {
            val raw = getString(REMINDER_INTERVAL_PATTERN_KEY, "")

            val ret: LongArray?

            if (!raw.isEmpty()) {
                ret = PreferenceUtils.parseSnoozePresets(raw)
            } else {
                val intervalSeconds = getInt(REMIND_INTERVAL_SECONDS_KEY, 0)
                if (intervalSeconds != 0) {
                    ret = longArrayOf(intervalSeconds * 1000L)
                }
                else {
                    val intervalMinutes = getInt(REMIND_INTERVAL_MINUTES_KEY, DEFAULT_REMINDER_INTERVAL_MINUTES)
                    ret = longArrayOf(intervalMinutes * 60L * 1000L)
                }
            }

            return ret ?: longArrayOf(DEFAULT_REMINDER_INTERVAL_SECONDS * 1000L)
        }
        set(value) {
            setString(REMINDER_INTERVAL_PATTERN_KEY, PreferenceUtils.formatPattern(value))
        }

    fun reminderIntervalMillisForIndex(index: Int): Long {
        val pattern = remindersIntervalMillisPattern
        val value = pattern[index % pattern.size]
        return Math.max(value, Consts.MIN_REMINDER_INTERVAL_SECONDS * 1000L)
    }

    fun currentAndNextReminderIntervalsMillis(indexCurrent: Int): Pair<Long, Long> {
        val pattern = remindersIntervalMillisPattern
        val minInterval = Consts.MIN_REMINDER_INTERVAL_SECONDS * 1000L

        val current = Math.max(pattern[indexCurrent % pattern.size], minInterval)
        val next = Math.max(pattern[(indexCurrent + 1) % pattern.size], minInterval)

        return Pair(current, next)
    }

    val maxNumberOfReminders: Int
        get() = getString(MAX_REMINDERS_KEY, DEFAULT_MAX_REMINDERS).toIntOrNull() ?: 0

    val quietHoursEnabled: Boolean
        get() = getBoolean(ENABLE_QUIET_HOURS_KEY, false)

    val quietHoursFrom: Pair<Int, Int>
        get() = PreferenceUtils.unpackTime(getInt(QUIET_HOURS_FROM_KEY, 0))

    val quietHoursTo: Pair<Int, Int>
        get() = PreferenceUtils.unpackTime(getInt(QUIET_HOURS_TO_KEY, 0))

    fun getCalendarIsHandled(calendarId: Long) =
            getBoolean("$CALENDAR_IS_HANDLED_KEY_PREFIX.$calendarId", true)

    fun setCalendarIsHandled(calendarId: Long, enabled: Boolean) =
            setBoolean("$CALENDAR_IS_HANDLED_KEY_PREFIX.$calendarId", enabled)

    var versionCodeFirstInstalled: Long
        get() = getLong(VERSION_CODE_FIRST_INSTALLED_KEY, 0L)
        set(value) = setLong(VERSION_CODE_FIRST_INSTALLED_KEY, value)

    val useSetAlarmClock: Boolean
        get() = getBoolean(BEHAVIOR_USE_SET_ALARM_CLOCK_KEY, true)

    val useSetAlarmClockForFailbackEventPaths: Boolean
        get() = getBoolean(BEHAVIOR_USE_SET_ALARM_CLOCK_FOR_FAILBACK_KEY, false)

    val shouldRemindForEventsWithNoReminders: Boolean
        get() = getBoolean(SHOULD_REMIND_FOR_EVENTS_WITH_NO_REMINDERS_KEY, false)

    val defaultReminderTimeForEventWithNoReminder: Long
        get() = getInt(DEFAULT_REMINDER_TIME_FOR_EVENTS_WITH_NO_REMINDER_KEY, 15) * 60L * 1000L

    val defaultReminderTimeForAllDayEventWithNoreminder: Long
        get() = getInt(DEFAULT_REMINDER_TIME_FOR_ALL_DAY_EVENTS_WITH_NO_REMINDER, -480) * 60L * 1000L

    val manualCalWatchScanWindow: Long
        get() = getLong(CALENDAR_MANUAL_WATCH_RELOAD_WINDOW_KEY, 30L * 24L * 3600L * 1000L) // 1 month by default

    val dontShowDeclinedEvents: Boolean
        get() = getBoolean(DONT_SHOW_DECLINED_EVENTS_KEY, false)

    val dontShowCancelledEvents: Boolean
        get() = getBoolean(DONT_SHOW_CANCELLED_EVENTS_KEY, false)

    val dontShowAllDayEvents: Boolean
        get() = getBoolean(DONT_SHOW_ALL_DAY_EVENTS_KEY, false)

    var enableMonitorDebug: Boolean
        get() = getBoolean(ENABLE_MONITOR_DEBUGGING_KEY, false)
        set(value) = setBoolean(ENABLE_MONITOR_DEBUGGING_KEY, value)

    val firstDayOfWeek: Int
        get() = getString(FIRST_DAY_OF_WEEK_KEY, "-1").toIntOrNull() ?: -1

    val shouldKeepLogs: Boolean
        get() = getBoolean(KEEP_APP_LOGS_KEY, false)

    val enableCalendarRescan: Boolean
        get() = getBoolean(ENABLE_CALENDAR_RESCAN_KEY, true)

    val rescanCreatedEvent: Boolean
        get() = true

    val notifyOnEmailOnlyEvents: Boolean
        get() = getBoolean(NOTIFY_ON_EMAIL_ONLY_EVENTS_KEY, false)

    val notificationSettingsSnapshot: NotificationSettingsSnapshot
        get() = NotificationSettingsSnapshot(
                ////notificationSwipeDoesSnooze = notificationSwipeDoesSnooze,
                enableNotificationMute = remindersEnabled,
                appendEmptyAction = notificationAddEmptyAction,
                useAlarmStream = notificationUseAlarmStream
        )

    companion object {

        // Preferences keys

        const val VIBRATION_PATTERN_KEY = "pref_vibration_pattern"

        private const val SNOOZE_PRESET_KEY = "pref_snooze_presets"

        private const val VIEW_AFTER_EDIT_KEY = "show_event_after_reschedule"

        private const val ENABLE_REMINDERS_KEY = "enable_reminding_key"
        private const val REMIND_INTERVAL_MINUTES_KEY = "remind_interval_key2"
        private const val REMIND_INTERVAL_SECONDS_KEY = "remind_interval_key_seconds"
        private const val REMINDER_INTERVAL_PATTERN_KEY = "remind_interval_key_pattern"

        private const val MAX_REMINDERS_KEY = "reminder_max_reminders"

        private const val ENABLE_QUIET_HOURS_KEY = "enable_quiet_hours"
        private const val QUIET_HOURS_FROM_KEY = "quiet_hours_from"
        private const val QUIET_HOURS_TO_KEY = "quiet_hours_to"

        private const val CALENDAR_IS_HANDLED_KEY_PREFIX = "calendar_handled_"

        private const val VERSION_CODE_FIRST_INSTALLED_KEY = "first_installed_ver"

        private const val BEHAVIOR_USE_SET_ALARM_CLOCK_KEY = "use_set_alarm_clock"
        private const val BEHAVIOR_USE_SET_ALARM_CLOCK_FOR_FAILBACK_KEY = "use_set_alarm_clock_for_failback"

        private const val SHOULD_REMIND_FOR_EVENTS_WITH_NO_REMINDERS_KEY = "remind_events_no_rmdnrs"
        private const val DEFAULT_REMINDER_TIME_FOR_EVENTS_WITH_NO_REMINDER_KEY = "default_rminder_time"
        private const val DEFAULT_REMINDER_TIME_FOR_ALL_DAY_EVENTS_WITH_NO_REMINDER = "default_all_day_rminder_time"

        private const val CALENDAR_MANUAL_WATCH_RELOAD_WINDOW_KEY = "manual_watch_reload_window"

        private const val DONT_SHOW_DECLINED_EVENTS_KEY = "dont_show_declined_events"
        private const val DONT_SHOW_CANCELLED_EVENTS_KEY = "dont_show_cancelled_events"
        private const val DONT_SHOW_ALL_DAY_EVENTS_KEY = "dont_show_all_day_events"

        private const val ENABLE_MONITOR_DEBUGGING_KEY = "enableMonitorDebug"

        private const val FIRST_DAY_OF_WEEK_KEY = "first_day_of_week"

        private const val USE_ALARM_STREAM_FOR_NOTIFICATION_KEY = "use_alarm_stream_for_notification"

        private const val KEEP_APP_LOGS_KEY = "keep_logs"

        private const val ENABLE_CALENDAR_RESCAN_KEY = "enable_manual_calendar_rescan"
        private const val NOTIFY_ON_EMAIL_ONLY_EVENTS_KEY = "notify_on_email_only_events"

        private const val DEVELOPER_MODE_KEY = "dev"

        private const val NOTIFICATION_ADD_EMPTY_ACTION_KEY = "add_empty_action_to_the_end"

        // Default values
        internal const val DEFAULT_SNOOZE_PRESET = "15m, 1h, 4h, 1d, -5m"
        internal const val DEFAULT_REMINDER_INTERVAL_MINUTES = 10
        internal const val DEFAULT_REMINDER_INTERVAL_SECONDS = 600
        internal const val DEFAULT_MAX_REMINDERS = "0"
    }
}
