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

package com.github.quarck.calnotifyng.notification

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.text.format.DateUtils
import com.github.quarck.calnotifyng.*
import com.github.quarck.calnotifyng.calendar.*
import com.github.quarck.calnotifyng.eventsstorage.EventsStorage
import com.github.quarck.calnotifyng.logs.DevLog
import com.github.quarck.calnotifyng.prefs.PreferenceUtils
import com.github.quarck.calnotifyng.quiethours.QuietHoursManager
import com.github.quarck.calnotifyng.reminders.ReminderState
import com.github.quarck.calnotifyng.textutils.EventFormatter
import com.github.quarck.calnotifyng.textutils.EventFormatterInterface
import com.github.quarck.calnotifyng.ui.MainActivity
import com.github.quarck.calnotifyng.ui.SnoozeActivityNoRecents
import com.github.quarck.calnotifyng.utils.*

@Suppress("VARIABLE_WITH_REDUNDANT_INITIALIZER")
class EventNotificationManager : EventNotificationManagerInterface {

    override fun onEventAdded(ctx: Context, formatter: EventFormatterInterface, event: EventAlertRecord) {
        postEventNotifications(ctx, formatter, false, event.eventId)
    }

    override fun onEventRestored(context: Context, formatter: EventFormatterInterface, event: EventAlertRecord) {

        if (event.displayStatus != EventDisplayStatus.Hidden) {
            EventsStorage(context).use {
                it.updateEvent(event, displayStatus = EventDisplayStatus.Hidden)
            }
        }

        postEventNotifications(context, formatter, true, null)
    }

    override fun onEventDismissing(context: Context, eventId: Long, notificationId: Int) {
        removeNotification(context, notificationId)
    }

    override fun onEventsDismissing(context: Context, events: Collection<EventAlertRecord>) {
        removeNotifications(context, events)
    }

    override fun onEventDismissed(context: Context, formatter: EventFormatterInterface, eventId: Long, notificationId: Int) {
        removeNotification(context, notificationId)
        postEventNotifications(context, formatter, false, null)
    }

    override fun onEventsDismissed(context: Context, formatter: EventFormatterInterface, events: Collection<EventAlertRecord>, postNotifications: Boolean, hasActiveEvents: Boolean) {

        for (event in events) {
            removeNotification(context, event.notificationId)
        }

        if (!hasActiveEvents) {
            removeNotification(context, Consts.NOTIFICATION_ID_BUNDLED_GROUP)
        }

        if (postNotifications) {
            postEventNotifications(context, formatter, false, null)
        }
    }

    override fun onEventSnoozed(context: Context, formatter: EventFormatterInterface, eventId: Long, notificationId: Int) {
        removeNotification(context, notificationId)
        postEventNotifications(context, formatter, false, null)
    }

    override fun onEventMuteToggled(context: Context, formatter: EventFormatterInterface, event: EventAlertRecord) {

        if (event.displayStatus != EventDisplayStatus.DisplayedNormal)
            return

        val settings = Settings(context)

        val notificationSettings = settings.notificationSettingsSnapshot

        postNotification(
                ctx = context,
                formatter = formatter,
                event = event,
                notificationSettings = notificationSettings,
                isRepost = true,
                snoozePresetsNotFiltered = settings.snoozePresets,
                shouldBeQuiet = true
        )

        context.notificationManager.cancel(Consts.NOTIFICATION_ID_REMINDER)
    }

    override fun onAllEventsSnoozed(context: Context) {
        context.notificationManager.cancelAll()
    }

    /**
     * @param events - events to sort
     * @returns pair of boolean and list, boolean means "all collapsed"
     */
    private fun sortEvents(
            events: List<EventAlertRecord>
    ): Pair<Boolean, List<EventAlertRecord>> {

        var allCollapsed = false

        if (events.size > Consts.MAX_UNCOLLAPSED_NOTIFICATIONS)
            allCollapsed = true
        else if (events.any {it.displayStatus == EventDisplayStatus.DisplayedCollapsed})
            allCollapsed = true

        return Pair(allCollapsed, events)
    }

    /**
     * @returns pair of boolean and list, boolean means "all collapsed"
     */
    private fun sortEvents(
            db: EventsStorage,
            currentTime: Long
    ): Pair<Boolean, List<EventAlertRecord>> {

        // requests with snoozedUntil == 0 are currently visible ones
        // requests with experied snoozedUntil are the ones to beep about
        // everything else should be hidden and waiting for the next alarm

        val events =
                db.events.filter {
                    ((it.snoozedUntil == 0L)
                            || (it.snoozedUntil < currentTime + Consts.ALARM_THRESHOLD))
                            && it.isNotSpecial
                }

        return sortEvents(events)
    }

    override fun postEventNotifications(context: Context, formatter: EventFormatterInterface, force: Boolean, primaryEventId: Long?) {
        //
        val settings = Settings(context)

        val currentTime = System.currentTimeMillis()

        val isQuietPeriodActive = QuietHoursManager.getSilentUntil(settings) != 0L

        //var updatedAnything = false

        EventsStorage(context).use {
            db ->

            val (allCollapsed, events) = sortEvents(db, currentTime)

            if (!allCollapsed) {
                hideCollapsedEventsNotification(context)

                if (!events.isEmpty()) {
                    postDisplayedEventNotifications(
                            context = context,
                            db = db,
                            settings = settings,
                            formatter = formatter,
                            events = events,
                            force = force,
                            isQuietPeriodActive = isQuietPeriodActive,
                            primaryEventId = primaryEventId
                    )
                } else {
                    removeNotification(context, Consts.NOTIFICATION_ID_BUNDLED_GROUP)
                }
            }
            else {
                removeNotification(context, Consts.NOTIFICATION_ID_BUNDLED_GROUP)

                postEverythingCollapsed(
                        context = context,
                        db = db,
                        events = events,
                        force = force,
                        isQuietPeriodActive = isQuietPeriodActive,
                        settings = settings,
                        primaryEventId = primaryEventId
                )
            }
        }
    }

