package dev.oddbyte.hardendroid;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.rosan.dhizuku.api.Dhizuku;
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener;

import dev.oddbyte.hardendroid.pages.UsersActivity;
import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_SHIZUKU = 1;
    private TextView statusMessage;
    private Button refreshStatusButton;
    private boolean isDhizukuAvailable = false;
    private boolean isShizukuAvailable = false;
    private boolean isDhizukuPermissionGranted = false;
    private boolean isShizukuPermissionGranted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusMessage = findViewById(R.id.statusMessage);
        refreshStatusButton = findViewById(R.id.refreshStatusButton);

        refreshStatusButton.setOnClickListener(v -> checkStatus());

        checkStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkStatus();
    }

    private void checkStatus() {
        isDhizukuAvailable = Dhizuku.init(this);
        isShizukuAvailable = !Shizuku.isPreV11() && Shizuku.pingBinder();

        isDhizukuPermissionGranted = isDhizukuAvailable && Dhizuku.isPermissionGranted();
        isShizukuPermissionGranted = isShizukuAvailable && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;

        if (isDhizukuPermissionGranted && isShizukuPermissionGranted) {
            statusMessage.setText(R.string.both_permissions_granted);
            startActivity(new Intent(MainActivity.this, UsersActivity.class));
            finish();
        } else {
            updateStatusText();

            if (isDhizukuAvailable && !isDhizukuPermissionGranted) {
                requestDhizukuPermission();
            }

            if (isShizukuAvailable && !isShizukuPermissionGranted) {
                requestShizukuPermission();
            }
        }
    }

    private void updateStatusText() {
        if (!isDhizukuAvailable && !isShizukuAvailable) {
            statusMessage.setText(R.string.both_services_not_available);
        } else if (!isDhizukuAvailable) {
            statusMessage.setText(R.string.dhizuku_not_available);
        } else if (!isShizukuAvailable) {
            statusMessage.setText(R.string.shizuku_not_running);
        } else if (!isDhizukuPermissionGranted && !isShizukuPermissionGranted) {
            statusMessage.setText(R.string.both_permissions_denied);
        } else if (!isDhizukuPermissionGranted) {
            statusMessage.setText(R.string.dhizuku_permission_denied);
        } else if (!isShizukuPermissionGranted) {
            statusMessage.setText(R.string.shizuku_permission_denied);
        }
    }

    private void requestShizukuPermission() {
        if (!Shizuku.shouldShowRequestPermissionRationale()) {
            try {
                Shizuku.requestPermission(REQUEST_CODE_SHIZUKU);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, R.string.error_request_permission, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void requestDhizukuPermission() {
        try {
            Dhizuku.requestPermission(new DhizukuRequestPermissionListener() {
                @Override
                public void onRequestPermission(int grantResult) {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(MainActivity.this, R.string.dhizuku_permission_granted, Toast.LENGTH_SHORT).show();
                        isDhizukuPermissionGranted = true;
                    } else {
                        Toast.makeText(MainActivity.this, R.string.dhizuku_permission_denied, Toast.LENGTH_LONG).show();
                    }
                    checkStatus();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.error_request_permission, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_SHIZUKU) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.shizuku_permission_granted, Toast.LENGTH_SHORT).show();
                isShizukuPermissionGranted = true;
            } else {
                Toast.makeText(this, R.string.shizuku_permission_denied, Toast.LENGTH_LONG).show();
            }
            checkStatus();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SHIZUKU) {
            checkStatus();
        }
    }
}