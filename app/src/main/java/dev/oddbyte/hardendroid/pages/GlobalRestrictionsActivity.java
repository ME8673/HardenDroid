package dev.oddbyte.hardendroid.pages;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.app.admin.IDevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.UserManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.rosan.dhizuku.api.Dhizuku;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.oddbyte.hardendroid.R;
import dev.oddbyte.hardendroid.pages.UsersActivity.RestrictionItem;

public class GlobalRestrictionsActivity extends AppCompatActivity {
    private static final String TAG = "GlobalRestrictionsActivity";
    private DevicePolicyManager dpm;
    private ComponentName admin;
    private List<RestrictionItem> globalRestrictions;
    private List<RestrictionItem> filteredRestrictions;
    private RestrictionAdapter adapter;
    private SwipeRefreshLayout swipeRefreshLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_global_restrictions);

        // Yeet default bar
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        // Set up back button handler
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        // Set up search button
        findViewById(R.id.searchButton).setOnClickListener(v -> showSearchDialog());

        try {
            // Initialize Dhizuku
            if (!Dhizuku.init(this)) {
                Toast.makeText(this, "Failed to initialize Dhizuku", Toast.LENGTH_LONG).show();
            }

            // Initialize DevicePolicyManager
            initDevicePolicyManager();

            // Initialize restrictions list
            globalRestrictions = initRestrictionsList();
            updateGlobalRestrictions();

            // Sort alphabetically by display name
            sortRestrictionsList();

            // Initialize filtered list with all items
            filteredRestrictions = new ArrayList<>(globalRestrictions);

            RecyclerView restrictionsRecyclerView = findViewById(R.id.restrictionsRecyclerView);
            restrictionsRecyclerView.setLayoutManager(new LinearLayoutManager(this));

            final RestrictionAdapter[] adapter = {new RestrictionAdapter(globalRestrictions)};
            restrictionsRecyclerView.setAdapter(adapter[0]);
            swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
            swipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.primary));
            swipeRefreshLayout.setOnRefreshListener(() -> {
                try {
                    // Re-initialize restrictions
                    globalRestrictions = initRestrictionsList();
                    sortRestrictionsList();
                    updateGlobalRestrictions();

                    // Reset filtered list
                    filteredRestrictions = new ArrayList<>(globalRestrictions);
                    adapter[0] = new RestrictionAdapter(filteredRestrictions);
                    restrictionsRecyclerView.setAdapter(adapter[0]);

                    // Reset subtitle
                    TextView subtitleText = findViewById(R.id.subtitleText);
                    subtitleText.setText("Global Restrictions");

                    Toast.makeText(this, "Restrictions reloaded", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e(TAG, "Error refreshing: ", e);
                    Toast.makeText(this, "Refresh failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                } finally {
                    swipeRefreshLayout.setRefreshing(false);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error initializing: ", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void sortRestrictionsList() {
        Collections.sort(globalRestrictions, (item1, item2) ->
                item1.name.compareToIgnoreCase(item2.name));
    }

    private void showSearchDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_search, null);
        EditText searchInput = dialogView.findViewById(R.id.searchInput);
        Button searchButton = dialogView.findViewById(R.id.searchButton);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();

        // Search function
        Runnable performSearch = () -> {
            try {
                String query = searchInput.getText().toString().toLowerCase().trim();

                // Create a filtered list
                List<RestrictionItem> results = new ArrayList<>();

                if (query.isEmpty() && globalRestrictions != null) {
                    results.addAll(globalRestrictions);
                } else if (globalRestrictions != null) {
                    for (RestrictionItem item : globalRestrictions) {
                        if (item.key.toLowerCase().contains(query) ||
                                item.name.toLowerCase().contains(query)) {
                            results.add(item);
                        }
                    }
                }

                if (results.isEmpty()) {
                    Toast.makeText(GlobalRestrictionsActivity.this,
                            "No results found", Toast.LENGTH_SHORT).show();
                } else {
                    // Update filtered list and recreate adapter
                    filteredRestrictions = results;

                    // Update UI
                    TextView subtitleText = findViewById(R.id.subtitleText);
                    if (!query.isEmpty()) {
                        subtitleText.setText("Global Restrictions - Filtered: " + query);
                    } else {
                        subtitleText.setText("Global Restrictions");
                    }

                    // Update recycler view with new adapter
                    RecyclerView restrictionsRecyclerView = findViewById(R.id.restrictionsRecyclerView);
                    adapter = new RestrictionAdapter(filteredRestrictions);
                    restrictionsRecyclerView.setAdapter(adapter);

                    dialog.dismiss();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error searching: ", e);
                Toast.makeText(GlobalRestrictionsActivity.this,
                        "Search error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };

        // Set up search button
        searchButton.setOnClickListener(v -> performSearch.run());

        // Set up keyboard action
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch.run();
                return true;
            }
            return false;
        });

        // Close button
        dialogView.findViewById(R.id.closeButton).setOnClickListener(v -> dialog.dismiss());
    }

    private void filterRestrictions(String query) {
        if (filteredRestrictions == null) {
            filteredRestrictions = new ArrayList<>();
        }

        filteredRestrictions.clear();

        if (query.isEmpty() && globalRestrictions != null) {
            filteredRestrictions.addAll(globalRestrictions);
        } else if (globalRestrictions != null) {
            for (RestrictionItem item : globalRestrictions) {
                if (item.key.toLowerCase().contains(query) ||
                        item.name.toLowerCase().contains(query)) {
                    filteredRestrictions.add(item);
                }
            }
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

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

    private List<RestrictionItem> initRestrictionsList() {
        List<RestrictionItem> restrictions = new ArrayList<>();

        // Network/Internet Restrictions
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS, "Block Mobile Network Configuration", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_CONFIG_WIFI, "Block WiFi Configuration", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_DATA_ROAMING, "Block Data Roaming", false));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) restrictions.add(new RestrictionItem(UserManager.DISALLOW_CELLULAR_2G, "Block Cellular 2G", false));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) restrictions.add(new RestrictionItem(UserManager.DISALLOW_ULTRA_WIDEBAND_RADIO, "Block Ultra Wideband Radio", false));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) restrictions.add(new RestrictionItem(UserManager.DISALLOW_ADD_WIFI_CONFIG, "Block Adding WiFi Configurations", false));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) restrictions.add(new RestrictionItem(UserManager.DISALLOW_CHANGE_WIFI_STATE, "Block Changing WiFi State", false));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) restrictions.add(new RestrictionItem(UserManager.DISALLOW_WIFI_DIRECT, "Block WiFi Direct", false));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) restrictions.add(new RestrictionItem(UserManager.DISALLOW_WIFI_TETHERING, "Block WiFi Tethering", false));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) restrictions.add(new RestrictionItem(UserManager.DISALLOW_SHARING_ADMIN_CONFIGURED_WIFI, "Block Sharing Admin WiFi", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_NETWORK_RESET, "Block Network Reset", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_CONFIG_TETHERING, "Block Tethering Configuration", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_CONFIG_VPN, "Block VPN Configuration", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_CONFIG_PRIVATE_DNS, "Block Private DNS Configuration", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_AIRPLANE_MODE, "Block Airplane Mode", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_CONFIG_CELL_BROADCASTS, "Block Cell Broadcast Configuration", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_SMS, "Block SMS", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_OUTGOING_CALLS, "Block Outgoing Calls", false));

        // Connectivity Restrictions
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_BLUETOOTH, "Block Bluetooth", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_BLUETOOTH_SHARING, "Block Bluetooth Sharing", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_SHARE_LOCATION, "Block Location Sharing", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_CONFIG_LOCATION, "Block Location Configuration", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_OUTGOING_BEAM, "Block Outgoing Beam", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_USB_FILE_TRANSFER, "Block USB File Transfer", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA, "Block Physical Media Mounting", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_PRINTING, "Block Printing", false));

        // Media Restrictions
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_CONFIG_BRIGHTNESS, "Block Brightness Configuration", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_CONFIG_SCREEN_TIMEOUT, "Block Screen Timeout Configuration", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_AMBIENT_DISPLAY, "Block Ambient Display", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_ADJUST_VOLUME, "Block Volume Adjustment", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_UNMUTE_MICROPHONE, "Block Microphone Unmuting", false));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) restrictions.add(new RestrictionItem(UserManager.DISALLOW_CAMERA_TOGGLE, "Block Camera Toggle", false));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) restrictions.add(new RestrictionItem(UserManager.DISALLOW_MICROPHONE_TOGGLE, "Block Microphone Toggle", false));

        // Application Restrictions
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_INSTALL_APPS, "Block App Installation", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY, "Block Unknown Sources Globally", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES, "Block Unknown Sources", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_UNINSTALL_APPS, "Block App Uninstallation", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_APPS_CONTROL, "Block Apps Control", false));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) restrictions.add(new RestrictionItem(UserManager.DISALLOW_CONFIG_DEFAULT_APPS, "Block Default Apps Configuration", false));

        // User Management Restrictions
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_ADD_USER, "Block Adding Users", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_REMOVE_USER, "Block Removing Users", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_USER_SWITCH, "Block User Switching", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_SET_USER_ICON, "Block Setting User Icon", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_CROSS_PROFILE_COPY_PASTE, "Block Cross-Profile Copy/Paste", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_SHARE_INTO_MANAGED_PROFILE, "Block Sharing to Managed Profile", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_UNIFIED_PASSWORD, "Block Unified Password", false));

        // Other Restrictions
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_AUTOFILL, "Block Autofill", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_CONFIG_CREDENTIALS, "Block Credential Configuration", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_CONTENT_CAPTURE, "Block Content Capture", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_CONTENT_SUGGESTIONS, "Block Content Suggestions", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_CREATE_WINDOWS, "Block Creating Windows", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_SET_WALLPAPER, "Block Setting Wallpaper", false));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) restrictions.add(new RestrictionItem(UserManager.DISALLOW_GRANT_ADMIN, "Block Granting Admin", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_FUN, "Block Fun", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_MODIFY_ACCOUNTS, "Block Account Modifications", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_CONFIG_LOCALE, "Block Locale Configuration", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_CONFIG_DATE_TIME, "Block Date/Time Configuration", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_SYSTEM_ERROR_DIALOGS, "Block System Error Dialogs", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_FACTORY_RESET, "Block Factory Reset", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_SAFE_BOOT, "Block Safe Boot", false));
        restrictions.add(new RestrictionItem(UserManager.DISALLOW_DEBUGGING_FEATURES, "Block Debugging Features", false));

        return restrictions;
    }

    private void updateGlobalRestrictions() {
        try {
            Bundle restrictions = dpm.getUserRestrictions(admin);
            for (RestrictionItem item : globalRestrictions) {
                item.enabled = restrictions.getBoolean(item.key, false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get global restrictions: ", e);
        }
    }

    private void setUserRestriction(String key, boolean value) throws Exception {
        try {
            Log.d(TAG, "Setting global restriction: key=" + key + ", value=" + value);

            if (value) {
                dpm.addUserRestriction(admin, key);
            } else {
                dpm.clearUserRestriction(admin, key);
            }

            Log.d(TAG, "User restriction set successfully");
            Toast.makeText(this, "Setting global restriction:\n" + key + " = " + value, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to set user restriction: ", e);
            throw new Exception("Set restriction failed: " + e.getMessage());
        }
    }

    private class RestrictionAdapter extends RecyclerView.Adapter<RestrictionAdapter.RestrictionViewHolder> {
        private List<UsersActivity.RestrictionItem> restrictions;

        RestrictionAdapter(List<UsersActivity.RestrictionItem> restrictions) {
            this.restrictions = restrictions;
        }

        @NonNull
        @Override
        public RestrictionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_restriction, parent, false);
            return new RestrictionViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RestrictionViewHolder holder, int position) {
            UsersActivity.RestrictionItem restriction = restrictions.get(position);
            holder.nameText.setText(restriction.name);
            holder.switchView.setChecked(restriction.enabled);

            holder.switchView.setOnCheckedChangeListener((buttonView, isChecked) -> {
                try {
                    setUserRestriction(restriction.key, isChecked);
                    restriction.enabled = isChecked;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to set restriction: ", e);
                    Toast.makeText(GlobalRestrictionsActivity.this, "Failed to set restriction: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    holder.switchView.setChecked(!isChecked);
                }
            });
        }

        @Override
        public int getItemCount() {
            return restrictions.size();
        }

        class RestrictionViewHolder extends RecyclerView.ViewHolder {
            TextView nameText;
            com.google.android.material.switchmaterial.SwitchMaterial switchView;

            RestrictionViewHolder(View itemView) {
                super(itemView);
                nameText = itemView.findViewById(R.id.restrictionNameText);
                switchView = itemView.findViewById(R.id.restrictionSwitch);
            }
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