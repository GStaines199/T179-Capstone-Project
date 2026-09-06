package com.atakmap.android.plugintemplate.runtime;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

public final class OperationQrCodeGenerator {

    private OperationQrCodeGenerator() {
    }

    public static Bitmap create(String value, int sizePx) throws WriterException {
        BitMatrix matrix = new MultiFormatWriter().encode(value,
                BarcodeFormat.QR_CODE, sizePx, sizePx);
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx,
                Bitmap.Config.ARGB_8888);
        for (int y = 0; y < sizePx; y++) {
            for (int x = 0; x < sizePx; x++) {
                bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK
                        : Color.WHITE);
            }
        }
        return bitmap;
    }
}
