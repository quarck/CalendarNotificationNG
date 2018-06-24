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

package com.github.quarck.calnotifyng.prefs

import android.app.AlertDialog
import android.content.Context
import android.widget.ArrayAdapter
import com.github.quarck.calnotifyng.R

class ListPreference(
        val context: Context,
        val titleId: Int,
        val namesId: Int,
        val valuesId: Int,
        val onNewValue: (pos: Int) -> Unit) {

    fun create() {

        val builder = AlertDialog.Builder(context)
        builder.setIcon(R.drawable.ic_launcher)
        builder.setTitle(context.resources.getString(titleId))

        val namesArray: Array<String> = context.resources.getStringArray(namesId)
        val valuesArray: IntArray = context.resources.getIntArray(valuesId)

        val arrayAdapter = ArrayAdapter<String>(context, android.R.layout.select_dialog_singlechoice, namesArray)

        builder.setNegativeButton(R.string.cancel) {
            dialog, which -> dialog.dismiss()
        }

        builder.setAdapter(arrayAdapter) {
            dialog, which ->
            onNewValue(valuesArray[which])
            //val strName = arrayAdapter.getItem(which)
        }

        return builder.create().show()
    }
}
