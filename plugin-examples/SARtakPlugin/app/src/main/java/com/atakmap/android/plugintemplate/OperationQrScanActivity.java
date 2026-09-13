package com.atakmap.android.plugintemplate;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.plugintemplate.runtime.OperationQrScanResultStore;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;

import java.util.Collections;

public class OperationQrScanActivity extends Activity {

    private static final String TAG = "SARtakQrScanner";
    public static final String ACTION_SCAN_RESULT =
            "com.atakmap.android.plugintemplate.OPERATION_QR_SCAN_RESULT";
    public static final String EXTRA_JOIN_CODE = "join_code";
    public static final String EXTRA_HOST_PACKAGE = "host_package";
    private static final int REQUEST_CAMERA_PERMISSION = 7104;

    private DecoratedBarcodeView barcodeView;
    private boolean handled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && checkSelfPermission(Manifest.permission.CAMERA)
                            != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[] { Manifest.permission.CAMERA },
                        REQUEST_CAMERA_PERMISSION);
                return;
            }
            setupScanner();
        } catch (Throwable throwable) {
            failScanner("QR scanner could not be opened", throwable);
        }
    }

    private void setupScanner() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(0xff111417);

        TextView instruction = new TextView(this);
        instruction.setText("Scan SARtak operation QR code");
        instruction.setTextColor(0xfff2f5f7);
        instruction.setGravity(Gravity.CENTER);
        instruction.setTextSize(18);
        instruction.setPadding(16, 24, 16, 16);
        layout.addView(instruction, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        barcodeView = new DecoratedBarcodeView(this);
        barcodeView.getBarcodeView().setDecoderFactory(
                new DefaultDecoderFactory(Collections.singletonList(
                        BarcodeFormat.QR_CODE)));
        barcodeView.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                handleScanResult(result);
            }
        });
        layout.addView(barcodeView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(layout);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions,
                grantResults);
        if (requestCode != REQUEST_CAMERA_PERMISSION)
            return;
        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            try {
                setupScanner();
                if (barcodeView != null)
                    barcodeView.resume();
            } catch (Throwable throwable) {
                failScanner("QR scanner could not be opened", throwable);
            }
        } else {
            Toast.makeText(this,
                    "Camera permission is needed to scan operation QR codes",
                    Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (barcodeView != null)
                barcodeView.resume();
        } catch (Throwable throwable) {
            failScanner("QR scanner could not access the camera", throwable);
        }
    }

    @Override
    protected void onPause() {
        try {
            if (barcodeView != null)
                barcodeView.pause();
        } catch (Throwable throwable) {
            Log.w(TAG, "QR scanner pause failed", throwable);
        }
        super.onPause();
    }

    private void handleScanResult(BarcodeResult result) {
        if (handled || result == null || result.getText() == null)
            return;
        String text = result.getText().trim();
        if (!text.startsWith("SARTAK-OP1:")) {
            Toast.makeText(this, "Not a SARtak operation QR code",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        handled = true;
        OperationQrScanResultStore.save(this, text);
        Log.d(TAG, "SARtak operation QR scanned");
        Intent intent = new Intent(ACTION_SCAN_RESULT);
        intent.putExtra(EXTRA_JOIN_CODE, text);
        String hostPackage = getIntent() == null ? ""
                : getIntent().getStringExtra(EXTRA_HOST_PACKAGE);
        try {
            sendBroadcast(intent);
            Log.d(TAG, "Sent global QR result broadcast");
        } catch (Throwable throwable) {
            Log.w(TAG, "Android QR result broadcast failed", throwable);
        }
        if (hostPackage != null && hostPackage.trim().length() > 0) {
            try {
                Intent hostIntent = new Intent(ACTION_SCAN_RESULT);
                hostIntent.setPackage(hostPackage.trim());
                hostIntent.putExtra(EXTRA_JOIN_CODE, text);
                sendBroadcast(hostIntent);
                Log.d(TAG, "Sent targeted QR result broadcast to "
                        + hostPackage.trim());
            } catch (Throwable throwable) {
                Log.w(TAG, "Targeted QR result broadcast failed", throwable);
            }

            try {
                Intent showIntent = new Intent(
                        PluginTemplateDropDownReceiver.SHOW_PLUGIN);
                showIntent.setPackage(hostPackage.trim());
                showIntent.putExtra(EXTRA_JOIN_CODE, text);
                sendBroadcast(showIntent);
                Log.d(TAG, "Sent targeted SARtak open broadcast to "
                        + hostPackage.trim());
            } catch (Throwable throwable) {
                Log.w(TAG, "Targeted QR show broadcast failed", throwable);
            }
        }
        finish();
    }

    private void failScanner(String message, Throwable throwable) {
        Log.w(TAG, message, throwable);
        Toast.makeText(this,
                message + ". Paste the operation code instead.",
                Toast.LENGTH_LONG).show();
        finish();
    }
}
