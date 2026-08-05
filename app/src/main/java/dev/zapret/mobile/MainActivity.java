package dev.zapret.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.VpnService;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MainActivity extends Activity {
    private static final int VPN_REQUEST = 710;
    private TextView status;
    private Spinner profileSpinner;
    private TextView routingSummary;
    private Switch routingSwitch;
    private Switch quicSwitch;
    private Button chooseApps;
    private AppRoutingSettings.Snapshot routingSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        routingSettings = AppRoutingSettings.load(this);
        setContentView(buildContent());
        updateStatus(getString(R.string.state_stopped));
        updateRoutingUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        routingSettings = AppRoutingSettings.load(this);
        updateRoutingUi();
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
        int pad = dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(0xFFF6F8F7);

        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name));
        title.setTextColor(0xFF151A1D);
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);
        root.addView(title, fullWidth());

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.subtitle);
        subtitle.setTextColor(0xFF42514A);
        subtitle.setTextSize(15);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = fullWidth();
        subtitleParams.setMargins(0, dp(12), 0, dp(20));
        root.addView(subtitle, subtitleParams);

        status = new TextView(this);
        status.setTextColor(0xFF0F8A5F);
        status.setTextSize(18);
        status.setGravity(Gravity.CENTER);
        root.addView(status, fullWidth());

        TextView profileTitle = new TextView(this);
        profileTitle.setText(R.string.profile_title);
        profileTitle.setTextColor(0xFF151A1D);
        profileTitle.setTextSize(18);
        LinearLayout.LayoutParams profileTitleParams = fullWidth();
        profileTitleParams.setMargins(0, dp(20), 0, dp(4));
        root.addView(profileTitle, profileTitleParams);

        StrategyProfile[] profiles = StrategyProfile.values();
        String[] profileLabels = new String[profiles.length];
        for (int index = 0; index < profiles.length; index += 1) {
            profileLabels[index] = getString(profiles[index].getLabelResource());
        }
        ArrayAdapter<String> profileAdapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_item,
            profileLabels
        );
        profileAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        profileSpinner = new Spinner(this);
        profileSpinner.setAdapter(profileAdapter);
        profileSpinner.setSelection(EngineSettings.getStrategyProfile(this).ordinal());
        profileSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                EngineSettings.setStrategyProfile(MainActivity.this, profiles[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        root.addView(profileSpinner, fullWidth());

        TextView routingTitle = new TextView(this);
        routingTitle.setText(R.string.routing_title);
        routingTitle.setTextColor(0xFF151A1D);
        routingTitle.setTextSize(18);
        LinearLayout.LayoutParams routingTitleParams = fullWidth();
        routingTitleParams.setMargins(0, dp(24), 0, dp(4));
        root.addView(routingTitle, routingTitleParams);

        routingSwitch = new Switch(this);
        routingSwitch.setText(R.string.routing_selected_only);
        routingSwitch.setChecked(routingSettings.isSelectedOnly());
        routingSwitch.setOnCheckedChangeListener((button, checked) -> {
            AppRoutingSettings.save(this, checked, routingSettings.getPackages());
            routingSettings = AppRoutingSettings.load(this);
            updateRoutingUi();
        });
        root.addView(routingSwitch, fullWidth());

        routingSummary = new TextView(this);
        routingSummary.setTextColor(0xFF42514A);
        routingSummary.setTextSize(14);
        LinearLayout.LayoutParams routingSummaryParams = fullWidth();
        routingSummaryParams.setMargins(0, dp(4), 0, dp(8));
        root.addView(routingSummary, routingSummaryParams);

        chooseApps = new Button(this);
        chooseApps.setText(R.string.routing_choose_apps);
        chooseApps.setOnClickListener(v -> showAppSelectionDialog());
        root.addView(chooseApps, fullWidth());

        quicSwitch = new Switch(this);
        quicSwitch.setText(R.string.block_quic);
        quicSwitch.setChecked(EngineSettings.isQuicBlocked(this));
        quicSwitch.setOnCheckedChangeListener((button, checked) ->
            EngineSettings.setQuicBlocked(this, checked)
        );
        LinearLayout.LayoutParams quicParams = fullWidth();
        quicParams.setMargins(0, dp(12), 0, 0);
        root.addView(quicSwitch, quicParams);

        Button start = new Button(this);
        start.setText(R.string.start_vpn);
        start.setOnClickListener(v -> requestVpn());
        LinearLayout.LayoutParams buttonParams = fullWidth();
        buttonParams.setMargins(0, dp(24), 0, dp(10));
        root.addView(start, buttonParams);

        Button stop = new Button(this);
        stop.setText(R.string.stop_vpn);
        stop.setOnClickListener(v -> {
            Intent intent = new Intent(this, ZapretVpnService.class);
            intent.setAction(ZapretVpnService.ACTION_STOP);
            startService(intent);
            updateStatus(getString(R.string.state_stopping));
        });
        root.addView(stop, fullWidth());

        TextView diagnostics = new TextView(this);
        diagnostics.setText(getString(R.string.diagnostics, NativeZapretEngine.version()));
        diagnostics.setTextColor(0xFF151A1D);
        diagnostics.setTextSize(14);
        LinearLayout.LayoutParams diagnosticsParams = fullWidth();
        diagnosticsParams.setMargins(0, dp(24), 0, 0);
        root.addView(diagnostics, diagnosticsParams);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        return scroll;
    }

    private void requestVpn() {
        routingSettings = AppRoutingSettings.load(this);
        if (routingSettings.isSelectedOnly() && routingSettings.getPackages().isEmpty()) {
            updateStatus(getString(R.string.state_select_apps));
            showAppSelectionDialog();
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

    private void updateRoutingUi() {
        if (routingSettings == null || routingSummary == null || routingSwitch == null || chooseApps == null) {
            return;
        }
        if (routingSwitch.isChecked() != routingSettings.isSelectedOnly()) {
            routingSwitch.setChecked(routingSettings.isSelectedOnly());
        }
        int selectedCount = routingSettings.getPackages().size();
        if (!routingSettings.isSelectedOnly()) {
            routingSummary.setText(R.string.routing_all_apps_summary);
        } else if (selectedCount == 0) {
            routingSummary.setText(R.string.routing_no_apps_summary);
        } else {
            routingSummary.setText(getString(R.string.routing_selected_summary, selectedCount));
        }
        chooseApps.setEnabled(routingSettings.isSelectedOnly());
    }

    private void showAppSelectionDialog() {
        List<RoutableApp> apps = loadRoutableApps();
        if (apps.isEmpty()) {
            updateStatus(getString(R.string.state_no_apps_found));
            return;
        }

        Set<String> selectedPackages = new HashSet<>(routingSettings.getPackages());
        String[] labels = new String[apps.size()];
        boolean[] checked = new boolean[apps.size()];
        for (int index = 0; index < apps.size(); index += 1) {
            RoutableApp app = apps.get(index);
            labels[index] = app.label + " (" + app.packageName + ")";
            checked[index] = selectedPackages.contains(app.packageName);
        }

        new AlertDialog.Builder(this)
            .setTitle(R.string.routing_dialog_title)
            .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> {
                String packageName = apps.get(which).packageName;
                if (isChecked) {
                    selectedPackages.add(packageName);
                } else {
                    selectedPackages.remove(packageName);
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.routing_save, (dialog, which) -> {
                AppRoutingSettings.save(this, routingSwitch.isChecked(), selectedPackages);
                routingSettings = AppRoutingSettings.load(this);
                updateRoutingUi();
            })
            .show();
    }

    private List<RoutableApp> loadRoutableApps() {
        PackageManager packageManager = getPackageManager();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolvedApps = packageManager.queryIntentActivities(launcherIntent, 0);
        Map<String, RoutableApp> uniqueApps = new HashMap<>();
        for (ResolveInfo resolvedApp : resolvedApps) {
            if (resolvedApp.activityInfo == null) {
                continue;
            }
            String packageName = resolvedApp.activityInfo.packageName;
            if (getPackageName().equals(packageName)) {
                continue;
            }
            CharSequence loadedLabel = resolvedApp.loadLabel(packageManager);
            String label = loadedLabel == null ? packageName : loadedLabel.toString().trim();
            uniqueApps.put(packageName, new RoutableApp(label.isEmpty() ? packageName : label, packageName));
        }

        List<RoutableApp> apps = new ArrayList<>(uniqueApps.values());
        apps.sort(Comparator
            .comparing((RoutableApp app) -> app.label, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(app -> app.packageName));
        return apps;
    }

    private static final class RoutableApp {
        private final String label;
        private final String packageName;

        private RoutableApp(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }
}
