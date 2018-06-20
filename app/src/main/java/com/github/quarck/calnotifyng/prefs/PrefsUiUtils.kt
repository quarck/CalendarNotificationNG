package com.github.quarck.calnotifyng.prefs

import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Switch
import android.widget.TextView
import com.github.quarck.calnotifyng.utils.findOrThrow

class ButtonPreference(parent: AppCompatActivity, id: Int, f: () -> Unit) {
    init {
        parent.findOrThrow<View>(id).setOnClickListener({ f() })
    }
}

class ButtonPreferenceTwoLine(parent: AppCompatActivity, id1: Int, id2: Int, f: () -> Unit) {
    init {
        parent.findOrThrow<View>(id1).setOnClickListener({ f() })
        parent.findOrThrow<View>(id2).setOnClickListener({ f() })
    }
}

class ButtonPreferenceMultipleIDs(parent: AppCompatActivity, ids: IntArray, f: () -> Unit) {
    init {
        for (id in ids) {
            parent.findOrThrow<View>(id).setOnClickListener({ f() })
        }
    }
}

class SwitchPreference(
        parent: AppCompatActivity,
        id: Int,
        initialValue: Boolean,
        onChange: (Boolean) -> Unit
) {
    val switch: Switch = parent.findOrThrow(id)
    init {
        switch.isChecked = initialValue

        switch.setOnClickListener({
            val value = switch.isChecked
            onChange(value)
        })
    }
}

class SwitchPreferenceWithLayout(
        parent: AppCompatActivity,
        id: Int,
        initialValue: Boolean,
        onChange: (Boolean) -> Unit,
        updateLayout: (Boolean) -> Unit
) {
    val switch: Switch = parent.findOrThrow(id)
    init {
        switch.isChecked = initialValue

        updateLayout(initialValue)

        switch.setOnClickListener({
            val value = switch.isChecked
            onChange(value)
            updateLayout(value)
        })
    }
}