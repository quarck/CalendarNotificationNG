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
import com.github.quarck.calnotifyng.R
import com.github.quarck.calnotifyng.Settings
import com.github.quarck.calnotifyng.notification.NotificationChannelManager
import com.github.quarck.calnotifyng.prefs.*
import com.github.quarck.calnotifyng.utils.findOrThrow

class NotificationSettingsActivity : AppCompatActivity(){

    lateinit var settings: Settings

    lateinit var remindersLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_pref_notification)
        settings = Settings(this)

        remindersLayout = findOrThrow(R.id.notification_pref_reminder_layout)

        ButtonPreference(this, R.id.notification_pref_main_channel_pref) {
            NotificationChannelManager.launchSystemSettingForChannel(this,
                    NotificationChannelManager.SoundState.Normal,
                    false)
        }

        ButtonPreference(this, R.id.notification_pref_silent_channel_pref) {
            NotificationChannelManager.launchSystemSettingForChannel(this,
                    NotificationChannelManager.SoundState.Silent,
                    false)
        }
        ButtonPreference(this, R.id.notification_pref_alarm_channel_pref) {
            NotificationChannelManager.launchSystemSettingForChannel(this,
                    NotificationChannelManager.SoundState.Alarm,
                    false)
        }
        ButtonPreference(this, R.id.notification_pref_reminder_main_channel) {
            NotificationChannelManager.launchSystemSettingForChannel(this,
                    NotificationChannelManager.SoundState.Normal,
                    true)
        }
        ButtonPreference(this, R.id.notification_pref_reminder_alarm_channel) {
            NotificationChannelManager.launchSystemSettingForChannel(this,
                    NotificationChannelManager.SoundState.Alarm,
                    true)
        }

        SwitchPreference(this, R.id.notification_pref_switch_empty_action, settings.notificationAddEmptyAction) {
            settings.notificationAddEmptyAction = it
        }

        SwitchPreferenceWithLayout(
                parent = this,
                id = R.id.notification_pref_enable_reminders,
                initialValue = settings.remindersEnabled,
                onChange = { settings.remindersEnabled = it},
                updateLayout = { remindersLayout.visibility = if (it) View.VISIBLE else View.GONE}
        )

        ButtonPreference(this, R.id.notification_pref_reminder_interval) {
            ReminderPatternPreference(this, settings, this.layoutInflater).create().show()
        }

        ButtonPreferenceTwoLine(this,
                R.id.notification_pref_reminder_max_reminders,
                R.id.notification_pref_reminder_max_reminders_summary) {
            MaxRemindersPreference(this, settings, this.layoutInflater).create().show()
        }
    }
}
