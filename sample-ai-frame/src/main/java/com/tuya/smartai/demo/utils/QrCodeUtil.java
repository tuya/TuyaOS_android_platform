package com.tuya.smartai.demo.utils;


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
     * @param content 要编码到二维码中的文本内容
     * @param width 二维码图片的宽度（像素）
     * @param height 二维码图片的高度（像素）
     * @return 生成的 Bitmap 对象，如果内容为空或生成失败则返回 null
     */
    public static Bitmap generateQrCodeBitmap(String content, int width, int height) {
        if (content == null || content.isEmpty()) {
            return null;
        }

        // 配置参数
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);

        try {
            QRCodeWriter writer = new QRCodeWriter();
            // 1. 生成矩阵 (这一步也有点耗时，建议放在子线程)
            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height, hints);

            // 2. 预先分配像素数组 (避免几十万次 JNI 调用)
            int[] pixels = new int[width * height];

            // 3. 填充数组 (这是纯 Java 运算，极快)
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (bitMatrix.get(x, y)) {
                        pixels[y * width + x] = Color.BLACK;
                    } else {
                        pixels[y * width + x] = Color.WHITE;
                    }
                }
            }

            // 4. 创建 Bitmap 并一次性写入
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

            // 核心优化点：一次性写入所有像素
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);

            return bitmap;

        } catch (WriterException e) {
            e.printStackTrace();
            return null;
        }
    }


    /**
     * {"shortUrl":"https://t.tuya.com/1D0xBh6D"} ->   https://t.tuya.com/1D0xBh6D
     */
    public static String getShortUrl(String jsonString) {
        // 1. 检查输入是否为空或空白
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
            // 只有在 JSON 格式严重错误时才会捕获这个异常
            System.err.println("Fastjson parsing error: " + e.getMessage());
            e.printStackTrace();
            return null; // 返回 null 表示提取失败
        }
    }
}