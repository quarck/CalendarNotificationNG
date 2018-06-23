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
import android.preference.PreferenceActivity
import android.preference.PreferenceFragment
import android.support.v7.app.AppCompatActivity
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import com.github.quarck.calnotifyng.R
import com.github.quarck.calnotifyng.Settings
import com.github.quarck.calnotifyng.prefs.ButtonPreferenceTwoLine
import com.github.quarck.calnotifyng.prefs.PrefsRoot
import com.github.quarck.calnotifyng.prefs.SwitchPreferenceWithLayout
import com.github.quarck.calnotifyng.prefs.TimeOfDayPreference
import com.github.quarck.calnotifyng.utils.findOrThrow
import java.text.DateFormat
import java.util.*

class QuietHoursSettingsActivity : AppCompatActivity() {

    lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_pref_quiet_hours)

        settings = Settings(this)

        val root = findOrThrow<LinearLayout>(R.id.preference_quiet_hours_root)

//        val sampleSwitch = findOrThrow<Switch>(R.id.preference_quiet_hours_switch_enable)
//
//        val newChild = layoutInflater.inflate(R.layout.pref_switch_with_text, null)
//        newChild.findOrThrow<Switch>(R.id.pref_switch_generic).text = "Hello"
//        newChild.findOrThrow<TextView>(R.id.pref_switch_generic_small_text).text = "Hello World"
//        root.addView(newChild)
//
//        val newChild2 = layoutInflater.inflate(R.layout.pref_switch_with_text, null)
//        newChild2.findOrThrow<Switch>(R.id.pref_switch_generic).text = "Hello2 "
//        newChild2.findOrThrow<TextView>(R.id.pref_switch_generic_small_text).text = "Hello World 2"
//        root.addView(newChild2)

        PrefsRoot(layoutInflater, root) {
            switch("Hello", "world") {
                initial(true)
                onChange {  }
            }

            header("Something else below")

            item("hahaha - item", "with sub") {

            }

            item("hahaha - item") {

            }

            switch("Goodbye", "Universe") {
                initial(false)
                onChange {  }

                depending {
                    switch("Another", "One") {
                        initial(true)
                    }

                    header("ha-ha")

                    switch ("And yet") {
                        initial(false)
                    }
                }
            }
        }


        SwitchPreferenceWithLayout(
                this,
                R.id.preference_quiet_hours_switch_enable,
                settings.quietHoursEnabled,
                { settings.quietHoursEnabled = it },
                { findOrThrow<LinearLayout>(R.id.preference_quiet_hours_layout_times).visibility =
                        if (it) View.VISIBLE else View.GONE }
        )

        ButtonPreferenceTwoLine(
                this,
                R.id.preference_quiet_hours_from,
                R.id.preference_quiet_hours_from_value,
                {
                    TimeOfDayPreference(
                            this,
                            this.layoutInflater,
                            settings.quietHoursFrom,
                            { settings.quietHoursFrom = it; updateViews() }
                    ).create().show()
                }
        )

        ButtonPreferenceTwoLine(
                this,
                R.id.preference_quiet_hours_to,
                R.id.preference_quiet_hours_to_value,
                {
                    TimeOfDayPreference(
                            this,
                            this.layoutInflater,
                            settings.quietHoursTo,
                            { settings.quietHoursTo = it; updateViews() }
                    ).create().show()
                }
        )

        updateViews()
    }

    private fun updateViews() {
        findOrThrow<TextView>(R.id.preference_quiet_hours_from_value).text = formatTime(settings.quietHoursFrom)
        findOrThrow<TextView>(R.id.preference_quiet_hours_to_value).text = formatTime(settings.quietHoursTo)
    }

    @Suppress("DEPRECATION")
    private fun formatTime(time: Pair<Int, Int>): String {
        val timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT)
        val date = Date(0, 0, 0, time.component1(), time.component2())
        return timeFormatter.format(date)
    }
}
