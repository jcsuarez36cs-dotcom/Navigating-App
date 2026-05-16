package com.navigating.app.ui

import android.app.Dialog
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.navigating.app.data.Feature

class EditFeatureDialog : DialogFragment() {
    private var feature: Feature? = null
    private var onSave: ((Feature) -> Unit)? = null

    companion object {
        fun newInstance(feature: Feature, onSave: (Feature) -> Unit): EditFeatureDialog {
            return EditFeatureDialog().apply {
                this.feature = feature
                this.onSave = onSave
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val f = feature ?: return super.onCreateDialog(savedInstanceState)
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }
        val etName = EditText(ctx).apply {
            hint = "Feature Name"
            setText(f.name)
        }
        layout.addView(TextView(ctx).apply { text = "Name:" })
        layout.addView(etName)
        val attrViews = mutableListOf<Pair<String, EditText>>()
        layout.addView(TextView(ctx).apply { text = "Attributes:" })
        f.attributes.forEach { (k, v) ->
            val et = EditText(ctx).apply { setText(v) }
            layout.addView(TextView(ctx).apply { text = k })
            layout.addView(et)
            attrViews.add(k to et)
        }
        val scroll = ScrollView(ctx).apply { addView(layout) }
        return AlertDialog.Builder(ctx)
            .setTitle("Edit Feature")
            .setView(scroll)
            .setPositiveButton("Save") { _, _ ->
                val newAttrs = attrViews.associate { (k, et) -> k to et.text.toString() }
                onSave?.invoke(f.copy(name = etName.text.toString(), attributes = newAttrs))
            }
            .setNegativeButton("Cancel", null)
            .create()
    }
}