    override fun fireEventReminder(
            context: Context, itIsAfterQuietHoursReminder: Boolean,
            hasActiveAlarms: Boolean) {

        val settings = Settings(context)
        //val isQuietPeriodActive = !hasActiveAlarms && (QuietHoursManager.getSilentUntil(settings) != 0L)

        EventsStorage(context).use {
            db ->

            val notificationSettings =
                    settings.notificationSettingsSnapshot

            val activeEvents = db.events.filter { it.isNotSnoozed && it.isNotSpecial && !it.isTask  && !it.isMuted}

            val numActiveEvents = activeEvents.count()
            val lastStatusChange = activeEvents.map { it.lastStatusChangeTime }.max() ?: 0L

            if (numActiveEvents > 0) {
                postReminderNotification(
                        context,
                        numActiveEvents,
                        lastStatusChange,
                        notificationSettings,
                        itIsAfterQuietHoursReminder,
                        hasActiveAlarms
                )
            }
            else {
                context.notificationManager.cancel(Consts.NOTIFICATION_ID_REMINDER)
            }
        }
    }

    override fun cleanupEventReminder(context: Context) {
        context.notificationManager.cancel(Consts.NOTIFICATION_ID_REMINDER)
    }

    ///
    /// Post events in collapsed state
    ///
    private fun postEverythingCollapsed(
            context: Context,
            db: EventsStorage,
            events: List<EventAlertRecord>,
            settings: Settings,
            force: Boolean,
            isQuietPeriodActive: Boolean,
            primaryEventId: Long?
    ): Boolean {

        if (events.isEmpty()) {
            hideCollapsedEventsNotification(context)
            return false
        }

        DevLog.info(context, LOG_TAG, "Posting ${events.size} notifications in collapsed view")

        val hasAlarms = events.any { it.isAlarm }

        val notificationsSettings = settings.notificationSettingsSnapshot

        var postedNotification = false

        var shouldPlayAndVibrate = false

        // make sure we remove full notifications
        removeNotifications(context, events)

        val eventsToUpdate = events
                .filter {
                    it.snoozedUntil != 0L || it.displayStatus != EventDisplayStatus.DisplayedCollapsed
                }

        db.updateEvents(
                eventsToUpdate,
                snoozedUntil = 0L,
                displayStatus = EventDisplayStatus.DisplayedCollapsed
        )

        for (event in events) {

            if (event.snoozedUntil == 0L) {

                if ((event.displayStatus != EventDisplayStatus.DisplayedCollapsed) || force) {
                    // currently not displayed or forced -- post notifications
//                    DevLog.debug(LOG_TAG, "Posting notification id ${event.notificationId}, eventId ${event.eventId}")

                    var shouldBeQuiet = false

                    @Suppress("CascadeIf")
                    if (force) {
                        // If forced to re-post all notifications - we only have to actually display notifications
                        // so not playing sound / vibration here
                        //DevLog.debug(LOG_TAG, "event ${event.eventId}: 'forced' notification - staying quiet")
                        shouldBeQuiet = true

                    }
                    else if (event.displayStatus == EventDisplayStatus.DisplayedNormal) {

                        //DevLog.debug(LOG_TAG, "event ${event.eventId}: notification was displayed, not playing sound")
                        shouldBeQuiet = true

                    }
                    else if (isQuietPeriodActive) {

                        // we are in a silent period, normally we should always be quiet, but there
                        // are a few exclusions
                        @Suppress("LiftReturnOrAssignment")
                        if (primaryEventId != null && event.eventId == primaryEventId) {
                            // this is primary event -- play based on use preference for muting
                            // primary event reminders
                            //DevLog.debug(LOG_TAG, "event ${event.eventId}: quiet period and this is primary notification - sound according to settings")
                            shouldBeQuiet = settings.quietHoursMutePrimary && !event.isAlarm
                        }
                        else {
                            // not a primary event -- always silent in silent period
                            //DevLog.debug(LOG_TAG, "event ${event.eventId}: quiet period and this is NOT primary notification quiet")
                            shouldBeQuiet = true
                        }
                    }

                    shouldBeQuiet = shouldBeQuiet || event.isMuted

                    postedNotification = true
                    shouldPlayAndVibrate = shouldPlayAndVibrate || !shouldBeQuiet

                }
            }
            else {
                // This event is currently snoozed and switching to "Shown" state

                //DevLog.debug(LOG_TAG, "Posting snoozed notification id ${event.notificationId}, eventId ${event.eventId}, isQuietPeriodActive=$isQuietPeriodActive")

                postedNotification = true
                shouldPlayAndVibrate = shouldPlayAndVibrate || (!isQuietPeriodActive && !event.isMuted)
            }
        }

        // now build actual notification and notify
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, MAIN_ACTIVITY_NUM_NOTIFICATIONS_COLLAPSED_CODE, intent, 0)

        val numEvents = events.size

        var soundState = NotificationChannelManager.SoundState.Normal
        if (!shouldPlayAndVibrate)
            soundState = NotificationChannelManager.SoundState.Silent
        else if (notificationsSettings.useAlarmStream)
            soundState = NotificationChannelManager.SoundState.Alarm

        val channel = NotificationChannelManager.createNotificationChannelForPurpose(
                context = context,
                isReminder = false,
                soundState = soundState
        )

        val notificationStyle = Notification.InboxStyle()

        val eventsSorted = events.sortedByDescending { it.instanceStartTime }

