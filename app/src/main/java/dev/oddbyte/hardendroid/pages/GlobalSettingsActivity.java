package dev.oddbyte.hardendroid.pages;

import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.rosan.dhizuku.api.Dhizuku;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import dev.oddbyte.hardendroid.R;

public class GlobalSettingsActivity extends AppCompatActivity {
    private static final String TAG = "GlobalSettingsActivity";
    private DevicePolicyManager dpm;
    private ComponentName admin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_global_settings);

        // Setup back button in action bar
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Global Settings");

        try {
            // Initialize Dhizuku
            if (!Dhizuku.init(this)) {
                Toast.makeText(this, "Failed to initialize Dhizuku", Toast.LENGTH_LONG).show();
            }

            // Initialize DevicePolicyManager
            initDevicePolicyManager();

            // Set up Global Restrictions button
            Button restrictionsButton = findViewById(R.id.globalRestrictionsButton);
            restrictionsButton.setOnClickListener(v -> {
                Intent intent = new Intent(this, GlobalRestrictionsActivity.class);
                startActivity(intent);
            });
        } catch (Exception e) {
            Log.e(TAG, "Error initializing: ", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @SuppressLint("PrivateApi")
    private void initDevicePolicyManager() throws Exception {
        Context dhizukuContext = getApplicationContext().createPackageContext(
                Dhizuku.getOwnerPackageName(),
                Context.CONTEXT_IGNORE_SECURITY
        );

        dpm = dhizukuContext.getSystemService(DevicePolicyManager.class);
        admin = Dhizuku.getOwnerComponent();

        // Use reflection but with a approach compatible w/ API 34+
        Class<?> dpmClass = dpm.getClass();

        // Get the IDevicePolicyManager interface through reflection
        Class<?> iDevicePolicyManagerClass = Class.forName("android.app.admin.IDevicePolicyManager");

        // Find the internal field that holds the service, but avoid using "mService" directly cause its gonna get blocked in API 34 :|
        // Thanks Google for making my life harder! (.-.)
        Field serviceField = null;
        for (Field field : dpmClass.getDeclaredFields()) {
            if (iDevicePolicyManagerClass.isAssignableFrom(field.getType())) {
                serviceField = field;
                break;
            }
        }

        if (serviceField != null) {
            serviceField.setAccessible(true);
            Object service = serviceField.get(dpm);

            // Wrap the binder
            Method asBinder = service.getClass().getMethod("asBinder");
            IBinder binder = (IBinder) asBinder.invoke(service);
            IBinder wrappedBinder = Dhizuku.binderWrapper(binder);

            // Create wrapped service
            Class<?> stubClass = Class.forName("android.app.admin.IDevicePolicyManager$Stub");
            Method asInterface = stubClass.getMethod("asInterface", IBinder.class);
            Object wrappedService = asInterface.invoke(null, wrappedBinder);

            // Set the wrapped service back
            serviceField.set(dpm, wrappedService);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}