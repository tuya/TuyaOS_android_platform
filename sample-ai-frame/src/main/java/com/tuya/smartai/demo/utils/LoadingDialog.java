package com.tuya.smartai.demo.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.tuya.smartai.demo.R;

public class LoadingDialog {

    private Activity activity;
    private AlertDialog dialog;

    public LoadingDialog(Activity myActivity) {
        activity = myActivity;
    }


    public void show(String message) {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        LayoutInflater inflater = activity.getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_loading, null);

        TextView tvMessage = view.findViewById(R.id.tv_loading_text);
        if (message != null) {
            tvMessage.setText(message);
        }

        builder.setView(view);
        builder.setCancelable(false);

        dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        dialog.show();
    }


    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
            dialog = null; // 释放引用，防止内存泄漏
        }
    }
}