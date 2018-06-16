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

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceActivity
import android.preference.PreferenceFragment
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Switch
import com.github.quarck.calnotifyng.R
import com.github.quarck.calnotifyng.Settings
import com.github.quarck.calnotifyng.notification.NotificationChannelManager
import com.github.quarck.calnotifyng.utils.findOrThrow

class NotificationSettingsActivity : AppCompatActivity(){

    lateinit var settings: Settings
    lateinit var switchAlarmVol: Switch
    lateinit var switchAppendEmpty: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        //addPreferencesFromResource(R.xml.notification_preferences)
        setContentView(R.layout.activity_pref_notification)
        settings = Settings(this)

        switchAlarmVol = findOrThrow(R.id.notification_pref_switch_alarm_volume)
        switchAppendEmpty = findOrThrow(R.id.notification_pref_switch_empty_action)

        switchAlarmVol.isChecked = settings.notificationUseAlarmStream
        switchAppendEmpty.isChecked = settings.notificationAddEmptyAction
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

    fun onAlarmVolumeSettings(v: View?) {
        settings.notificationUseAlarmStream = switchAlarmVol.isChecked
    }

    fun onAppendEmptyActionSettings(v: View?) {
        settings.notificationAddEmptyAction = switchAppendEmpty.isChecked
    }
}
