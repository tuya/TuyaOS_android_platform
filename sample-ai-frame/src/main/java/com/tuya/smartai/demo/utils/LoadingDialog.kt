package com.tuya.smartai.demo.utils

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.widget.TextView
import com.tuya.smartai.demo.R

class LoadingDialog(private val activity: Activity) {

    private var dialog: AlertDialog? = null

    fun show(message: String?) {
        dialog?.let {
            if (it.isShowing) {
                it.dismiss()
                return
            }
        }

        val view = activity.layoutInflater.inflate(R.layout.dialog_loading, null)
        view.findViewById<TextView>(R.id.tv_loading_text)?.let { tv ->
            message?.let { tv.text = it }
        }

        dialog = AlertDialog.Builder(activity)
            .setView(view)
            .setCancelable(false)
            .create()
            .also {
                it.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                it.show()
            }
    }

    fun dismiss() {
        dialog?.takeIf { it.isShowing }?.dismiss()
        dialog = null
    }
}
