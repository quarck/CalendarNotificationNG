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

package com.github.quarck.calnotifyng.prefs.activities

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import com.github.quarck.calnotifyng.R
import com.github.quarck.calnotifyng.Settings
import com.github.quarck.calnotifyng.notification.NotificationChannelManager
import com.github.quarck.calnotifyng.prefs.MaxRemindersPreference
import com.github.quarck.calnotifyng.prefs.ReminderPatternPreference
import com.github.quarck.calnotifyng.utils.findOrThrow

class NotificationSettingsActivity : AppCompatActivity(){

    lateinit var settings: Settings

    lateinit var enableReminders: Switch
    lateinit var reminderMainChannel: TextView
    lateinit var reminderAlarmChannel: TextView
    lateinit var reminderInterval: TextView
    lateinit var reminderMaxReminders: TextView
    lateinit var switchAppendEmpty: Switch
    lateinit var remindersLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        //addPreferencesFromResource(R.xml.notification_preferences)
        setContentView(R.layout.activity_pref_notification)
        settings = Settings(this)

        enableReminders = findOrThrow(R.id.notification_pref_enable_reminders)

        reminderMainChannel = findOrThrow(R.id.notification_pref_reminder_main_channel)
        reminderAlarmChannel = findOrThrow(R.id.notification_pref_reminder_alarm_channel)
        reminderInterval = findOrThrow(R.id.notification_pref_reminder_interval)
        reminderMaxReminders = findOrThrow(R.id.notification_pref_reminder_max_reminders)


        switchAppendEmpty = findOrThrow(R.id.notification_pref_switch_empty_action)

        remindersLayout = findOrThrow(R.id.notification_pref_reminder_layout)

        //switchAlarmVol.isChecked = settings.notificationUseAlarmStream
        updateControls()
    }

    fun updateControls() {
        switchAppendEmpty.isChecked = settings.notificationAddEmptyAction

        val remindersEnabled = settings.remindersEnabled
        enableReminders.isChecked = remindersEnabled
        remindersLayout.visibility = if (remindersEnabled) View.VISIBLE else View.GONE
    }

    fun onChannelSettings(v: View?) {
        NotificationChannelManager.launchSystemSettingForChannel(this,
                NotificationChannelManager.SoundState.Normal,
                false)
    }

    fun onSilentChannelSettings(v: View?) {
        NotificationChannelManager.launchSystemSettingForChannel(this,
                NotificationChannelManager.SoundState.Silent,
                false)
    }

    fun onAlarmChannelSettings(v: View?) {
        NotificationChannelManager.launchSystemSettingForChannel(this,
                NotificationChannelManager.SoundState.Alarm,
                false)
    }

    fun onReminderChannelSettings(v: View?) {
        NotificationChannelManager.launchSystemSettingForChannel(this,
                NotificationChannelManager.SoundState.Normal,
                true)
    }

    fun onReminderAlarmChannelSettings(v: View?) {
        NotificationChannelManager.launchSystemSettingForChannel(this,
                NotificationChannelManager.SoundState.Alarm,
                true)
    }

    fun onAppendEmptyActionSettings(v: View?) {
        settings.notificationAddEmptyAction = switchAppendEmpty.isChecked
        updateControls()
    }

    fun onEnableRemindersClick(v: View?) {
        settings.remindersEnabled = enableReminders.isChecked
        updateControls()
    }

    fun onReminderInterval(v: View?) {
        ReminderPatternPreference(this, settings, this.layoutInflater).create().show()
    }

    fun onMaxReminders(v: View?) {
        MaxRemindersPreference(this, settings, this.layoutInflater).create().show()
    }
}
