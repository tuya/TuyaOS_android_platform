package com.tuya.smartai.sample.utils;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.HashMap;
import java.util.Map;

public class QrCodeUtil {

    /**
     * 根据内容生成二维码 Bitmap
     */
    public static Bitmap generateQrCodeBitmap(String content, int width, int height) {
        if (content == null || content.isEmpty()) {
            return null;
        }

        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);

        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height, hints);

            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (bitMatrix.get(x, y)) {
                        pixels[y * width + x] = Color.BLACK;
                    } else {
                        pixels[y * width + x] = Color.WHITE;
                    }
                }
            }

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return bitmap;

        } catch (WriterException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 从 JSON 字符串中提取 shortUrl
     * {"shortUrl":"https://t.tuya.com/1D0xBh6D"} -> https://t.tuya.com/1D0xBh6D
     */
    public static String getShortUrl(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return null;
        }

        try {
            JSONObject jsonObject = JSON.parseObject(jsonString);
            if (jsonObject == null) {
                return null;
            }
            return jsonObject.getString("shortUrl");
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }
}