        val appendPlusMoreLine = eventsSorted.size > 5
        val lines = eventsSorted.take(if (appendPlusMoreLine) 4 else 5).map {
                    ev ->
                    val flags =
                            if (DateUtils.isToday(ev.displayedStartTime))
                                DateUtils.FORMAT_SHOW_TIME
                            else
                                DateUtils.FORMAT_SHOW_DATE
                    "${DateUtils.formatDateTime(context, ev.displayedStartTime, flags)}: ${ev.title}"
                }.toMutableList()

        if (appendPlusMoreLine) {
            lines.add(context.getString(R.string.plus_more).format(events.size - 4))
        }

        lines.forEach { notificationStyle.addLine(it) }
        notificationStyle.setBigContentTitle("")

        val builder =
                Notification.Builder(context, channel)
                        .setContentTitle(context.getString(R.string.multiple_events_single_notification).format(events.size))
                        .setContentText(if (lines.size > 0) lines[0] else "")
                        .setSmallIcon(R.drawable.stat_notify_calendar_multiple)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(false)
                        .setOngoing(false)
                        .setStyle(notificationStyle)
                        .setNumber(numEvents)
                        .setShowWhen(false)
                        .setOnlyAlertOnce(!shouldPlayAndVibrate)

        val snoozeIntent = Intent(context, NotificationActionSnoozeService::class.java)
        snoozeIntent.putExtra(Consts.INTENT_SNOOZE_PRESET, Consts.DEFAULT_SNOOZE_TIME_IF_NONE)
        snoozeIntent.putExtra(Consts.INTENT_SNOOZE_ALL_COLLAPSED_KEY, true)

        val pendingSnoozeIntent = pendingServiceIntent(context, snoozeIntent, EVENT_CODE_DEFAULT_SNOOOZE0_OFFSET)
        builder.setDeleteIntent(pendingSnoozeIntent)

        val notification = builder.build()

        try {
            context.notificationManager.notify(Consts.NOTIFICATION_ID_COLLAPSED, notification) // would update if already exists
        }
        catch (ex: Exception) {
            DevLog.error(context, LOG_TAG, "Error posting notification: $ex, ${ex.stackTrace}")
        }
        val reminderState = ReminderState(context)

        if (shouldPlayAndVibrate) {
            context.persistentState.notificationLastFireTime = System.currentTimeMillis()
            reminderState.numRemindersFired = 0
        }

        if (isQuietPeriodActive && events.isNotEmpty() && !shouldPlayAndVibrate && !hasAlarms) {

            DevLog.debug(LOG_TAG, "Would remind after snooze period")

            if (!reminderState.quietHoursOneTimeReminderEnabled)
                reminderState.quietHoursOneTimeReminderEnabled = true
        }

        return postedNotification
    }


