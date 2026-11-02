package com.antigravity.usbdebugtile;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.text.HtmlCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    public static final String CMD_FREEZE = "adb shell pm disable-user --user 0 com.samsung.android.rampart";
    public static final String CMD_GRANT = "adb shell pm grant com.antigravity.usbdebugtile android.permission.WRITE_SECURE_SETTINGS";

    public static final String BAT_GRANT_SCRIPT =
            "@echo off\r\n" +
            "title USB Debugging - Grant Permission\r\n" +
            "echo ============================================================\r\n" +
            "echo   USB Debugging: Grant WRITE_SECURE_SETTINGS Permission\r\n" +
            "echo ============================================================\r\n" +
            "echo.\r\n" +
            "echo Waiting for device via ADB...\r\n" +
            "adb wait-for-device\r\n" +
            "echo Device detected!\r\n" +
            "echo.\r\n" +
            "echo Granting permission to com.antigravity.usbdebugtile...\r\n" +
            "adb shell pm grant com.antigravity.usbdebugtile android.permission.WRITE_SECURE_SETTINGS\r\n" +
            "echo.\r\n" +
            "if %ERRORLEVEL% EQU 0 (\r\n" +
            "    echo ============================================================\r\n" +
            "    echo [SUCCESS] Permission granted successfully!\r\n" +
            "    echo You can now toggle USB Debugging directly from Quick Settings.\r\n" +
            "    echo ============================================================\r\n" +
            ") else (\r\n" +
            "    echo ============================================================\r\n" +
            "    echo [ERROR] Failed to grant permission.\r\n" +
            "    echo Please make sure:\r\n" +
            "    echo   1. USB Debugging is turned ON in Developer Options.\r\n" +
            "    echo   2. Prompt 'Always allow from this computer' was accepted.\r\n" +
            "    echo ============================================================\r\n" +
            ")\r\n" +
            "echo.\r\n" +
            "pause\r\n";

    public static final String BAT_SETUP_SCRIPT =
            "@echo off\r\n" +
            "title USB Debugging - 1-Click Setup\r\n" +
            "echo ============================================================\r\n" +
            "echo       USB Debugging - Auto Setup for Samsung One UI\r\n" +
            "echo ============================================================\r\n" +
            "echo.\r\n" +
            "echo Waiting for device via ADB...\r\n" +
            "adb wait-for-device\r\n" +
            "echo Device detected!\r\n" +
            "echo.\r\n" +
            "echo [1/2] Disabling Samsung Auto Blocker...\r\n" +
            "adb shell pm disable-user --user 0 com.samsung.android.rampart\r\n" +
            "echo.\r\n" +
            "echo [2/2] Granting WRITE_SECURE_SETTINGS to USB Debugging...\r\n" +
            "adb shell pm grant com.antigravity.usbdebugtile android.permission.WRITE_SECURE_SETTINGS\r\n" +
            "echo.\r\n" +
            "if %ERRORLEVEL% EQU 0 (\r\n" +
            "    echo ============================================================\r\n" +
            "    echo [SUCCESS] Setup completed successfully!\r\n" +
            "    echo USB Debugging is ready for 1-tap toggling in Quick Settings.\r\n" +
            "    echo ============================================================\r\n" +
            ") else (\r\n" +
            "    echo ============================================================\r\n" +
            "    echo [ERROR] Setup encountered an issue.\r\n" +
            "    echo Please ensure USB Debugging is ON and PC is authorized.\r\n" +
            "    echo ============================================================\r\n" +
            ")\r\n" +
            "echo.\r\n" +
            "pause\r\n";

    // Hero Card
    private MaterialCardView cardHero;
    private MaterialCardView cardIconSquircle;
    private ImageView iconHeroAdb;
    private TextView textHeroTitle;
    private TextView textHeroSubtitle;
    private MaterialSwitch switchHeroAdb;
    private View layoutHeroSubstrip;
    private ImageView iconHeroSubstrip;
    private TextView textHeroSubstrip;

    // Status Section
    private TextView textSectionStatus;
    private View badgeAllReady;
    private ImageView iconPermission;
    private TextView textPermissionStatus;
    private MaterialCardView badgePermission;
    private TextView textBadgePermission;

    private ImageView iconAutoBlocker;
    private TextView textAutoBlockerStatus;
    private MaterialCardView badgeAutoBlocker;
    private TextView textBadgeAutoBlocker;

    // Dynamic Contextual Command Views
    private View layoutPermissionCommand;
    private View layoutAutoBlockerCommand;
    private View layoutExportScriptSection;

    // Joined Tonal Pill Action Buttons
    private View btnMainCopyFreeze;
    private View btnMainShareFreeze;
    private View btnMainHelpFreeze;
    private View btnMainCopyGrant;
    private View btnMainShareGrant;
    private View btnMainHelpGrant;

    private ContentObserver adbObserver;
    private ContentObserver autoBlockerObserver;
    private BottomSheetDialog permissionBottomSheet;

    // Port Security & Auto-Disable Views
    private MaterialCardView cardAutoDisableContainer;
    private MaterialCardView cardIconAutoDisable;
    private ImageView iconAutoDisable;
    private View layoutAutoDisableHeader;
    private MaterialSwitch switchAutoDisable;
    private View layoutAutoDisableDetails;
    private ChipGroup chipGroupTimeout;
    private Chip chipTimeout1m;
    private Chip chipTimeout5m;
    private Chip chipTimeout10m;
    private Chip chipTimeout15m;
    private Chip chipTimeout30m;
    private TextView textAutoDisableStatus;

    private boolean isSyncingChips = false;
    private final Handler countdownHandler = new Handler(Looper.getMainLooper());
    private final Runnable countdownRunnable = new Runnable() {
        @Override
        public void run() {
            updateAutoDisableCountdown();
            countdownHandler.postDelayed(this, 1000);
        }
    };

    private final BroadcastReceiver usbHardwareReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            AutoDisableManager.updateAutoDisableState(context);
            updateUi();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Enable Material Design 3 Dynamic Colors (Material You)
        DynamicColors.applyToActivityIfAvailable(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Ensure navigation bar & status bar match light/dark surface and draw crisp contrasting icons
        boolean isNightMode = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(!isNightMode);
            insetsController.setAppearanceLightNavigationBars(!isNightMode);
        }

        // Bind Views
        cardHero = findViewById(R.id.cardHero);
        cardIconSquircle = findViewById(R.id.cardIconSquircle);
        iconHeroAdb = findViewById(R.id.iconHeroAdb);
        textHeroTitle = findViewById(R.id.textHeroTitle);
        textHeroSubtitle = findViewById(R.id.textHeroSubtitle);
        switchHeroAdb = findViewById(R.id.switchHeroAdb);
        layoutHeroSubstrip = findViewById(R.id.layoutHeroSubstrip);
        iconHeroSubstrip = findViewById(R.id.iconHeroSubstrip);
        textHeroSubstrip = findViewById(R.id.textHeroSubstrip);

        textSectionStatus = findViewById(R.id.textSectionStatus);
        badgeAllReady = findViewById(R.id.badgeAllReady);
        iconPermission = findViewById(R.id.iconPermission);
        textPermissionStatus = findViewById(R.id.textPermissionStatus);
        badgePermission = findViewById(R.id.badgePermission);
        textBadgePermission = findViewById(R.id.textBadgePermission);

        iconAutoBlocker = findViewById(R.id.iconAutoBlocker);
        textAutoBlockerStatus = findViewById(R.id.textAutoBlockerStatus);
        badgeAutoBlocker = findViewById(R.id.badgeAutoBlocker);
        textBadgeAutoBlocker = findViewById(R.id.textBadgeAutoBlocker);

        layoutPermissionCommand = findViewById(R.id.layoutPermissionCommand);
        layoutAutoBlockerCommand = findViewById(R.id.layoutAutoBlockerCommand);
        layoutExportScriptSection = findViewById(R.id.layoutExportScriptSection);

        if (switchHeroAdb != null) {
            switchHeroAdb.setThumbIconDrawable(null);
        }

        // Optional tap on completed card to toggle details
        View cardPermissionContainer = findViewById(R.id.cardPermissionContainer);
        if (cardPermissionContainer != null) {
            cardPermissionContainer.setOnClickListener(v -> {
                boolean perm = UsbDebugTileService.hasSecureSettingsPermission(this);
                if (perm && layoutPermissionCommand != null) {
                    layoutPermissionCommand.setVisibility(
                            layoutPermissionCommand.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                }
            });
        }

        View cardAutoBlockerContainer = findViewById(R.id.cardAutoBlockerContainer);
        if (cardAutoBlockerContainer != null) {
            cardAutoBlockerContainer.setOnClickListener(v -> {
                boolean blocker = isAutoBlockerActive(this);
                if (!blocker && layoutAutoBlockerCommand != null) {
                    layoutAutoBlockerCommand.setVisibility(
                            layoutAutoBlockerCommand.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                }
            });
        }

        btnMainCopyFreeze = findViewById(R.id.btnMainCopyFreeze);
        btnMainShareFreeze = findViewById(R.id.btnMainShareFreeze);
        btnMainHelpFreeze = findViewById(R.id.btnMainHelpFreeze);
        btnMainCopyGrant = findViewById(R.id.btnMainCopyGrant);
        btnMainShareGrant = findViewById(R.id.btnMainShareGrant);
        btnMainHelpGrant = findViewById(R.id.btnMainHelpGrant);

        // Auto-Disable Views
        cardAutoDisableContainer = findViewById(R.id.cardAutoDisableContainer);
        cardIconAutoDisable = findViewById(R.id.cardIconAutoDisable);
        iconAutoDisable = findViewById(R.id.iconAutoDisable);
        layoutAutoDisableHeader = findViewById(R.id.layoutAutoDisableHeader);
        switchAutoDisable = findViewById(R.id.switchAutoDisable);
        layoutAutoDisableDetails = findViewById(R.id.layoutAutoDisableDetails);
        chipGroupTimeout = findViewById(R.id.chipGroupTimeout);
        chipTimeout1m = findViewById(R.id.chipTimeout1m);
        chipTimeout5m = findViewById(R.id.chipTimeout5m);
        chipTimeout10m = findViewById(R.id.chipTimeout10m);
        chipTimeout15m = findViewById(R.id.chipTimeout15m);
        chipTimeout30m = findViewById(R.id.chipTimeout30m);
        textAutoDisableStatus = findViewById(R.id.textAutoDisableStatus);

        if (switchAutoDisable != null) {
            switchAutoDisable.setThumbIconDrawable(null);
        }

        if (layoutAutoDisableHeader != null) {
            layoutAutoDisableHeader.setOnClickListener(v -> {
                if (switchAutoDisable != null) {
                    switchAutoDisable.toggle();
                }
            });
        }

        if (switchAutoDisable != null) {
            switchAutoDisable.setOnCheckedChangeListener((buttonView, isChecked) -> {
                AutoDisableManager.setAutoDisableEnabled(this, isChecked);
                if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
                    }
                }
                updateUi();
            });
        }

        if (chipGroupTimeout != null) {
            chipGroupTimeout.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (isSyncingChips || checkedIds.isEmpty()) return;
                int checkedId = checkedIds.get(0);
                int minutes = getTimeoutForChipId(checkedId);
                AutoDisableManager.setTimeoutMinutes(this, minutes);
                updateAutoDisableCountdown();
            });
        }


        // Click Listeners
        // Entire Hero Card acts as the master toggle switch (One UI style)
        cardHero.setOnClickListener(v -> handleToggleClick());

        btnMainCopyFreeze.setOnClickListener(v -> copyToClipboard(CMD_FREEZE, "Freeze command copied"));
        btnMainShareFreeze.setOnClickListener(v -> shareText(CMD_FREEZE, "Share Freeze Command to PC"));
        if (btnMainHelpFreeze != null) {
            btnMainHelpFreeze.setOnClickListener(v -> showAutoBlockerHelpDialog());
        }

        btnMainCopyGrant.setOnClickListener(v -> copyToClipboard(CMD_GRANT, "Grant command copied"));
        btnMainShareGrant.setOnClickListener(v -> shareText(CMD_GRANT, "Share Grant Command to PC"));
        if (btnMainHelpGrant != null) {
            btnMainHelpGrant.setOnClickListener(v -> showPermissionHelpDialog());
        }

        View btnMainExportScript = findViewById(R.id.btnMainExportScript);
        if (btnMainExportScript != null) {
            btnMainExportScript.setOnClickListener(v -> exportBatchFile("setup_usb_tile.bat", BAT_SETUP_SCRIPT, v));
        }

        View btnMainExportScriptHelp = findViewById(R.id.btnMainExportScriptHelp);
        if (btnMainExportScriptHelp != null) {
            btnMainExportScriptHelp.setOnClickListener(v -> showScriptHelpDialog());
        }

        // Observers
        adbObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                AutoDisableManager.updateAutoDisableState(MainActivity.this);
                updateUi();
            }
        };

        autoBlockerObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                updateUi();
            }
        };

        // Check if opened with request to show permission modal
        checkShowPermissionModal(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        checkShowPermissionModal(intent);
        updateUi();
    }

    private void checkShowPermissionModal(Intent intent) {
        if (intent != null && intent.getBooleanExtra("SHOW_PERMISSION_MODAL", false)) {
            if (!UsbDebugTileService.hasSecureSettingsPermission(this)) {
                showPermissionBottomSheet();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ADB_ENABLED),
                false,
                adbObserver
        );
        getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor("rampart_main_switch_enabled"),
                false,
                autoBlockerObserver
        );

        // USB connection receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        filter.addAction("android.hardware.usb.action.USB_STATE");
        registerReceiver(usbHardwareReceiver, filter);

        // Start countdown ticker
        countdownHandler.removeCallbacks(countdownRunnable);
        countdownHandler.post(countdownRunnable);

        updateUi();

        // Dismiss modal if permission was granted while app was in background
        if (permissionBottomSheet != null && permissionBottomSheet.isShowing()
                && UsbDebugTileService.hasSecureSettingsPermission(this)) {
            permissionBottomSheet.dismiss();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        getContentResolver().unregisterContentObserver(adbObserver);
        getContentResolver().unregisterContentObserver(autoBlockerObserver);

        // Stop countdown ticker
        countdownHandler.removeCallbacks(countdownRunnable);

        // Unregister USB receiver
        try {
            unregisterReceiver(usbHardwareReceiver);
        } catch (Exception ignored) {
        }
    }

    private void handleToggleClick() {
        boolean hasPermission = UsbDebugTileService.hasSecureSettingsPermission(this);

        if (!hasPermission) {
            showPermissionBottomSheet();
            return;
        }

        boolean isAdbOn = UsbDebugTileService.isAdbEnabled(this);
        boolean targetState = !isAdbOn;

        try {
            Settings.Global.putInt(
                    getContentResolver(),
                    Settings.Global.ADB_ENABLED,
                    targetState ? 1 : 0
            );
            showSnackbar(targetState ? "USB Debugging Enabled" : "USB Debugging Disabled");
            AutoDisableManager.updateAutoDisableState(this);
        } catch (SecurityException e) {
            showPermissionBottomSheet();
        }

        updateUi();
    }

    private void showPermissionBottomSheet() {
        if (permissionBottomSheet != null && permissionBottomSheet.isShowing()) {
            return;
        }

        permissionBottomSheet = new BottomSheetDialog(this);
        permissionBottomSheet.setContentView(R.layout.bottom_sheet_permission);

        MaterialButton btnOpenDevSettings = permissionBottomSheet.findViewById(R.id.btnOpenDevSettings);
        View btnSheetCopy = permissionBottomSheet.findViewById(R.id.btnSheetCopy);
        View btnSheetShare = permissionBottomSheet.findViewById(R.id.btnSheetShare);
        View btnSheetHelp = permissionBottomSheet.findViewById(R.id.btnSheetHelp);
        MaterialButton btnSheetExportBat = permissionBottomSheet.findViewById(R.id.btnSheetExportBat);

        if (btnOpenDevSettings != null) {
            btnOpenDevSettings.setOnClickListener(v -> {
                openDeveloperOptions();
                permissionBottomSheet.dismiss();
            });
        }

        if (btnSheetCopy != null) {
            btnSheetCopy.setOnClickListener(v -> copyToClipboard(CMD_GRANT, "Grant command copied"));
        }

        if (btnSheetShare != null) {
            btnSheetShare.setOnClickListener(v -> shareText(CMD_GRANT, "Share ADB Command to PC"));
        }

        if (btnSheetHelp != null) {
            btnSheetHelp.setOnClickListener(v -> showPermissionHelpDialog());
        }

        if (btnSheetExportBat != null) {
            btnSheetExportBat.setOnClickListener(v -> exportBatchFile("grant_permission.bat", BAT_GRANT_SCRIPT, v));
        }

        permissionBottomSheet.show();
    }

    private void openDeveloperOptions() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
            intent.putExtra(":settings:fragment_args_key", "enable_adb");
            Bundle bundle = new Bundle();
            bundle.putString(":settings:fragment_args_key", "enable_adb");
            intent.putExtra(":settings:show_fragment_args", bundle);
            startActivity(intent);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {
            }
        }
    }

    private void updateUi() {
        boolean isAdbOn = UsbDebugTileService.isAdbEnabled(this);
        boolean hasPermission = UsbDebugTileService.hasSecureSettingsPermission(this);
        boolean isAutoBlockerOn = isAutoBlockerActive(this);
        boolean allSystemsReady = hasPermission && !isAutoBlockerOn;

        // M3 Semantic Tokens
        int colorPrimary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, 0);
        int colorOnPrimary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimary, 0);
        int colorPrimaryContainer = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer, 0);
        int colorOnPrimaryContainer = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimaryContainer, 0);

        int colorSurfaceContainerHigh = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHigh, 0);
        int colorSurfaceContainerHighest = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHighest, 0);

        int colorOnSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0);
        int colorOnSurfaceVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0);

        int colorError = MaterialColors.getColor(this, com.google.android.material.R.attr.colorError, 0);
        int colorErrorContainer = MaterialColors.getColor(this, com.google.android.material.R.attr.colorErrorContainer, 0);
        int colorOnErrorContainer = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnErrorContainer, 0);

        // 1. M3 Expressive Hero Card State (Master Switch style)
        if (switchHeroAdb != null) {
            switchHeroAdb.setChecked(isAdbOn);
        }

        if (isAdbOn) {
            cardHero.setCardBackgroundColor(colorPrimaryContainer);
            textHeroTitle.setTextColor(colorOnPrimaryContainer);
            textHeroSubtitle.setTextColor(colorOnPrimaryContainer);
            textHeroTitle.setText("USB Debugging");
            textHeroSubtitle.setText("On • Physical port open for ADB");

            cardIconSquircle.setCardBackgroundColor(colorPrimary);
            iconHeroAdb.setColorFilter(colorOnPrimary);

            if (layoutHeroSubstrip != null) {
                layoutHeroSubstrip.setVisibility(View.VISIBLE);
                if (allSystemsReady) {
                    iconHeroSubstrip.setImageResource(R.drawable.ic_check_circle);
                    iconHeroSubstrip.setColorFilter(colorOnPrimaryContainer);
                    textHeroSubstrip.setText("Tile is armed and ready in Quick Settings");
                    textHeroSubstrip.setTextColor(colorOnPrimaryContainer);
                } else {
                    iconHeroSubstrip.setImageResource(R.drawable.ic_error_outline);
                    iconHeroSubstrip.setColorFilter(colorOnErrorContainer);
                    textHeroSubstrip.setText("Action required before QS tile can work");
                    textHeroSubstrip.setTextColor(colorOnErrorContainer);
                }
            }
        } else {
            cardHero.setCardBackgroundColor(colorSurfaceContainerHigh);
            textHeroTitle.setTextColor(colorOnSurface);
            textHeroSubtitle.setTextColor(colorOnSurfaceVariant);
            textHeroTitle.setText("USB Debugging");
            textHeroSubtitle.setText("Off • Physical port is secure");

            cardIconSquircle.setCardBackgroundColor(colorSurfaceContainerHighest);
            iconHeroAdb.setColorFilter(colorOnSurfaceVariant);

            if (layoutHeroSubstrip != null) {
                layoutHeroSubstrip.setVisibility(View.VISIBLE);
                if (allSystemsReady) {
                    iconHeroSubstrip.setImageResource(R.drawable.ic_check_circle);
                    iconHeroSubstrip.setColorFilter(colorPrimary);
                    textHeroSubstrip.setText("Tap switch or QS tile to enable debugging");
                    textHeroSubstrip.setTextColor(colorOnSurfaceVariant);
                } else {
                    iconHeroSubstrip.setImageResource(R.drawable.ic_error_outline);
                    iconHeroSubstrip.setColorFilter(colorError);
                    textHeroSubstrip.setText("Setup required before enabling QS tile");
                    textHeroSubstrip.setTextColor(colorOnSurfaceVariant);
                }
            }
        }

        // 2. Permission Status Item
        if (hasPermission) {
            iconPermission.setImageResource(R.drawable.ic_check_circle);
            iconPermission.setColorFilter(colorPrimary);
            textPermissionStatus.setText("Authorized for Quick Settings toggle");
            badgePermission.setCardBackgroundColor(colorPrimaryContainer);
            textBadgePermission.setTextColor(colorOnPrimaryContainer);
            textBadgePermission.setText("Granted");
            if (layoutPermissionCommand != null) {
                layoutPermissionCommand.setVisibility(View.GONE);
            }
        } else {
            iconPermission.setImageResource(R.drawable.ic_error_outline);
            iconPermission.setColorFilter(colorError);
            textPermissionStatus.setText("WRITE_SECURE_SETTINGS missing");
            badgePermission.setCardBackgroundColor(colorErrorContainer);
            textBadgePermission.setTextColor(colorOnErrorContainer);
            textBadgePermission.setText("Missing");
            if (layoutPermissionCommand != null) {
                layoutPermissionCommand.setVisibility(View.VISIBLE);
            }
        }

        // 3. Samsung Auto Blocker Item
        if (isAutoBlockerOn) {
            iconAutoBlocker.setImageResource(R.drawable.ic_error_outline);
            iconAutoBlocker.setColorFilter(colorError);
            textAutoBlockerStatus.setText("May block USB cable commands");
            badgeAutoBlocker.setCardBackgroundColor(colorErrorContainer);
            textBadgeAutoBlocker.setTextColor(colorOnErrorContainer);
            textBadgeAutoBlocker.setText("Active");
            if (layoutAutoBlockerCommand != null) {
                layoutAutoBlockerCommand.setVisibility(View.VISIBLE);
            }
        } else {
            iconAutoBlocker.setImageResource(R.drawable.ic_check_circle);
            iconAutoBlocker.setColorFilter(colorPrimary);
            textAutoBlockerStatus.setText("Safe for USB debugging");
            badgeAutoBlocker.setCardBackgroundColor(colorPrimaryContainer);
            textBadgeAutoBlocker.setTextColor(colorOnPrimaryContainer);
            textBadgeAutoBlocker.setText("Off");
            if (layoutAutoBlockerCommand != null) {
                layoutAutoBlockerCommand.setVisibility(View.GONE);
            }
        }

        // 4. Overall Setup / All Ready State
        if (allSystemsReady) {
            if (badgeAllReady != null) {
                badgeAllReady.setVisibility(View.VISIBLE);
            }
            if (layoutExportScriptSection != null) {
                layoutExportScriptSection.setVisibility(View.GONE);
            }
            if (textSectionStatus != null) {
                textSectionStatus.setText("Device Status");
            }
        } else {
            if (badgeAllReady != null) {
                badgeAllReady.setVisibility(View.GONE);
            }
            if (layoutExportScriptSection != null) {
                layoutExportScriptSection.setVisibility(View.VISIBLE);
            }
            if (textSectionStatus != null) {
                textSectionStatus.setText("Device Status & Actions");
            }
        }

        // 5. Port Security & Auto-Disable UI
        updateAutoDisableUi(colorPrimary, colorOnPrimary, colorPrimaryContainer,
                colorOnPrimaryContainer, colorSurfaceContainerHighest, colorOnSurfaceVariant, colorError);
    }

    public static boolean isAutoBlockerActive(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo("com.samsung.android.rampart", 0);
            int enabledSetting = pm.getApplicationEnabledSetting("com.samsung.android.rampart");
            if (enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    || enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                    || !appInfo.enabled) {
                return false;
            }
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }

        try {
            int switchState = Settings.Secure.getInt(
                    context.getContentResolver(),
                    "rampart_main_switch_enabled",
                    -1
            );
            if (switchState == 0) {
                return false;
            } else if (switchState == 1) {
                return true;
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    private void copyToClipboard(String text, String confirmationMessage) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("ADB Command", text);
            clipboard.setPrimaryClip(clip);
            showSnackbar(confirmationMessage);
        }
    }

    private void shareText(String text, String title) {
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, text);
        sendIntent.setType("text/plain");
        Intent shareIntent = Intent.createChooser(sendIntent, title);
        startActivity(shareIntent);
    }

    private void showSnackbar(String message) {
        Snackbar.make(findViewById(R.id.coordinatorLayout), message, Snackbar.LENGTH_SHORT).show();
    }

    private boolean exportBatchFile(String fileName, String content, View anchorView) {
        try {
            ContentResolver resolver = getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/x-bat");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            }

            // Remove prior entry with same name if present to avoid duplicate (1), (2) files
            try {
                Uri queryUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                String selection = MediaStore.MediaColumns.DISPLAY_NAME + " = ?";
                String[] selectionArgs = new String[]{fileName};
                resolver.delete(queryUri, selection, selectionArgs);
            } catch (Exception ignored) {
            }

            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                showSnackbar("Failed to create file in Downloads");
                return false;
            }

            try (OutputStream os = resolver.openOutputStream(uri)) {
                if (os != null) {
                    os.write(content.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
            }

            View targetView = anchorView != null ? anchorView : findViewById(R.id.coordinatorLayout);
            Snackbar.make(targetView, "Saved " + fileName + " to Downloads", Snackbar.LENGTH_LONG)
                    .setAction("OPEN FOLDER", v -> openDownloadsFolder())
                    .show();
            return true;
        } catch (Exception e) {
            showSnackbar("Error saving file: " + e.getMessage());
            return false;
        }
    }

    private void openDownloadsFolder() {
        try {
            Intent intent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                Uri uri = Uri.parse("content://com.android.externalstorage.documents/document/primary:Download");
                intent.setDataAndType(uri, "vnd.android.document/directory");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception ex) {
                showSnackbar("Saved in device's Download folder");
            }
        }
    }

    private void showAutoBlockerHelpDialog() {
        CharSequence formattedMessage = HtmlCompat.fromHtml(
                "<b>What this command does</b><br>" +
                "Freezes Samsung's Auto Blocker package (com.samsung.android.rampart) for user 0.<br><br>" +
                "<b>Why you need it</b><br>" +
                "Samsung Auto Blocker monitors physical USB ports and automatically blocks ADB commands, developer tooling, and sideloading. Even if you turn Auto Blocker OFF in Settings, background security policies can still silently prevent USB Debugging from opening properly.<br><br>" +
                "<b>Knox &amp; safety</b><br>" +
                "Freezing a package via ADB does not trip Knox or void warranty. The app remains safely installed on the system partition.<br><br>" +
                "<b>How to revert</b><br>" +
                "Run from your PC:<br>" +
                "<tt>adb shell pm enable com.samsung.android.rampart</tt>",
                HtmlCompat.FROM_HTML_MODE_LEGACY
        );

        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_help_outline)
                .setTitle("Why Freeze Auto Blocker?")
                .setMessage(formattedMessage)
                .setPositiveButton("Understood", null)
                .show();
    }

    private void showPermissionHelpDialog() {
        CharSequence formattedMessage = HtmlCompat.fromHtml(
                "<b>What this command does</b><br>" +
                "Grants this app permission to modify system-level global settings (Settings.Global.ADB_ENABLED).<br><br>" +
                "<b>Why you need it</b><br>" +
                "Android restricts regular apps from toggling developer settings. Granting this once via PC ADB empowers your Quick Settings tile to switch USB Debugging on and off with 1 tap, without navigating through Settings menus.<br><br>" +
                "<b>Safety &amp; revocation</b><br>" +
                "The permission is only used to toggle ADB. Uninstalling the app revokes it completely.",
                HtmlCompat.FROM_HTML_MODE_LEGACY
        );

        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_help_outline)
                .setTitle("Why WRITE_SECURE_SETTINGS?")
                .setMessage(formattedMessage)
                .setPositiveButton("Understood", null)
                .show();
    }

    private void showScriptHelpDialog() {
        CharSequence formattedMessage = HtmlCompat.fromHtml(
                "<b>What this script does</b><br>" +
                "An all-in-one Windows batch script (setup_usb_tile.bat) that automates your PC setup in a single double-click.<br><br>" +
                "<b>Actions performed</b><br>" +
                "1. Disables Samsung Auto Blocker (com.samsung.android.rampart)<br>" +
                "2. Grants WRITE_SECURE_SETTINGS to USB Debugging<br><br>" +
                "<b>How to use it</b><br>" +
                "1. Tap the left half of the button to export.<br>" +
                "2. Connect your phone to your PC via USB cable.<br>" +
                "3. In Windows Explorer, open:<br>" +
                "<i>This PC &gt; Galaxy S23+ &gt; Download</i><br>" +
                "4. Double-click <b>setup_usb_tile.bat</b>.<br><br>" +
                "<b>Safety</b><br>" +
                "Uses standard ADB commands with status confirmation and error handling. It does not modify system partitions or trip Knox.",
                HtmlCompat.FROM_HTML_MODE_LEGACY
        );

        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_help_outline)
                .setTitle("1-Click Setup Script Help")
                .setMessage(formattedMessage)
                .setPositiveButton("Understood", null)
                .show();
    }

    private void updateAutoDisableUi(int colorPrimary, int colorOnPrimary,
                                     int colorPrimaryContainer, int colorOnPrimaryContainer,
                                     int colorSurfaceContainerHighest, int colorOnSurfaceVariant,
                                     int colorError) {
        if (switchAutoDisable == null) return;

        boolean isAutoDisableOn = AutoDisableManager.isAutoDisableEnabled(this);

        // Temporarily detach listener to prevent feedback loop
        switchAutoDisable.setOnCheckedChangeListener(null);
        switchAutoDisable.setChecked(isAutoDisableOn);
        switchAutoDisable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AutoDisableManager.setAutoDisableEnabled(this, isChecked);
            if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
                }
            }
            updateUi();
        });

        if (isAutoDisableOn) {
            if (cardIconAutoDisable != null) {
                cardIconAutoDisable.setCardBackgroundColor(colorPrimaryContainer);
            }
            if (iconAutoDisable != null) {
                iconAutoDisable.setColorFilter(colorOnPrimaryContainer);
            }
            if (layoutAutoDisableDetails != null) {
                layoutAutoDisableDetails.setVisibility(View.VISIBLE);
            }
        } else {
            if (cardIconAutoDisable != null) {
                cardIconAutoDisable.setCardBackgroundColor(colorSurfaceContainerHighest);
            }
            if (iconAutoDisable != null) {
                iconAutoDisable.setColorFilter(colorOnSurfaceVariant);
            }
            if (layoutAutoDisableDetails != null) {
                layoutAutoDisableDetails.setVisibility(View.GONE);
            }
        }

        if (chipGroupTimeout != null) {
            int currentTimeout = AutoDisableManager.getTimeoutMinutes(this);
            int targetChipId = getChipIdForTimeout(currentTimeout);
            if (chipGroupTimeout.getCheckedChipId() != targetChipId) {
                isSyncingChips = true;
                chipGroupTimeout.check(targetChipId);
                isSyncingChips = false;
            }
        }

        updateAutoDisableCountdown();
    }

    private void updateAutoDisableCountdown() {
        if (textAutoDisableStatus == null) return;

        boolean isAutoDisableOn = AutoDisableManager.isAutoDisableEnabled(this);
        int colorOnSurfaceVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0);
        int colorPrimary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, 0);
        int colorError = MaterialColors.getColor(this, com.google.android.material.R.attr.colorError, 0);

        if (!isAutoDisableOn) {
            textAutoDisableStatus.setText("Auto-lock is disabled");
            textAutoDisableStatus.setTextColor(colorOnSurfaceVariant);
            return;
        }

        boolean isAdbOn = UsbDebugTileService.isAdbEnabled(this);
        if (!isAdbOn) {
            textAutoDisableStatus.setText("Armed • Will activate when USB debugging is enabled");
            textAutoDisableStatus.setTextColor(colorOnSurfaceVariant);
            return;
        }

        boolean isUsbConnected = AutoDisableManager.isUsbConnected(this);
        if (isUsbConnected) {
            textAutoDisableStatus.setText("USB cable connected • Port actively in use");
            textAutoDisableStatus.setTextColor(colorPrimary);
            return;
        }

        int remainingSeconds = AutoDisableManager.getRemainingSeconds(this);
        if (remainingSeconds > 0) {
            int minutes = remainingSeconds / 60;
            int seconds = remainingSeconds % 60;
            textAutoDisableStatus.setText(String.format("Unplugged • Auto-disabling in %d:%02d", minutes, seconds));
            textAutoDisableStatus.setTextColor(colorError);
        } else if (remainingSeconds == 0) {
            textAutoDisableStatus.setText("Unplugged • Disabling USB debugging...");
            textAutoDisableStatus.setTextColor(colorError);
            AutoDisableManager.executeAutoDisable(this);
            updateUi();
        } else {
            textAutoDisableStatus.setText("Armed • Waiting for disconnect");
            textAutoDisableStatus.setTextColor(colorOnSurfaceVariant);
        }
    }

    private int getTimeoutForChipId(int chipId) {
        if (chipId == R.id.chipTimeout1m) return 1;
        if (chipId == R.id.chipTimeout5m) return 5;
        if (chipId == R.id.chipTimeout10m) return 10;
        if (chipId == R.id.chipTimeout15m) return 15;
        if (chipId == R.id.chipTimeout30m) return 30;
        return AutoDisableManager.DEFAULT_TIMEOUT_MINUTES;
    }

    private int getChipIdForTimeout(int minutes) {
        if (minutes == 1) return R.id.chipTimeout1m;
        if (minutes == 5) return R.id.chipTimeout5m;
        if (minutes == 10) return R.id.chipTimeout10m;
        if (minutes == 15) return R.id.chipTimeout15m;
        if (minutes == 30) return R.id.chipTimeout30m;
        return R.id.chipTimeout10m;
    }
}
