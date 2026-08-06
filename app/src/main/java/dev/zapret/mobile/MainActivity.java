package dev.zapret.mobile;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.VpnService;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final int VPN_REQUEST = 710;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 711;

    /** Red for "running, tap to stop"; the theme accent means "stopped, tap to start". */
    private static final int POWER_STOP_COLOR = 0xFFD64545;

    private TextView status;
    private TextView powerButton;
    private TextView powerHint;
    private AppTheme currentTheme;
    private AppLanguage currentLanguage;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LanguageSettings.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        currentTheme = ThemeSettings.getTheme(this);
        UiKit.applyTheme(this, currentTheme);
        super.onCreate(savedInstanceState);
        currentLanguage = LanguageSettings.getLanguage(this);
        setContentView(buildContent());
        UiKit.applyWindowChrome(this, currentTheme);
        updateStatus(getString(
            ZapretVpnService.isRunning() ? R.string.state_starting : R.string.state_stopped));
        requestNotificationPermissionIfNeeded();
        UpdateManager.checkOnLaunch(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // A language change has to go through recreate(), not setContentView:
        // strings resolve against the context built in attachBaseContext, so
        // rebuilding the views alone would keep rendering the old language.
        if (LanguageSettings.getLanguage(this) != currentLanguage) {
            recreate();
            return;
        }
        // A palette change also swaps the platform theme, which can only be
        // applied before the content view exists -- so recreate rather than
        // rebuild, same as for a language change.
        if (ThemeSettings.getTheme(this) != currentTheme) {
            recreate();
            return;
        }
        // The service can have started or stopped while this screen was away
        // (notification actions, onRevoke, a crash), so the control is
        // re-synced from the service rather than from what was last tapped.
        boolean running = ZapretVpnService.isRunning();
        applyPowerState(running);
        updateStatus(getString(running ? R.string.state_starting : R.string.state_stopped));
    }

    private void togglePower() {
        if (ZapretVpnService.isRunning()) {
            AppLog.userAction(this, "Tapped Stop VPN");
            Intent intent = new Intent(this, ZapretVpnService.class);
            intent.setAction(ZapretVpnService.ACTION_STOP);
            startService(intent);
            updateStatus(getString(R.string.state_stopping));
            applyPowerState(false);
        } else {
            AppLog.userAction(this, "Tapped Start VPN");
            requestVpn();
        }
    }

    /** Repaints the single power control to match `running`. */
    private void applyPowerState(boolean running) {
        if (powerButton == null) {
            return;
        }
        int fill = running ? POWER_STOP_COLOR : currentTheme.accent;
        powerButton.setText(getString(running ? R.string.power_stop : R.string.power_start));
        powerButton.setTextColor(running ? 0xFFFFFFFF : currentTheme.onAccent);
        GradientDrawable disc = new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        disc.setColor(fill);
        disc.setStroke(UiKit.dp(this, 6), (fill & 0x00FFFFFF) | 0x55000000);
        powerButton.setBackground(disc);
        if (powerHint != null) {
            powerHint.setText(getString(
                running ? R.string.power_hint_stop : R.string.power_hint_start));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST && resultCode == RESULT_OK) {
            startVpn();
        } else if (requestCode == VPN_REQUEST) {
            updateStatus(getString(R.string.state_permission_denied));
        }
    }

    private ScrollView buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(currentTheme.background);

        root.addView(UiKit.headerBanner(
            this,
            currentTheme,
            getString(R.string.app_name),
            getString(R.string.subtitle)
        ), UiKit.fullWidth());

        int pad = UiKit.dp(this, 24);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(pad, pad, pad, pad);
        body.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout statusCard = UiKit.card(this, currentTheme);
        statusCard.setGravity(Gravity.CENTER_HORIZONTAL);
        status = new TextView(this);
        status.setTextColor(currentTheme.accent);
        status.setTextSize(18);
        status.setGravity(Gravity.CENTER);
        statusCard.addView(status, UiKit.fullWidth());
        body.addView(statusCard, UiKit.fullWidth());

        boolean running = ZapretVpnService.isRunning();
        powerButton = UiKit.powerButton(
            this,
            currentTheme,
            getString(running ? R.string.power_stop : R.string.power_start),
            running ? POWER_STOP_COLOR : currentTheme.accent,
            running ? 0xFFFFFFFF : currentTheme.onAccent
        );
        powerButton.setOnClickListener(v -> togglePower());
        body.addView(powerButton, UiKit.circle(this, 190, 24, 12));

        powerHint = UiKit.bodyText(this, currentTheme, "");
        powerHint.setGravity(Gravity.CENTER);
        body.addView(powerHint, UiKit.fullWidth(this, 0, 20));
        applyPowerState(running);

        LinearLayout navRow = new LinearLayout(this);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams navButtonParams = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );

        Button strategiesButton = UiKit.outlineButton(this, currentTheme, getString(R.string.nav_strategies));
        strategiesButton.setOnClickListener(v -> {
            AppLog.userAction(this, "Opened Strategies screen");
            startActivity(new Intent(this, StrategiesActivity.class));
        });
        LinearLayout.LayoutParams strategiesParams = new LinearLayout.LayoutParams(navButtonParams);
        strategiesParams.setMarginEnd(UiKit.dp(this, 8));
        navRow.addView(strategiesButton, strategiesParams);

        Button settingsButton = UiKit.outlineButton(this, currentTheme, getString(R.string.nav_settings));
        settingsButton.setOnClickListener(v -> {
            AppLog.userAction(this, "Opened Settings screen");
            startActivity(new Intent(this, SettingsActivity.class));
        });
        navRow.addView(settingsButton, navButtonParams);

        body.addView(navRow, UiKit.fullWidth());

        root.addView(body, UiKit.fullWidth());

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(currentTheme.background);
        scroll.addView(root);
        return scroll;
    }

    private void requestVpn() {
        AppRoutingSettings.Snapshot routingSettings = AppRoutingSettings.load(this);
        if (routingSettings.isSelectedOnly() && routingSettings.getPackages().isEmpty()) {
            updateStatus(getString(R.string.state_select_apps));
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        updateStatus(getString(R.string.state_preparing));
        Intent prepareIntent = VpnService.prepare(this);
        if (prepareIntent != null) {
            startActivityForResult(prepareIntent, VPN_REQUEST);
        } else {
            startVpn();
        }
    }

    private void startVpn() {
        Intent intent = new Intent(this, ZapretVpnService.class);
        intent.setAction(ZapretVpnService.ACTION_START);
        startForegroundService(intent);
        updateStatus(getString(R.string.state_starting));
        // The tunnel comes up asynchronously, so the control is flipped
        // optimistically here and re-synced from the service in onResume.
        applyPowerState(true);
    }

    private void updateStatus(String text) {
        if (status != null) {
            status.setText(getString(R.string.status_format, text));
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
            && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                new String[] {android.Manifest.permission.POST_NOTIFICATIONS},
                NOTIFICATION_PERMISSION_REQUEST
            );
        }
    }
}
