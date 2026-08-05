package dev.zapret.mobile;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
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

    private TextView status;
    private AppTheme currentTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentTheme = ThemeSettings.getTheme(this);
        setContentView(buildContent());
        updateStatus(getString(R.string.state_stopped));
        requestNotificationPermissionIfNeeded();
        UpdateManager.checkOnLaunch(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppTheme latestTheme = ThemeSettings.getTheme(this);
        if (latestTheme != currentTheme) {
            currentTheme = latestTheme;
            setContentView(buildContent());
            updateStatus(getString(R.string.state_stopped));
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

        Button start = UiKit.primaryButton(this, currentTheme, getString(R.string.start_vpn));
        start.setOnClickListener(v -> requestVpn());
        body.addView(start, UiKit.fullWidth(this, 20, 10));

        Button stop = UiKit.dangerButton(this, getString(R.string.stop_vpn));
        stop.setOnClickListener(v -> {
            Intent intent = new Intent(this, ZapretVpnService.class);
            intent.setAction(ZapretVpnService.ACTION_STOP);
            startService(intent);
            updateStatus(getString(R.string.state_stopping));
        });
        body.addView(stop, UiKit.fullWidth(this, 0, 20));

        LinearLayout navRow = new LinearLayout(this);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams navButtonParams = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );

        Button strategiesButton = UiKit.outlineButton(this, currentTheme, getString(R.string.nav_strategies));
        strategiesButton.setOnClickListener(v -> startActivity(new Intent(this, StrategiesActivity.class)));
        LinearLayout.LayoutParams strategiesParams = new LinearLayout.LayoutParams(navButtonParams);
        strategiesParams.setMarginEnd(UiKit.dp(this, 8));
        navRow.addView(strategiesButton, strategiesParams);

        Button settingsButton = UiKit.outlineButton(this, currentTheme, getString(R.string.nav_settings));
        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
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