//    private fun collapseDisplayedNotifications(
//            context: Context, db: EventsStorage,
//            events: List<EventAlertRecord>, settings: Settings,
//            force: Boolean,
//            isQuietPeriodActive: Boolean) {
//
//        DevLog.debug(LOG_TAG, "Hiding notifications for ${events.size} notification")
//
//        if (events.isEmpty()) {
//            hideCollapsedEventsNotification(context)
//            return
//        }
//
//        for (event in events) {
//            if ((event.displayStatus != EventDisplayStatus.Hidden) || force) {
//                //DevLog.debug(LOG_TAG, "Hiding notification id ${event.notificationId}, eventId ${event.eventId}")
//                removeNotification(context, event.notificationId)
//            }
//            else {
//                //DevLog.debug(LOG_TAG, "Skipping collapsing notification id ${event.notificationId}, eventId ${event.eventId} - already collapsed")
//            }
//
//            if (event.snoozedUntil != 0L || event.displayStatus != EventDisplayStatus.DisplayedCollapsed) {
//                db.updateEvent(event,
//                        snoozedUntil = 0L,
//                        displayStatus = EventDisplayStatus.DisplayedCollapsed)
//            }
//        }
//
//        postNumNotificationsCollapsed(context, db, settings, events, isQuietPeriodActive)
//    }

    // force - if true - would re-post all active notifications. Normally only new notifications are posted to
    // avoid excessive blinking in the notifications area. Forced notifications are posted without sound or vibra
    @Suppress("UNUSED_PARAMETER")
    private fun postDisplayedEventNotifications(
            context: Context,
            db: EventsStorage,
            settings: Settings,
            formatter: EventFormatterInterface,
            events: List<EventAlertRecord>,
            force: Boolean,
            isQuietPeriodActive: Boolean,
            primaryEventId: Long?
    ): Boolean {

        DevLog.debug(context, LOG_TAG, "Posting ${events.size} notifications")

        val notificationsSettings = settings.notificationSettingsSnapshot

        val hasAlarms = events.any { it.isAlarm }

        var postedNotification = false
        var playedAnySound = false

        val snoozePresets = settings.snoozePresets

        postGroupNotification(
                context,
                events.size
        )

        var currentTime = System.currentTimeMillis()

        for (event in events) {

            currentTime++ // so last change times are not all the same

            if (event.snoozedUntil == 0L) {
                // snooze zero could mean
                // - this is a new event -- we have to display it, it would have displayStatus == hidden
                // - this is an old event returning from "collapsed" state
                // - this is currently potentially displayed event but we are doing "force re-post" to
                //   ensure all requests are displayed (like at boot or after app upgrade

                var wasCollapsed = false

                if ((event.displayStatus != EventDisplayStatus.DisplayedNormal) || force) {
                    // currently not displayed or forced -- post notifications
                    DevLog.info(context, LOG_TAG, "Posting notification id ${event.notificationId}, eventId ${event.eventId}")

                    var shouldBeQuiet = false

                    @Suppress("CascadeIf")
                    if (force) {
                        // If forced to re-post all notifications - we only have to actually display notifications
                        // so not playing sound / vibration here
                        DevLog.info(context, LOG_TAG, "event ${event.eventId}: 'forced' notification - staying quiet")
                        shouldBeQuiet = true
                    }
                    else if (event.displayStatus == EventDisplayStatus.DisplayedCollapsed) {
                        // This event was already visible as "collapsed", user just removed some other notification
                        // and so we automatically expanding some of the requests, this one was lucky.
                        // No sound / vibration should be played here
                        DevLog.info(context, LOG_TAG, "event ${event.eventId}: notification was collapsed, not playing sound")
                        shouldBeQuiet = true
                        wasCollapsed = true

                    }
                    else if (isQuietPeriodActive) {
                        // we are in a silent period, normally we should always be quiet, but there
                        // are a few exclusions
                        @Suppress("LiftReturnOrAssignment")
                        if (primaryEventId != null && event.eventId == primaryEventId) {
                            // this is primary event -- play based on use preference for muting
                            // primary event reminders
                            DevLog.info(context, LOG_TAG, "event ${event.eventId}: quiet period and this is primary notification - sound according to settings")
                            shouldBeQuiet = settings.quietHoursMutePrimary && !event.isAlarm
                        }
                        else {
                            // not a primary event -- always silent in silent period
                            DevLog.info(context, LOG_TAG, "event ${event.eventId}: quiet period and this is NOT primary notification quiet")
                            shouldBeQuiet = true
                        }
                    }

                    DevLog.debug(LOG_TAG, "event ${event.eventId}: shouldBeQuiet = $shouldBeQuiet, isMuted=${event.isMuted}")

                    shouldBeQuiet = shouldBeQuiet || event.isMuted

                    postNotification(
                            ctx = context,
                            formatter = formatter,
                            event = event,
                            notificationSettings = notificationsSettings,
                            isRepost = force,
                            snoozePresetsNotFiltered = snoozePresets,
                            shouldBeQuiet = isQuietPeriodActive || wasCollapsed
                    )

                    // Update db to indicate that this event is currently actively displayed
                    db.updateEvent(event, displayStatus = EventDisplayStatus.DisplayedNormal)

                    postedNotification = true
                    playedAnySound = playedAnySound || !shouldBeQuiet

                }
                else {
                    DevLog.info(context, LOG_TAG, "Not re-posting notification id ${event.notificationId}, eventId ${event.eventId} - already on the screen")
                }
            }
            else {
                // This event is currently snoozed and switching to "Shown" state

                DevLog.info(context, LOG_TAG, "Posting snoozed notification id ${event.notificationId}, eventId ${event.eventId}, isQuietPeriodActive=$isQuietPeriodActive")

                // Update this time before posting notification as this is now used as a sort-key
                event.lastStatusChangeTime = currentTime

                postNotification(
                        ctx = context,
                        formatter = formatter,
                        event = event,
                        notificationSettings = notificationsSettings,
                        isRepost = force,
                        snoozePresetsNotFiltered = snoozePresets,
                        shouldBeQuiet = isQuietPeriodActive || event.isMuted
                )

                if (event.snoozedUntil + Consts.ALARM_THRESHOLD < currentTime) {

                    val warningMessage = "snooze alarm is very late: expected at ${event.snoozedUntil}, " +
                            "received at $currentTime, late by ${currentTime - event.snoozedUntil} us"

                    DevLog.warn(context, LOG_TAG, warningMessage)

                    if (settings.debugAlarmDelays)
                        postNotificationsAlarmDelayDebugMessage(context,
                                "Snooze alarm was late!", "Late by ${(currentTime - event.snoozedUntil) / 1000L}s")

                }
                // Update Db to indicate that event is currently displayed and no longer snoozed
                // Since it is displayed now -- it is no longer snoozed, set snoozedUntil to zero
                // also update 'lastVisible' time since event just re-appeared
                db.updateEvent(event,
                        snoozedUntil = 0,
                        displayStatus = EventDisplayStatus.DisplayedNormal,
                        lastStatusChangeTime = currentTime)

                postedNotification = true
                playedAnySound = playedAnySound || !isQuietPeriodActive
            }
        }

        val reminderState = ReminderState(context)

        if (playedAnySound) {
            context.persistentState.notificationLastFireTime = System.currentTimeMillis()
            reminderState.numRemindersFired = 0
        }

        if (isQuietPeriodActive && events.isNotEmpty() && !playedAnySound) {

            DevLog.info(context, LOG_TAG, "Was quiet due to quiet hours - would remind after snooze period")

            if (!reminderState.quietHoursOneTimeReminderEnabled)
                reminderState.quietHoursOneTimeReminderEnabled = true
        }

        return postedNotification
    }

    private fun lastStatusChangeToSortingKey(lastStatusChangeTime: Long, eventId: Long): String {

        val sb = StringBuffer(20)

        var temp = eventId % (24 * 3)

        for (i in 0..3) {

            val chr = 24 - temp % 24
            temp /= 24

            sb.append(('A'.toInt() + chr).toChar())
        }

        temp = lastStatusChangeTime - 1500000000000L

        while (temp > 0) {

            val chr = 24 - temp % 24
            temp /= 24

            sb.append(('A'.toInt() + chr).toChar())
        }

        return sb.reverse().toString()
    }

    private fun postReminderNotification(
            ctx: Context,
            numActiveEvents: Int,
            lastStatusChange: Long,
            notificationSettings: NotificationSettingsSnapshot,
            itIsAfterQuietHoursReminder: Boolean,
            forceAlarmStream: Boolean
    ) {
        val notificationManager = ctx.notificationManager

        val intent = Intent(ctx, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(ctx, MAIN_ACTIVITY_REMINDER_CODE, intent, 0)

        val persistentState = ctx.persistentState

        val currentTime = System.currentTimeMillis()
        val msAgo: Long = (currentTime - persistentState.notificationLastFireTime)

        val resources = ctx.resources

        val title =
            when {
                itIsAfterQuietHoursReminder ->
                        resources.getString(R.string.reminder_after_quiet_time)
                numActiveEvents == 1 ->
                    resources.getString(R.string.reminder_you_have_missed_event)
                else ->
                    resources.getString(R.string.reminder_you_have_missed_events)
            }

        var text = ""

        if (!itIsAfterQuietHoursReminder) {

            val currentReminder = ReminderState(ctx).numRemindersFired + 1
            val totalReminders = Settings(ctx).maxNumberOfReminders

            val textTemplate = resources.getString(R.string.last_notification_s_ago)

            text = String.format(textTemplate,
                    EventFormatter(ctx).formatTimeDuration(msAgo),
                    currentReminder, totalReminders)
        }
        else {
            val textTemplate = resources.getString(R.string.last_event_s_ago)

            text = String.format(textTemplate,
                    EventFormatter(ctx).formatTimeDuration(msAgo))
        }

        val channel = NotificationChannelManager.createNotificationChannelForPurpose(
                ctx, isReminder = true,
                soundState = if (notificationSettings.useAlarmStream || forceAlarmStream)
                    NotificationChannelManager.SoundState.Alarm
                else NotificationChannelManager.SoundState.Normal)

        val builder = Notification.Builder(ctx, channel)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.stat_notify_calendar)
                .setContentIntent(
                        pendingIntent
                )
                .setAutoCancel(true)
                .setOngoing(false)
                .setStyle(
                        Notification.BigTextStyle().bigText(text)
                )
                .setWhen(
                        lastStatusChange
                )
                .setShowWhen(false)
                .setSortKey(
                        lastStatusChangeToSortingKey(lastStatusChange, 0)
                )
                .setCategory(
                        Notification.CATEGORY_REMINDER
                )
                .setOnlyAlertOnce(false)

        builder.setGroup(NOTIFICATION_GROUP)

        val notification = builder.build()

        try {
            DevLog.info(ctx, LOG_TAG, "adding reminder notification")

            notificationManager.notify(
                    Consts.NOTIFICATION_ID_REMINDER,
                    notification
            )
        }
        catch (ex: Exception) {
            DevLog.error(ctx, LOG_TAG, "Exception: ${ex.detailed}")
        }
    }

    @Suppress("unused")
    private fun isNotificationVisible(ctx: Context, event: EventAlertRecord): Boolean {

        val intent = snoozeIntent(ctx, event.eventId, event.instanceStartTime, event.notificationId)
        val id = event.notificationId * EVENT_CODES_TOTAL + EVENT_CODE_SNOOOZE_OFFSET
        val pendingIntent: PendingIntent? = PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_NO_CREATE)
        return pendingIntent != null
    }

    private fun postGroupNotification(
            ctx: Context,
            numTotalEvents: Int
    ) {
        val notificationManager = ctx.notificationManager

        val intent = Intent(ctx, MainActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(ctx, MAIN_ACTIVITY_GROUP_NOTIFICATION_CODE, intent, 0)

        val text = ctx.resources.getString(R.string.N_calendar_events).format(numTotalEvents)

        val channel = NotificationChannelManager.createNotificationChannelForPurpose(
                ctx,
                isReminder = false,
                soundState = NotificationChannelManager.SoundState.Silent)

        val groupBuilder = Notification.Builder(ctx, channel)
                .setContentTitle(ctx.resources.getString(R.string.calendar))
                .setContentText(text)
                .setSubText(text)
                .setGroupSummary(true)
                .setGroup(NOTIFICATION_GROUP)
                .setContentIntent(pendingIntent)
                .setCategory(
                        Notification.CATEGORY_EVENT
                )
                .setWhen(System.currentTimeMillis())
                .setShowWhen(false)
                .setNumber(numTotalEvents)
                .setOnlyAlertOnce(true)

        if (numTotalEvents > 1) {
            groupBuilder.setSmallIcon(R.drawable.stat_notify_calendar_multiple)
        }
        else {
            groupBuilder.setSmallIcon(R.drawable.stat_notify_calendar)
        }

        // swipe does snooze
        val snoozeIntent = Intent(ctx, NotificationActionSnoozeService::class.java)
        snoozeIntent.putExtra(Consts.INTENT_SNOOZE_PRESET, Consts.DEFAULT_SNOOZE_TIME_IF_NONE)
        snoozeIntent.putExtra(Consts.INTENT_SNOOZE_ALL_KEY, true)

        val pendingSnoozeIntent =
                pendingServiceIntent(ctx, snoozeIntent, EVENT_CODE_DEFAULT_SNOOOZE0_OFFSET)

        groupBuilder.setDeleteIntent(pendingSnoozeIntent)

        try {
            notificationManager.notify(
                    Consts.NOTIFICATION_ID_BUNDLED_GROUP,
                    groupBuilder.build()
            )
        }
        catch (ex: Exception) {
            DevLog.error(ctx, LOG_TAG, "Exception: ${ex.detailed}")
        }
    }

    private fun postNotification(
            ctx: Context,
            formatter: EventFormatterInterface,
            event: EventAlertRecord,
            notificationSettings: NotificationSettingsSnapshot,
            isRepost: Boolean, // == isRepost || wasCollapsed
            snoozePresetsNotFiltered: LongArray,
            shouldBeQuiet: Boolean
    ) {
        val notificationManager = ctx.notificationManager

        val calendarIntent = CalendarIntents.getCalendarViewIntent(event)

        val calendarPendingIntent =
                TaskStackBuilder.create(ctx)
                        .addNextIntentWithParentStack(calendarIntent)
                        .getPendingIntent(
                                event.notificationId * EVENT_CODES_TOTAL + EVENT_CODE_OPEN_OFFSET,
                                PendingIntent.FLAG_UPDATE_CURRENT)

        val snoozeActivityIntent =
                pendingActivityIntent(ctx,
                        snoozeIntent(ctx, event.eventId, event.instanceStartTime, event.notificationId),
                        event.notificationId * EVENT_CODES_TOTAL + EVENT_CODE_SNOOOZE_OFFSET
                )

        val dismissPendingIntent =
                pendingServiceIntent(ctx,
                        dismissOrDeleteIntent(ctx, event.eventId, event.instanceStartTime, event.notificationId),
                        event.notificationId * EVENT_CODES_TOTAL + EVENT_CODE_DISMISS_OFFSET
                )


        val currentTime = System.currentTimeMillis()

        val notificationTextString = formatter.formatNotificationSecondaryText(event)
        val title = event.title

        val sortKey = lastStatusChangeToSortingKey(event.lastStatusChangeTime, event.eventId)

        DevLog.info(ctx, LOG_TAG, "SortKey: ${event.eventId} -> ${event.lastStatusChangeTime} -> $sortKey")

        val primaryPendingIntent =
                if (notificationSettings.notificationOpensSnooze)
                    snoozeActivityIntent
                else
                    calendarPendingIntent

        var iconId = R.drawable.stat_notify_calendar
        if (event.isTask)
            iconId = R.drawable.ic_event_available_white_24dp
        else if (event.isMuted)
            iconId = R.drawable.stat_notify_calendar_muted

        var soundState = NotificationChannelManager.SoundState.Normal

        if (shouldBeQuiet)
            soundState = NotificationChannelManager.SoundState.Silent
        else if (notificationSettings.useAlarmStream || event.isAlarm)
            soundState = NotificationChannelManager.SoundState.Alarm

        val channel = NotificationChannelManager.createNotificationChannelForPurpose(
                ctx,
                soundState = soundState,
                isReminder = false
        )

        val builder = Notification.Builder(ctx, channel)
                .setContentTitle(title)
                .setContentText(notificationTextString)
                .setSmallIcon(iconId)
                .setContentIntent(primaryPendingIntent)
                .setAutoCancel(false)
                .setOngoing(false)
                .setStyle(Notification.BigTextStyle().bigText(notificationTextString))
                .setWhen(event.lastStatusChangeTime)
                .setShowWhen(false)
                .setSortKey(sortKey)
                .setCategory(Notification.CATEGORY_EVENT)
                .setOnlyAlertOnce(isRepost || (soundState == NotificationChannelManager.SoundState.Silent))

        builder.setGroup(NOTIFICATION_GROUP)

        var snoozePresets =
                snoozePresetsNotFiltered
                        .filter {
                            snoozeTimeInMillis ->
                            snoozeTimeInMillis >= 0 ||
                                    (event.instanceStartTime + snoozeTimeInMillis + Consts.ALARM_THRESHOLD) > currentTime
                        }
                        .toLongArray()

        if (snoozePresets.isEmpty())
            snoozePresets = longArrayOf(Consts.DEFAULT_SNOOZE_TIME_IF_NONE)

        val snoozeAction =
                Notification.Action.Builder(
                        Icon.createWithResource(ctx, R.drawable.ic_update_white_24dp),
                        ctx.getString(com.github.quarck.calnotifyng.R.string.snooze),
                        snoozeActivityIntent
                ).build()

        val dismissAction =
                Notification.Action.Builder(
                        Icon.createWithResource(ctx, R.drawable.ic_clear_white_24dp),
                        ctx.getString(if (event.isTask) R.string.done else R.string.dismiss),
                        dismissPendingIntent
                ).build()


        if (!notificationSettings.notificationOpensSnooze) {
            DevLog.debug(LOG_TAG, "adding pending intent for snooze, event id ${event.eventId}, notificationId ${event.notificationId}")
            builder.addAction(snoozeAction)
        }

        val extender = Notification.WearableExtender()

        if ((notificationSettings.enableNotificationMute && !event.isTask) || event.isMuted) {
            // build and append

            val muteTogglePendingIntent =
                    pendingServiceIntent(ctx,
                            defaultMuteToggleIntent(
                                    ctx,
                                    event.eventId,
                                    event.instanceStartTime,
                                    event.notificationId,
                                    if (event.isMuted) 1 else 0
                            ),
                            event.notificationId * EVENT_CODES_TOTAL + EVENT_CODE_MUTE_TOGGLE_OFFSET
                    )

            val actionBuilder =
                    if (event.isMuted) {
                        Notification.Action.Builder(
                                Icon.createWithResource(ctx, R.drawable.ic_volume_off_white_24dp),
                                ctx.getString(R.string.un_mute_notification),
                                muteTogglePendingIntent
                        )

                    }
                    else {
                        Notification.Action.Builder(
                                Icon.createWithResource(ctx, R.drawable.ic_volume_up_white_24dp),
                                ctx.getString(R.string.mute_notification),
                                muteTogglePendingIntent
                        )
                    }

            val action = actionBuilder.build()
            builder.addAction(action)
            extender.addAction(action)
        }

        // swipe does snooze
        val defaultSnooze0PendingIntent =
                pendingServiceIntent(ctx,
                        defaultSnoozeIntent(ctx,
                                event.eventId,
                                event.instanceStartTime,
                                event.notificationId,
                                Consts.DEFAULT_SNOOZE_TIME_IF_NONE),
                        event.notificationId * EVENT_CODES_TOTAL + EVENT_CODE_DEFAULT_SNOOOZE0_OFFSET
                )

        builder.setDeleteIntent(defaultSnooze0PendingIntent)
        builder.addAction(dismissAction)

        if (notificationSettings.appendEmptyAction) {
            builder.addAction(
                    Notification.Action.Builder(
                            Icon.createWithResource(ctx, R.drawable.ic_empty),
                            "",
                            primaryPendingIntent
                    ).build())
        }

        for ((idx, snoozePreset) in snoozePresets.withIndex()) {
            if (idx == 0)
                continue

            if (idx >= EVENT_CODE_DEFAULT_SNOOOZE_MAX_ITEMS)
                break

            if (snoozePreset <= 0L) {
                val targetTime = event.displayedStartTime - Math.abs(snoozePreset)
                if (targetTime - System.currentTimeMillis() < 5 * 60 * 1000L) // at least minutes left until target
                    continue
            }

            val snoozeIntent =
                    pendingServiceIntent(ctx,
                            defaultSnoozeIntent(ctx, event.eventId, event.instanceStartTime, event.notificationId, snoozePreset),
                            event.notificationId * EVENT_CODES_TOTAL + EVENT_CODE_DEFAULT_SNOOOZE0_OFFSET + idx
                    )

            val action =
                    Notification.Action.Builder(
                            Icon.createWithResource(ctx, R.drawable.ic_update_white_24dp),
                            ctx.getString(com.github.quarck.calnotifyng.R.string.snooze) + " " +
                                    PreferenceUtils.formatSnoozePreset(snoozePreset),
                            snoozeIntent
                    ).build()

            extender.addAction(action)
        }

        // In this combination of settings dismissing the notification would actually snooze it, so
        // add another "Dismiss Event" wearable action so to make it possible to actually dismiss
        // the event form wearable
        val dismissEventAction =
                Notification.Action.Builder(
                        Icon.createWithResource(ctx, R.drawable.ic_clear_white_24dp),
                        ctx.getString(com.github.quarck.calnotifyng.R.string.dismiss_event),
                        dismissPendingIntent
                ).build()

        extender.addAction(dismissEventAction)

        builder.extend(extender)

        builder.setColor(event.color.adjustCalendarColor(false))

        try {
            DevLog.info(ctx, LOG_TAG, "adding: notificationId=${event.notificationId}")

            notificationManager.notify(
                    event.notificationId,
                    builder.build()
            )
        }
        catch (ex: Exception) {
            DevLog.error(ctx, LOG_TAG, "Exception on notificationId=${event.notificationId}: ${ex.detailed}")
        }
    }

    private fun snoozeIntent(ctx: Context, eventId: Long, instanceStartTime: Long, notificationId: Int): Intent {

        val intent = Intent(ctx, SnoozeActivityNoRecents::class.java)
        intent.putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, notificationId)
        intent.putExtra(Consts.INTENT_EVENT_ID_KEY, eventId)
        intent.putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, instanceStartTime)

        intent.action = Intent.ACTION_MAIN
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        return intent
    }

    private fun dismissOrDeleteIntent(ctx: Context, eventId: Long, instanceStartTime: Long, notificationId: Int): Intent {

        val intent = Intent(ctx, NotificationActionDismissService::class.java)
        intent.putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, notificationId)
        intent.putExtra(Consts.INTENT_EVENT_ID_KEY, eventId)
        intent.putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, instanceStartTime)
        return intent
    }

    private fun defaultSnoozeIntent(ctx: Context, eventId: Long, instanceStartTime: Long, notificationId: Int, snoozePreset: Long): Intent {

        val intent = Intent(ctx, NotificationActionSnoozeService::class.java)
        intent.putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, notificationId)
        intent.putExtra(Consts.INTENT_EVENT_ID_KEY, eventId)
        intent.putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, instanceStartTime)
        intent.putExtra(Consts.INTENT_SNOOZE_PRESET, snoozePreset)
        return intent
    }

    private fun defaultMuteToggleIntent(ctx: Context, eventId: Long, instanceStartTime: Long, notificationId: Int, muteAction: Int): Intent {
        val intent = Intent(ctx, NotificationActionMuteToggleService::class.java)
        intent.putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, notificationId)
        intent.putExtra(Consts.INTENT_EVENT_ID_KEY, eventId)
        intent.putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, instanceStartTime)
        intent.putExtra(Consts.INTENT_MUTE_ACTION, muteAction)
        return intent
    }

    private fun pendingServiceIntent(ctx: Context, intent: Intent, id: Int): PendingIntent
            = PendingIntent.getService(ctx, id, intent, PendingIntent.FLAG_CANCEL_CURRENT)

    private fun pendingActivityIntent(ctx: Context, intent: Intent, id: Int): PendingIntent {

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        return PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_UPDATE_CURRENT)

    }

    private fun removeNotification(ctx: Context, notificationId: Int) {
        val notificationManager = ctx.notificationManager
        notificationManager.cancel(notificationId)
    }

    private fun removeNotifications(context: Context, events: Collection<EventAlertRecord>) {
        val notificationManager = context.notificationManager

        DevLog.info(context, LOG_TAG, "Removing 'full' notifications for  ${events.size} events")

        for (event in events)
            notificationManager.cancel(event.notificationId)
    }

    private fun removeVisibleNotifications(ctx: Context, events: Collection<EventAlertRecord>) {
        val notificationManager = ctx.notificationManager

        events.filter { it.displayStatus != EventDisplayStatus.Hidden }
                .forEach { notificationManager.cancel(it.notificationId) }
    }

