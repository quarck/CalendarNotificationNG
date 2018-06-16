//
//   Calendar Notifications Plus
//   Copyright (C) 2018 Sergey Parshin (s.parshin.sc@gmail.com)
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

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.preference.PreferenceActivity
import android.preference.PreferenceFragment
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Switch
import com.github.quarck.calnotifyng.R
import com.github.quarck.calnotifyng.Settings
import com.github.quarck.calnotifyng.utils.findOrThrow
import android.content.DialogInterface
import android.view.LayoutInflater
import android.widget.EditText
import com.github.quarck.calnotifyng.Consts
import com.github.quarck.calnotifyng.prefs.PreferenceUtils


class SnoozeSettingsActivity : AppCompatActivity() {

    lateinit var switchViewAfterEdit: Switch
    lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        //addPreferencesFromResource(R.xml.notification_preferences)
        setContentView(R.layout.activity_pref_snooze)
        settings = Settings(this)

        switchViewAfterEdit = findOrThrow(R.id.snooze_pref_switch_view_after_edit)
        switchViewAfterEdit.isChecked = settings.viewAfterEdit
    }

    private fun onDialogSnoozePresetsClosed(positiveResult: Boolean, newText: String) {
        // When the user selects "OK", persist the new value
        if (positiveResult) {

            val presets = PreferenceUtils.parseSnoozePresets(newText)
            if (presets != null) {
                if (presets.size == 0) {
                    settings.snoozePresetsRaw = Settings.DEFAULT_SNOOZE_PRESET
                }
                else {
                    settings.snoozePresetsRaw =
                            newText.split(',')
                                    .map { it.trim() }
                                    .filter { !it.isEmpty() }
                                    .joinToString(", ")
                }

                if (presets.size > Consts.MAX_SUPPORTED_PRESETS) {
                    showMessage(R.string.error_too_many_presets)
                }
            }
            else {
                showMessage(R.string.error_cannot_parse_preset)
            }
        }
    }

    private fun showMessage(id: Int) {
        val builder = AlertDialog.Builder(this)
        builder
                .setMessage(this.getString(id))
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok) { _, _ -> }

        builder.create().show()
    }

    private fun onCreateSnoozePresetsDialog(): Dialog {
        val builder = AlertDialog.Builder(this)
        // Get the layout inflater
        val inflater = this.layoutInflater

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        val rootView: View = inflater.inflate(R.layout.dialog_snooze_presets, null)

        val editText = rootView.findOrThrow<EditText>(R.id.edit_text_snooze_presets)
        editText.setText(settings.snoozePresetsRaw)

        builder.setView(rootView)
                // Add action buttons
                .setPositiveButton(R.string.ok, DialogInterface.OnClickListener {
                    _, _ ->
                    onDialogSnoozePresetsClosed(true, editText.text.toString())
                    // sign in the user ...
                })
                .setNegativeButton(R.string.cancel, DialogInterface.OnClickListener {
                    _, _ ->
                    onDialogSnoozePresetsClosed(false, "")
                    //this@LoginDialogFragment.getDialog().cancel()
                })
        return builder.create()
    }

    fun onSnoozePresets(v: View?) {
        onCreateSnoozePresetsDialog().show()
    }

    fun onViewAfterEdit(v: View?) {
        settings.viewAfterEdit = switchViewAfterEdit.isChecked
    }
}

