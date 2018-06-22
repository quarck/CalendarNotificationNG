package com.github.quarck.calnotifyng.prefs

import android.content.Context
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import com.github.quarck.calnotifyng.R
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


class PrefsSwitch(
        val inflater: LayoutInflater,
        val root: LinearLayout,
        textMain: String,
        textSecondary: String
) {
    val switch: Switch
    val text: TextView
    var onChangeFn: ((Boolean) -> Unit)? = null
    var dependingLayout: LinearLayout? = null

    init {
        val child = inflater.inflate(R.layout.pref_switch_with_text, null)
        switch = child.findOrThrow<Switch>(R.id.pref_switch_generic)
        text = child.findOrThrow<TextView>(R.id.pref_switch_generic_small_text)

        switch.text = textMain

        if (textSecondary.isNotEmpty())
            text.text = textSecondary
        else
            text.visibility = View.GONE

        switch.setOnClickListener({
            val value = switch.isChecked
            val fn = onChangeFn
            if (fn != null)
                fn(value)
            dependingLayout?.visibility = if (value) View.VISIBLE else View.GONE
        })

        root.addView(child)
    }

    fun initial(value: Boolean) {
        switch.isChecked = value
        dependingLayout?.visibility = if (value) View.VISIBLE else View.GONE
    }

    fun onChange(fn: (Boolean) -> Unit) {
        onChangeFn = fn
    }

    fun depending(fn: PrefsRoot.() -> Unit): PrefsRoot {
        val depLayout = inflater.inflate(R.layout.pref_empty_linear_layout, null)
        if (depLayout is LinearLayout) {
            dependingLayout = depLayout
            depLayout.visibility = if (switch.isChecked) View.VISIBLE else View.GONE
            root.addView(depLayout)
        }
        else {
            throw Exception("Internal error")
        }

        return PrefsRoot(inflater, depLayout, fn)
    }
}

class PrefsHeader(val inflater: LayoutInflater, val root: LinearLayout, text: String) {
    init {
        val child = inflater.inflate(R.layout.pref_header, null)
        child.findOrThrow<TextView>(R.id.pref_header_generic_text).text = text
        root.addView(child)
    }
}

class PrefsRoot(val inflater: LayoutInflater, val root: LinearLayout, val fn: PrefsRoot.() -> Unit) {

    init {
        this.fn()
    }

    fun switch(textMain: String, textSecondary: String, fn: PrefsSwitch.() -> Unit): PrefsSwitch  {
        val obj = PrefsSwitch(inflater, root, textMain, textSecondary)
        obj.fn()
        return obj
    }

    fun switch(textMain: String, fn: PrefsSwitch.() -> Unit): PrefsSwitch  {
        val obj = PrefsSwitch(inflater, root, textMain, "")
        obj.fn()
        return obj
    }

    fun header(text: String): PrefsHeader {
        return PrefsHeader(inflater, root, text)
    }
}