//    private fun postNumNotificationsCollapsed(
//            context: Context,
//            db: EventsStorage,
//            settings: Settings,
//            events: List<EventAlertRecord>,
//            isQuietPeriodActive: Boolean
//    ) {
//        DevLog.debug(LOG_TAG, "Posting collapsed view notification for ${events.size} requests")
//
//        val intent = Intent(context, MainActivity::class.java)
//        val pendingIntent = PendingIntent.getActivity(context, MAIN_ACTIVITY_NUM_NOTIFICATIONS_COLLAPSED_CODE, intent, 0)
//
//        val numEvents = events.size
//
//        val title = java.lang.String.format(context.getString(R.string.multiple_events), numEvents)
//
//        val text = context.getString(com.github.quarck.calnotifyng.R.string.multiple_events_details)
//
//        val bigText =
//                events
//                        .sortedByDescending { it.instanceStartTime }
//                        .take(30)
//                        .fold(
//                                StringBuilder(), {
//                            sb, ev ->
//
//                            val flags =
//                                    if (DateUtils.isToday(ev.displayedStartTime))
//                                        DateUtils.FORMAT_SHOW_TIME
//                                    else
//                                        DateUtils.FORMAT_SHOW_DATE
//
//                            sb.append("${DateUtils.formatDateTime(context, ev.displayedStartTime, flags)}: ${ev.title}\n")
//                        })
//                        .toString()
//
//        val channel = NotificationChannelManager.createNotificationChannelForPurpose(
//                context,
//                isReminder = false,
//                soundState = NotificationChannelManager.SoundState.Silent
//        )
//
//        val builder =
//                Notification.Builder(context, channel)
//                        .setContentTitle(title)
//                        .setContentText(text)
//                        .setSmallIcon(com.github.quarck.calnotifyng.R.drawable.stat_notify_calendar)
//                        .setContentIntent(pendingIntent)
//                        .setAutoCancel(false)
//                        .setOngoing(true)
//                        .setStyle(Notification.BigTextStyle().bigText(bigText))
//                        .setShowWhen(false)
//                        .setOnlyAlertOnce(true)
//
//
//        builder.setGroup(NOTIFICATION_GROUP)
//
//        val notification = builder.build()
//
//        context.notificationManager.notify(Consts.NOTIFICATION_ID_COLLAPSED, notification) // would update if already exists
//    }

    private fun hideCollapsedEventsNotification(context: Context) {
        DevLog.debug(LOG_TAG, "Hiding collapsed view notification")
        context.notificationManager.cancel(Consts.NOTIFICATION_ID_COLLAPSED)
    }

    private fun postDebugNotification(context: Context, notificationId: Int, title: String, text: String) {

        val notificationManager = context.notificationManager

        val appPendingIntent = pendingActivityIntent(context,
                Intent(context, MainActivity::class.java), notificationId)

        val builder = Notification.Builder(context, NotificationChannelManager.createDefaultNotificationChannelDebug(context))
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.stat_notify_calendar)
                .setContentIntent(appPendingIntent)
                .setAutoCancel(false)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_ERROR)
                .setOnlyAlertOnce(true)

        builder.setGroup(NOTIFICATION_GROUP)

        val notification = builder.build()

        try {
            notificationManager.notify(notificationId, notification)
        }
        catch (ex: Exception) {
            DevLog.error(context, LOG_TAG, "Exception: ${ex.detailed}")
        }
    }

    override fun postNotificationsAutoDismissedDebugMessage(context: Context) {

        postDebugNotification(
                context,
                Consts.NOTIFICATION_ID_DEBUG0_AUTO_DISMISS,
                "DEBUG: Events dismissed",
                "DEBUG: Some requests were auto-dismissed due to calendar move"
        )
    }

    override fun postNearlyMissedNotificationDebugMessage(context: Context) {

        postDebugNotification(
                context,
                Consts.NOTIFICATION_ID_DEBUG3_NEARLY_MISS,
                "DEBUG: Nearly missed event",
                "DEBUG: Some events has fired later than expeted"
        )
    }

    override fun postNotificationsAlarmDelayDebugMessage(context: Context, title: String, text: String) {

        postDebugNotification(
                context,
                Consts.NOTIFICATION_ID_DEBUG1_ALARM_DELAYS,
                title,
                text
        )
    }

    override fun postNotificationsSnoozeAlarmDelayDebugMessage(context: Context, title: String, text: String) {

        postDebugNotification(
                context,
                Consts.NOTIFICATION_ID_DEBUG2_SNOOZE_ALARM_DELAYS,
                title,
                text
        )

    }

    companion object {
        private const val LOG_TAG = "EventNotificationManager"

        private const val NOTIFICATION_GROUP = "GROUP_1"

        const val EVENT_CODE_SNOOOZE_OFFSET = 0
        const val EVENT_CODE_DISMISS_OFFSET = 1
        @Suppress("unused")
        const val EVENT_CODE_DELETE_OFFSET = 2
        const val EVENT_CODE_OPEN_OFFSET = 3
        const val EVENT_CODE_DEFAULT_SNOOOZE0_OFFSET = 4
        const val EVENT_CODE_MUTE_TOGGLE_OFFSET = 5
        const val EVENT_CODE_DEFAULT_SNOOOZE_MAX_ITEMS = 10
        const val EVENT_CODES_TOTAL = 16

        const val MAIN_ACTIVITY_EVERYTHING_COLLAPSED_CODE = 0
        const val MAIN_ACTIVITY_NUM_NOTIFICATIONS_COLLAPSED_CODE = 1
        const val MAIN_ACTIVITY_REMINDER_CODE = 2
        const val MAIN_ACTIVITY_GROUP_NOTIFICATION_CODE = 3
    }
}
