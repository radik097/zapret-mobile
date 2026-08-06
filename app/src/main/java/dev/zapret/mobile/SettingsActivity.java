package dev.zapret.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SettingsActivity extends Activity {
    private AppTheme currentTheme;
    private AppRoutingSettings.Snapshot routingSettings;
    private TextView routingSummary;
    private Switch routingSwitch;
    private Button chooseApps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentTheme = ThemeSettings.getTheme(this);
        routingSettings = AppRoutingSettings.load(this);
        setContentView(buildContent());
        updateRoutingUi();
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LanguageSettings.wrap(base));
    }

    @Override
    protected void onResume() {
        super.onResume();
        routingSettings = AppRoutingSettings.load(this);
        updateRoutingUi();
    }

    private ScrollView buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(currentTheme.background);
        root.addView(
            UiKit.headerBanner(this, currentTheme, getString(R.string.nav_settings), null),
            UiKit.fullWidth()
        );

        int pad = UiKit.dp(this, 24);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(pad, pad, pad, pad);

        body.addView(buildLanguageCard(), UiKit.fullWidth(this, 0, 16));
        body.addView(buildThemeCard(), UiKit.fullWidth(this, 0, 16));
        body.addView(buildRoutingCard(), UiKit.fullWidth(this, 0, 16));
        body.addView(buildEngineCard(), UiKit.fullWidth(this, 0, 16));
        body.addView(buildUpdateCard(), UiKit.fullWidth(this, 0, 16));
        body.addView(buildDiagnosticsCard(), UiKit.fullWidth(this, 0, 16));
        body.addView(buildAboutCard(), UiKit.fullWidth());

        root.addView(body, UiKit.fullWidth());
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(currentTheme.background);
        scroll.addView(root);
        return scroll;
    }

    private LinearLayout buildLanguageCard() {
        LinearLayout card = UiKit.card(this, currentTheme);
        card.addView(
            UiKit.sectionTitle(this, currentTheme, getString(R.string.language_title)),
            UiKit.fullWidth()
        );
        card.addView(
            UiKit.bodyText(this, currentTheme, getString(R.string.language_subtitle)),
            UiKit.fullWidth(this, 4, 12)
        );

        AppLanguage target = LanguageSettings.getLanguage(this).other();
        // Labelled with the language it switches *to*, written in that
        // language: someone who can't read the current one still can read this.
        Button switchButton = UiKit.outlineButton(
            this, currentTheme, getString(R.string.language_switch_to, target.ownName));
        switchButton.setOnClickListener(v -> {
            AppLog.userAction(this, "Switched language to " + target.tag);
            LanguageSettings.setLanguage(this, target);
            recreate();
        });
        card.addView(switchButton, UiKit.fullWidth());
        return card;
    }

    private LinearLayout buildThemeCard() {
        LinearLayout card = UiKit.card(this, currentTheme);
        card.addView(UiKit.sectionTitle(this, currentTheme, getString(R.string.theme_title)), UiKit.fullWidth());
        card.addView(
            UiKit.bodyText(this, currentTheme, getString(R.string.theme_subtitle)),
            UiKit.fullWidth(this, 4, 12)
        );

        AppTheme[] themes = AppTheme.values();
        String[] labels = new String[themes.length];
        for (int index = 0; index < themes.length; index += 1) {
            labels[index] = getString(themes[index].labelResource);
        }
        Spinner themeSpinner = new Spinner(this);
        themeSpinner.setAdapter(UiKit.spinnerAdapter(this, currentTheme, labels));
        themeSpinner.setSelection(currentTheme.ordinal());
        themeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                AppTheme selected = themes[position];
                if (selected != currentTheme) {
                    AppLog.userAction(SettingsActivity.this, "Selected theme: " + selected);
                    ThemeSettings.setTheme(SettingsActivity.this, selected);
                    currentTheme = selected;
                    setContentView(buildContent());
                    updateRoutingUi();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        card.addView(themeSpinner, UiKit.fullWidth());
        return card;
    }

    private LinearLayout buildRoutingCard() {
        LinearLayout card = UiKit.card(this, currentTheme);
        card.addView(UiKit.sectionTitle(this, currentTheme, getString(R.string.routing_title)), UiKit.fullWidth());

        routingSwitch = UiKit.styledSwitch(this, currentTheme, getString(R.string.routing_selected_only));
        routingSwitch.setChecked(routingSettings.isSelectedOnly());
        routingSwitch.setOnCheckedChangeListener((button, checked) -> {
            AppLog.userAction(this, "Toggled route-selected-apps-only: " + checked);
            AppRoutingSettings.save(this, checked, routingSettings.getPackages());
            routingSettings = AppRoutingSettings.load(this);
            updateRoutingUi();
        });
        card.addView(routingSwitch, UiKit.fullWidth(this, 8, 0));

        routingSummary = UiKit.bodyText(this, currentTheme, "");
        card.addView(routingSummary, UiKit.fullWidth(this, 4, 8));

        chooseApps = UiKit.outlineButton(this, currentTheme, getString(R.string.routing_choose_apps));
        chooseApps.setOnClickListener(v -> {
            AppLog.userAction(this, "Tapped Choose apps");
            showAppSelectionDialog();
        });
        card.addView(chooseApps, UiKit.fullWidth());
        return card;
    }

    private LinearLayout buildEngineCard() {
        LinearLayout card = UiKit.card(this, currentTheme);
        card.addView(UiKit.sectionTitle(this, currentTheme, getString(R.string.engine_title)), UiKit.fullWidth());

        Switch quicSwitch = UiKit.styledSwitch(this, currentTheme, getString(R.string.block_quic));
        quicSwitch.setChecked(EngineSettings.isQuicBlocked(this));
        quicSwitch.setOnCheckedChangeListener((button, checked) -> {
            AppLog.userAction(this, "Toggled Block QUIC: " + checked);
            EngineSettings.setQuicBlocked(this, checked);
        });
        card.addView(quicSwitch, UiKit.fullWidth(this, 8, 0));
        return card;
    }

    private LinearLayout buildUpdateCard() {
        LinearLayout card = UiKit.card(this, currentTheme);
        card.addView(UiKit.sectionTitle(this, currentTheme, getString(R.string.update_title)), UiKit.fullWidth());
        TextView versionText = UiKit.bodyText(
            this,
            currentTheme,
            getString(R.string.update_current_version, UpdateManager.currentVersionName(this))
        );
        card.addView(versionText, UiKit.fullWidth(this, 4, 12));

        Button checkButton = UiKit.outlineButton(this, currentTheme, getString(R.string.update_check_now));
        checkButton.setOnClickListener(v -> {
            AppLog.userAction(this, "Tapped Check for updates");
            UpdateManager.checkManually(this);
        });
        card.addView(checkButton, UiKit.fullWidth());
        return card;
    }

    private LinearLayout buildDiagnosticsCard() {
        LinearLayout card = UiKit.card(this, currentTheme);
        card.addView(UiKit.sectionTitle(this, currentTheme, getString(R.string.diagnostics_log_title)), UiKit.fullWidth());
        card.addView(
            UiKit.bodyText(this, currentTheme, getString(R.string.diagnostics_log_subtitle)),
            UiKit.fullWidth(this, 4, 12)
        );

        Button viewButton = UiKit.outlineButton(this, currentTheme, getString(R.string.diagnostics_log_view));
        viewButton.setOnClickListener(v -> {
            AppLog.userAction(this, "Tapped View today's log");
            showLogDialog();
        });
        card.addView(viewButton, UiKit.fullWidth(this, 0, 12));

        Button shareButton =
            UiKit.outlineButton(this, currentTheme, getString(R.string.diagnostics_log_share));
        shareButton.setOnClickListener(v -> {
            AppLog.userAction(this, "Tapped Share log");
            shareLogFile();
        });
        card.addView(shareButton, UiKit.fullWidth());
        return card;
    }

    /**
     * Hands today's log to the system share sheet as a file attachment rather
     * than as text: a day's log runs to tens of kilobytes, which many share
     * targets truncate or refuse outright when it arrives as EXTRA_TEXT.
     */
    private void shareLogFile() {
        String content = AppLog.readToday(this);
        if (content.isEmpty()) {
            Toast.makeText(this, R.string.diagnostics_log_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = LogFileProvider.exportLog(this, content);
        if (uri == null) {
            Toast.makeText(this, R.string.diagnostics_log_share_failed, Toast.LENGTH_LONG).show();
            return;
        }

        Intent share = new Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT,
                getString(R.string.diagnostics_log_share_subject, AppLog.appVersion(this)))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, getString(R.string.diagnostics_log_share)));
    }

    private void showLogDialog() {
        String content = AppLog.readToday(this);
        String displayText = content.isEmpty() ? getString(R.string.diagnostics_log_empty) : content;

        TextView logText = new TextView(this);
        logText.setText(displayText);
        logText.setTextColor(currentTheme.textPrimary);
        logText.setTextSize(12);
        logText.setTypeface(android.graphics.Typeface.MONOSPACE);
        int pad = UiKit.dp(this, 12);
        logText.setPadding(pad, pad, pad, pad);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(currentTheme.cardBackground);
        scroll.addView(logText);

        new AlertDialog.Builder(this)
            .setTitle(R.string.diagnostics_log_title)
            .setView(scroll)
            .setNeutralButton(R.string.diagnostics_log_copy, (dialog, which) -> {
                AppLog.userAction(this, "Tapped Copy log");
                android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("zapret-log", content));
                    Toast.makeText(this, R.string.diagnostics_log_copied, Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton(R.string.diagnostics_log_clear, (dialog, which) -> {
                AppLog.clearToday(this);
                Toast.makeText(this, R.string.diagnostics_log_cleared, Toast.LENGTH_SHORT).show();
            })
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    private LinearLayout buildAboutCard() {
        LinearLayout card = UiKit.card(this, currentTheme);
        card.addView(UiKit.sectionTitle(this, currentTheme, getString(R.string.about_title)), UiKit.fullWidth());
        card.addView(
            UiKit.bodyText(this, currentTheme, getString(R.string.diagnostics, NativeZapretEngine.version())),
            UiKit.fullWidth(this, 4, 0)
        );
        return card;
    }

    private void updateRoutingUi() {
        if (routingSummary == null || routingSwitch == null || chooseApps == null) {
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
            Toast.makeText(this, R.string.state_no_apps_found, Toast.LENGTH_SHORT).show();
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
                AppLog.userAction(this, "Saved app selection: " + selectedPackages.size() + " app(s)");
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
}
