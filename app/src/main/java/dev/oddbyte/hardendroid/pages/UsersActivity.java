package dev.oddbyte.hardendroid.pages;

import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Parcelable;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.rosan.dhizuku.api.Dhizuku;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.oddbyte.hardendroid.R;
import rikka.shizuku.Shizuku;

public class UsersActivity extends AppCompatActivity {
    private static final String TAG = "UsersActivity";
    private RecyclerView recyclerView;
    private UserAdapter adapter;
    private TextView maxUsersText;
    private List<RestrictionItem> globalRestrictions;
    private DevicePolicyManager dpm;
    private ComponentName admin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_users);

        try {
            // Initialize Dhizuku
            if (!Dhizuku.init(this)) {
                Toast.makeText(this, "Failed to initialize Dhizuku. Please make sure it's installed and activated.", Toast.LENGTH_LONG).show();
            }

            // Initialize DevicePolicyManager
            initDevicePolicyManager();

            recyclerView = findViewById(R.id.userRecyclerView);
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.setNestedScrollingEnabled(true);

            Button addUserButton = findViewById(R.id.addUserButton);
            addUserButton.setOnClickListener(v -> showAddUserDialog());

            Button globalSettingsButton = findViewById(R.id.globalSettingsButton);
            globalSettingsButton.setOnClickListener(v -> showGlobalSettingsDialog());

            maxUsersText = findViewById(R.id.maxUsersText);

            // Initialize global restrictions
            globalRestrictions = initRestrictionsList();

            updateMaxUsersInfo();
            refreshUsers();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing: ", e);
            Toast.makeText(this, "Error initializing services: " + e.getMessage(), Toast.LENGTH_LONG).show();
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

    @Override
    protected void onResume() {
        super.onResume();
        refreshUsers();
    }

    private void showGlobalSettingsDialog() {
        // Start GlobalSettingsActivity instead of showing a dialog
        Intent intent = new Intent(this, GlobalSettingsActivity.class);
        startActivity(intent);
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

    private void refreshUsers() {
        List<UserItem> users = getUserList();
        adapter = new UserAdapter(users);
        recyclerView.setAdapter(adapter);
    }

    private List<UserItem> getUserList() {
        List<UserItem> userItems = new ArrayList<>();

        try {
            Process process = Shizuku.newProcess(new String[]{"pm", "list", "users"}, null, null);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            Pattern pattern = Pattern.compile("UserInfo\\{(\\d+):(.*?):");

            while ((line = reader.readLine()) != null) {
                Log.d(TAG, "User list line: " + line);
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    int id = Integer.parseInt(matcher.group(1));
                    String name = matcher.group(2);
                    if (name == null || name.isEmpty()) {
                        name = "User " + id;
                    }
                    userItems.add(new UserItem(id, name));
                }
            }

            process.waitFor();

            if (userItems.isEmpty()) {
                Log.w(TAG, "No users found, adding current user as fallback");
                userItems.add(new UserItem(0, "Current User"));
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to get user list: ", e);
            Toast.makeText(this, "Failed to get user list: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            userItems.add(new UserItem(0, "Current User"));
        }

        return userItems;
    }

    private void showAddUserDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_add_user, null);
        EditText nameInput = view.findViewById(R.id.userNameInput);

        AlertDialog dialog = builder.setView(view)
                .setTitle("Add New User")
                .setPositiveButton("Add", (dialogInterface, which) -> {
                    String userName = nameInput.getText().toString();
                    if (userName.isEmpty()) {
                        userName = "User";
                    }
                    try {
                        createUser(userName);
                        refreshUsers();
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to create user: ", e);
                        Toast.makeText(UsersActivity.this, "Failed to create user: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();
    }

    private void createUser(String userName) throws Exception {
        try {
            Method createUser = DevicePolicyManager.class.getDeclaredMethod("createAndManageUser",
                    ComponentName.class, String.class, ComponentName.class, PersistableBundle.class, int.class);
            createUser.setAccessible(true);

            UserHandle newUser = (UserHandle) createUser.invoke(dpm, admin, userName, admin, null, 0);

            if (newUser != null) {
                Toast.makeText(UsersActivity.this, "User \"" + userName + "\" created", Toast.LENGTH_SHORT).show();
            } else {
                throw new Exception("Failed to create user");
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to create user: ", e);
            throw new Exception("Create user failed: " + e.getMessage());
        }
    }

    private void renameUser(int userId, String newName) throws Exception {
        try {
            int result = Shizuku.newProcess(new String[]{"pm", "rename-user", String.valueOf(userId), newName}, null, null).waitFor();
            if (result == 0) {
                Toast.makeText(UsersActivity.this, "User renamed successfully", Toast.LENGTH_SHORT).show();
                refreshUsers();
            } else {
                throw new Exception("Failed to rename user with exit code: " + result);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to rename user: ", e);
            throw new Exception("Rename user failed: " + e.getMessage());
        }
    }

    @SuppressLint("PrivateApi")
    private void stopUser(int userId) throws Exception {
        try {
            Method stopUser = DevicePolicyManager.class.getDeclaredMethod("stopUser",
                    ComponentName.class, UserHandle.class);
            stopUser.setAccessible(true);

            // Create UserHandle for userId
            Class<?> userHandleClass = UserHandle.class;
            Method getUserHandleForUid = userHandleClass.getDeclaredMethod("of", int.class);
            getUserHandleForUid.setAccessible(true);
            UserHandle userHandle = (UserHandle) getUserHandleForUid.invoke(null, userId);

            int result = (int) stopUser.invoke(dpm, admin, userHandle);

            if (result == UserManager.USER_OPERATION_SUCCESS) {
                Toast.makeText(UsersActivity.this, "User stopped", Toast.LENGTH_SHORT).show();
            } else {
                throw new Exception("Failed to stop user");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop user: ", e);
            throw new Exception("Stop user failed: " + e.getMessage());
        }
    }

    @SuppressLint("PrivateApi")
    private void switchToUser(int userId) throws Exception {
        try {
            Method switchUser = DevicePolicyManager.class.getDeclaredMethod("switchUser",
                    ComponentName.class, UserHandle.class);
            switchUser.setAccessible(true);

            // Create UserHandle for userId
            Class<?> userHandleClass = UserHandle.class;
            Method getUserHandleForUid = userHandleClass.getDeclaredMethod("of", int.class);
            getUserHandleForUid.setAccessible(true);
            UserHandle userHandle = (UserHandle) getUserHandleForUid.invoke(null, userId);

            Boolean result = (Boolean) switchUser.invoke(dpm, admin, userHandle);

            if (result) {
                Toast.makeText(UsersActivity.this, "Switching to user...", Toast.LENGTH_SHORT).show();
            } else {
                throw new Exception("Failed to switch user");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to switch user: ", e);
            throw new Exception("Switch user failed: " + e.getMessage());
        }
    }

    @SuppressLint("PrivateApi")
    private void removeUser(int userId) throws Exception {
        try {
            Method removeUser = DevicePolicyManager.class.getDeclaredMethod("removeUser",
                    ComponentName.class, UserHandle.class);
            removeUser.setAccessible(true);

            // Create UserHandle for userId
            Class<?> userHandleClass = UserHandle.class;
            Method getUserHandleForUid = userHandleClass.getDeclaredMethod("of", int.class);
            getUserHandleForUid.setAccessible(true);
            UserHandle userHandle = (UserHandle) getUserHandleForUid.invoke(null, userId);

            Boolean result = (Boolean) removeUser.invoke(dpm, admin, userHandle);

            if (result) {
                Toast.makeText(UsersActivity.this, "User deleted", Toast.LENGTH_SHORT).show();
            } else {
                throw new Exception("Failed to remove user");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to remove user: ", e);
            throw new Exception("Remove user failed: " + e.getMessage());
        }
    }

    private void updateMaxUsersInfo() {
        try {
            Method getMaxUsersMethod = UserManager.class.getDeclaredMethod("getMaxSupportedUsers");
            getMaxUsersMethod.setAccessible(true);
            UserManager userManager = getSystemService(UserManager.class);
            int maxUsers = (int) getMaxUsersMethod.invoke(userManager);
            maxUsersText.setText("Maximum supported users: " + maxUsers);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get max users info: ", e);
            maxUsersText.setText("Failed to get max users info");
        }
    }

    public static class UserItem {
        public int id;
        public String name;

        public UserItem(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static class RestrictionItem {
        public String key;
        public String name;
        public boolean enabled;

        public RestrictionItem(String key, String name, boolean enabled) {
            this.key = key;
            this.name = name;
            this.enabled = enabled;
        }
    }

    private void setUserRestriction(int userId, String key, boolean value) throws Exception {
        try {
            Log.d(TAG, "Setting " + (userId == -1 ? "global" : "user") + " restriction: user=" + userId + ", key=" + key + ", value=" + value);

            if (value) {
                dpm.addUserRestriction(admin, key);
            } else {
                dpm.clearUserRestriction(admin, key);
            }

            Log.d(TAG, "User restriction set successfully");
            Toast.makeText(UsersActivity.this, "Restriction updated", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to set user restriction: ", e);
            throw new Exception("Set restriction failed: " + e.getMessage());
        }
    }

    private class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {
        private List<UserItem> users;

        UserAdapter(List<UserItem> users) {
            this.users = users;
        }

        @NonNull
        @Override
        public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
            return new UserViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
            UserItem user = users.get(position);
            holder.userNameText.setText(user.name);
            holder.userIdText.setText("ID: " + user.id);

            // Disable delete & stop user buttons for user 0 ("Owner" user)
            if (user.id == 0) {
                holder.deleteButton.setEnabled(false);
                holder.deleteButton.setAlpha(0.5f);
                holder.stopUserButton.setEnabled(false);
                holder.stopUserButton.setAlpha(0.5f);
            } else {
                holder.deleteButton.setEnabled(true);
                holder.deleteButton.setAlpha(1.0f);
                holder.stopUserButton.setEnabled(true);
                holder.stopUserButton.setAlpha(1.0f);
            }

            holder.stopUserButton.setOnClickListener(v -> {
                if (user.id == 0) {
                    Toast.makeText(UsersActivity.this, "Cannot stop system user", Toast.LENGTH_SHORT).show();
                    return;
                }

                new AlertDialog.Builder(UsersActivity.this)
                        .setTitle("Stop User")
                        .setMessage("Are you sure you want to stop user '" + user.name + "'?")
                        .setPositiveButton("Stop", (dialog, which) -> {
                            try {
                                stopUser(user.id);
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to stop user: ", e);
                                Toast.makeText(UsersActivity.this, "Failed to stop user: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });

            holder.deleteButton.setOnClickListener(v -> {
                if (user.id == 0) {
                    Toast.makeText(UsersActivity.this, "Cannot delete system user", Toast.LENGTH_SHORT).show();
                    return;
                }

                new AlertDialog.Builder(UsersActivity.this)
                        .setTitle("Delete User")
                        .setMessage("Are you sure you want to delete user '" + user.name + "'?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            try {
                                removeUser(user.id);
                                users.remove(position);
                                notifyDataSetChanged();
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to delete user: ", e);
                                Toast.makeText(UsersActivity.this, "Failed to delete user: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });

            holder.renameButton.setOnClickListener(v -> {
                showRenameDialog(user);
            });

            holder.switchButton.setOnClickListener(v -> {
                try {
                    switchToUser(user.id);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to switch user: ", e);
                    Toast.makeText(UsersActivity.this, "Failed to switch user: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }

        @Override
        public int getItemCount() {
            return users.size();
        }

        private void showRenameDialog(UserItem user) {
            AlertDialog.Builder builder = new AlertDialog.Builder(UsersActivity.this);
            View view = getLayoutInflater().inflate(R.layout.dialog_add_user, null);
            EditText nameInput = view.findViewById(R.id.userNameInput);
            nameInput.setText(user.name);

            AlertDialog dialog = builder.setView(view)
                    .setTitle("Rename User")
                    .setPositiveButton("Rename", (dialogInterface, which) -> {
                        String newName = nameInput.getText().toString();
                        if (!newName.isEmpty()) {
                            try {
                                renameUser(user.id, newName);
                            } catch (Exception e) {
                                Toast.makeText(UsersActivity.this, "Failed to rename user: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .create();

            dialog.show();
        }

        class UserViewHolder extends RecyclerView.ViewHolder {
            TextView userNameText;
            TextView userIdText;
            Button deleteButton;
            Button renameButton;
            Button switchButton;
            Button stopUserButton;

            UserViewHolder(View itemView) {
                super(itemView);
                userNameText = itemView.findViewById(R.id.userNameText);
                userIdText = itemView.findViewById(R.id.userIdText);
                deleteButton = itemView.findViewById(R.id.deleteUserButton);
                renameButton = itemView.findViewById(R.id.renameUserButton);
                switchButton = itemView.findViewById(R.id.switchUserButton);
                stopUserButton = itemView.findViewById(R.id.stopUserButton);
            }
        }
    }
}