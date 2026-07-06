package com.github.fschroffner.radiodroid3.views

import android.app.Activity
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.github.fschroffner.radiodroid3.R

object ItemListDialog {
    fun interface Callback { fun onItemSelected(resourceId: Int) }

    @JvmStatic
    fun create(activity: Activity, resourceIds: IntArray, callback: Callback): BottomSheetDialog {
        val dialog = BottomSheetDialog(activity)
        val inflater = activity.layoutInflater
        val sheetView = inflater.inflate(R.layout.dialog_generic_item_list, null)
        val viewItemsList = sheetView.findViewById<android.view.ViewGroup>(R.id.layout_items_list)

        for (resourceId in resourceIds) {
            val textView = inflater.inflate(R.layout.dialog_generic_item, null).findViewById<TextView>(R.id.text)
            textView.setText(resourceId)
            textView.isClickable = true
            textView.setOnClickListener {
                callback.onItemSelected(resourceId)
                dialog.hide()
            }
            viewItemsList.addView(textView)
        }

        dialog.setContentView(sheetView)
        return dialog
    }
}
