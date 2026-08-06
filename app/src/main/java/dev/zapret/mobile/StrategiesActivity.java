package dev.zapret.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public final class StrategiesActivity extends Activity {
    // Flowseal first: it is the default/primary profile (fake decoy + split).
    private static final StrategyProfile[] BUILT_IN_PROFILES = {
        StrategyProfile.FLOWSEAL,
        StrategyProfile.MULTISPLIT,
        StrategyProfile.ZAPTRET2,
        StrategyProfile.AGGRESSIVE,
        StrategyProfile.BALANCED,
        StrategyProfile.COMPATIBLE
    };

    private AppTheme currentTheme;
    private StrategyRepository strategyRepository;
    private LinearLayout packsContainer;
    private LinearLayout autoTestResultsContainer;
    private TextView activeStrategyText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentTheme = ThemeSettings.getTheme(this);
        strategyRepository = new StrategyRepository(this);
        setContentView(buildContent());
        renderPacks(strategyRepository.getCachedPacks());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateActiveStrategyText();
    }

    private ScrollView buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(currentTheme.background);
        root.addView(
            UiKit.headerBanner(this, currentTheme, getString(R.string.nav_strategies), null),
            UiKit.fullWidth()
        );

        int pad = UiKit.dp(this, 24);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(pad, pad, pad, pad);

        LinearLayout activeCard = UiKit.card(this, currentTheme);
        activeCard.addView(
            UiKit.sectionTitle(this, currentTheme, getString(R.string.strategies_active_title)),
            UiKit.fullWidth()
        );
        activeStrategyText = UiKit.bodyText(this, currentTheme, "");
        activeCard.addView(activeStrategyText, UiKit.fullWidth(this, 4, 0));
        body.addView(activeCard, UiKit.fullWidth(this, 0, 16));

        body.addView(buildAutoTestCard(), UiKit.fullWidth(this, 0, 16));
        body.addView(buildBuiltInCard(), UiKit.fullWidth(this, 0, 16));
        body.addView(buildTargetingCard(), UiKit.fullWidth(this, 0, 16));
        body.addView(buildDownloadedCard(), UiKit.fullWidth());

        root.addView(body, UiKit.fullWidth());
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(currentTheme.background);
        scroll.addView(root);
        return scroll;
    }

    private LinearLayout buildAutoTestCard() {
        LinearLayout card = UiKit.card(this, currentTheme);
        card.addView(
            UiKit.sectionTitle(this, currentTheme, getString(R.string.strategies_autotest_title)),
            UiKit.fullWidth()
        );
        card.addView(
            UiKit.bodyText(this, currentTheme, getString(R.string.strategies_autotest_subtitle)),
            UiKit.fullWidth(this, 4, 12)
        );

        Button runButton = UiKit.outlineButton(this, currentTheme, getString(R.string.strategies_autotest_run));
        runButton.setOnClickListener(v -> {
            AppLog.userAction(this, "Tapped Run auto-test");
            runAutoTest(runButton);
        });
        card.addView(runButton, UiKit.fullWidth(this, 0, 12));

        autoTestResultsContainer = new LinearLayout(this);
        autoTestResultsContainer.setOrientation(LinearLayout.VERTICAL);
        card.addView(autoTestResultsContainer, UiKit.fullWidth());
        return card;
    }

    private void runAutoTest(Button runButton) {
        ZapretVpnService service = ZapretVpnService.getRunningInstance();
        if (service == null) {
            Toast.makeText(this, R.string.strategies_autotest_vpn_required, Toast.LENGTH_LONG).show();
            return;
        }
        runButton.setEnabled(false);
        autoTestResultsContainer.removeAllViews();
        Toast.makeText(this, R.string.strategies_autotest_running, Toast.LENGTH_SHORT).show();

        StrategyAutoTester.runAll(service, new StrategyAutoTester.Callback() {
            @Override
            public void onProfileStarted(StrategyProfile profile) {
                Toast.makeText(
                    StrategiesActivity.this,
                    getString(R.string.strategies_autotest_testing, getString(profile.getLabelResource())),
                    Toast.LENGTH_SHORT
                ).show();
            }

            @Override
            public void onProfileFinished(StrategyAutoTester.ProfileResult result) {
                addAutoTestResultRow(result);
            }

            @Override
            public void onComplete(List<StrategyAutoTester.ProfileResult> results) {
                runButton.setEnabled(true);
                Toast.makeText(StrategiesActivity.this, R.string.strategies_autotest_done, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addAutoTestResultRow(StrategyAutoTester.ProfileResult result) {
        if (autoTestResultsContainer == null) {
            return;
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        int successCount = result.successCount();
        int total = result.domainResults.size();
        long totalMs = 0;
        for (StrategyAutoTester.DomainResult domainResult : result.domainResults) {
            totalMs += domainResult.elapsedMs;
        }
        long avgMs = total > 0 ? totalMs / total : 0;

        TextView label = new TextView(this);
        label.setText(getString(
            R.string.strategies_autotest_row_format,
            getString(result.profile.getLabelResource()),
            successCount,
            total,
            avgMs
        ));
        label.setTextColor(result.allSucceeded() ? currentTheme.accent : currentTheme.textPrimary);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
            0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        row.addView(label, labelParams);

        Button applyButton = UiKit.outlineButton(this, currentTheme, getString(R.string.strategies_use));
        applyButton.setEnabled(successCount > 0);
        applyButton.setOnClickListener(v -> {
            AppLog.userAction(this, "Applied auto-test result: " + result.profile);
            EngineSettings.setStrategyProfile(this, result.profile);
            updateActiveStrategyText();
            Toast.makeText(
                this,
                getString(R.string.strategies_selected, getString(result.profile.getLabelResource())),
                Toast.LENGTH_SHORT
            ).show();
        });
        row.addView(applyButton);

        autoTestResultsContainer.addView(row, UiKit.fullWidth(this, 6, 6));
    }

    private LinearLayout buildBuiltInCard() {
        LinearLayout card = UiKit.card(this, currentTheme);
        card.addView(
            UiKit.sectionTitle(this, currentTheme, getString(R.string.strategies_builtin_title)),
            UiKit.fullWidth()
        );
        card.addView(
            UiKit.bodyText(this, currentTheme, getString(R.string.strategies_builtin_subtitle)),
            UiKit.fullWidth(this, 4, 12)
        );

        String[] labels = new String[BUILT_IN_PROFILES.length];
        for (int index = 0; index < BUILT_IN_PROFILES.length; index += 1) {
            labels[index] = getString(BUILT_IN_PROFILES[index].getLabelResource());
        }
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(UiKit.spinnerAdapter(this, currentTheme, labels));

        StrategyProfile current = EngineSettings.getStrategyProfile(this);
        int selectedIndex = 0;
        for (int index = 0; index < BUILT_IN_PROFILES.length; index += 1) {
            if (BUILT_IN_PROFILES[index] == current) {
                selectedIndex = index;
                break;
            }
        }
        spinner.setSelection(selectedIndex);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private boolean userDriven;

            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                // Spinner fires this once automatically on initial layout, not just on
                // user interaction; only log/apply from the second call onward.
                if (userDriven) {
                    AppLog.userAction(StrategiesActivity.this, "Selected built-in strategy: " + BUILT_IN_PROFILES[position]);
                    EngineSettings.setStrategyProfile(StrategiesActivity.this, BUILT_IN_PROFILES[position]);
                    updateActiveStrategyText();
                }
                userDriven = true;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        card.addView(spinner, UiKit.fullWidth());

        card.addView(
            UiKit.bodyText(this, currentTheme, getString(R.string.strategies_fake_decoy_warning)),
            UiKit.fullWidth(this, 16, 4)
        );
        Switch fakeDecoySwitch = UiKit.styledSwitch(this, currentTheme, getString(R.string.strategies_fake_decoy_enable));
        fakeDecoySwitch.setChecked(EngineSettings.isFakeDecoyEnabled(this));
        fakeDecoySwitch.setOnCheckedChangeListener((button, checked) -> {
            AppLog.userAction(this, "Toggled fake decoy: " + checked);
            EngineSettings.setFakeDecoyEnabled(this, checked);
        });
        card.addView(fakeDecoySwitch, UiKit.fullWidth(this, 4, 4));

        card.addView(
            UiKit.bodyText(this, currentTheme, getString(R.string.strategies_fake_ttl_label)),
            UiKit.fullWidth(this, 8, 4)
        );
        EditText ttlInput = UiKit.editText(this, currentTheme);
        ttlInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        ttlInput.setText(String.valueOf(EngineSettings.getFakeTtl(this)));
        card.addView(ttlInput, UiKit.fullWidth());
        Button ttlSaveButton = UiKit.outlineButton(this, currentTheme, getString(R.string.strategies_fake_ttl_save));
        ttlSaveButton.setOnClickListener(v -> {
            AppLog.userAction(this, "Tapped Save TTL: " + ttlInput.getText());
            try {
                int ttl = Integer.parseInt(ttlInput.getText().toString().trim());
                if (ttl < 1 || ttl > 64) {
                    throw new NumberFormatException();
                }
                EngineSettings.setFakeTtl(this, ttl);
                Toast.makeText(this, R.string.strategies_fake_ttl_saved, Toast.LENGTH_SHORT).show();
            } catch (NumberFormatException error) {
                Toast.makeText(this, R.string.strategies_fake_ttl_invalid, Toast.LENGTH_SHORT).show();
            }
        });
        card.addView(ttlSaveButton, UiKit.fullWidth(this, 4, 0));
        return card;
    }

    private LinearLayout buildTargetingCard() {
        LinearLayout card = UiKit.card(this, currentTheme);
        card.addView(
            UiKit.sectionTitle(this, currentTheme, getString(R.string.strategies_targeting_title)),
            UiKit.fullWidth()
        );
        card.addView(
            UiKit.bodyText(this, currentTheme, getString(R.string.strategies_targeting_subtitle)),
            UiKit.fullWidth(this, 4, 12)
        );

        Switch hostlistSwitch = UiKit.styledSwitch(this, currentTheme, getString(R.string.strategies_targeting_enable));
        hostlistSwitch.setChecked(EngineSettings.isHostlistOnly(this));
        card.addView(hostlistSwitch, UiKit.fullWidth(this, 0, 8));

        EditText domainsInput = UiKit.editText(this, currentTheme);
        domainsInput.setText(EngineSettings.getHostlistDomains(this));
        domainsInput.setMinLines(2);
        domainsInput.setHint(R.string.strategies_targeting_hint);
        card.addView(domainsInput, UiKit.fullWidth(this, 0, 8));

        Button saveButton = UiKit.outlineButton(this, currentTheme, getString(R.string.strategies_targeting_save));
        saveButton.setOnClickListener(v -> {
            AppLog.userAction(this, "Tapped Save targeting: enabled=" + hostlistSwitch.isChecked());
            EngineSettings.setHostlistTargeting(this, hostlistSwitch.isChecked(), domainsInput.getText().toString());
            Toast.makeText(this, R.string.strategies_targeting_saved, Toast.LENGTH_SHORT).show();
        });
        card.addView(saveButton, UiKit.fullWidth(this, 4, 0));
        return card;
    }

    private LinearLayout buildDownloadedCard() {
        LinearLayout card = UiKit.card(this, currentTheme);
        card.addView(
            UiKit.sectionTitle(this, currentTheme, getString(R.string.strategies_downloaded_title)),
            UiKit.fullWidth()
        );
        card.addView(
            UiKit.bodyText(this, currentTheme, getString(R.string.strategies_downloaded_subtitle)),
            UiKit.fullWidth(this, 4, 12)
        );

        Button refreshButton = UiKit.outlineButton(this, currentTheme, getString(R.string.strategies_refresh));
        refreshButton.setOnClickListener(v -> {
            AppLog.userAction(this, "Tapped Refresh strategy packs");
            refreshPacks();
        });
        card.addView(refreshButton, UiKit.fullWidth(this, 0, 12));

        packsContainer = new LinearLayout(this);
        packsContainer.setOrientation(LinearLayout.VERTICAL);
        card.addView(packsContainer, UiKit.fullWidth());
        return card;
    }

    private void refreshPacks() {
        Toast.makeText(this, R.string.strategies_refreshing, Toast.LENGTH_SHORT).show();
        strategyRepository.fetchAvailablePacks(new StrategyRepository.Callback() {
            @Override
            public void onSuccess(List<StrategyPack> packs) {
                renderPacks(packs);
                Toast.makeText(
                    StrategiesActivity.this,
                    getString(R.string.strategies_refresh_success, packs.size()),
                    Toast.LENGTH_SHORT
                ).show();
            }

            @Override
            public void onError(Exception error) {
                Toast.makeText(
                    StrategiesActivity.this,
                    getString(R.string.strategies_refresh_error),
                    Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    private void renderPacks(List<StrategyPack> packs) {
        if (packsContainer == null) {
            return;
        }
        packsContainer.removeAllViews();
        if (packs.isEmpty()) {
            packsContainer.addView(
                UiKit.bodyText(this, currentTheme, getString(R.string.strategies_none_cached)),
                UiKit.fullWidth()
            );
            return;
        }

        for (StrategyPack pack : packs) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView label = new TextView(this);
            label.setText(pack.name);
            label.setTextColor(currentTheme.textPrimary);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            );
            row.addView(label, labelParams);

            Button useButton = UiKit.outlineButton(this, currentTheme, getString(R.string.strategies_use));
            useButton.setOnClickListener(v -> {
                AppLog.userAction(StrategiesActivity.this, "Selected downloaded strategy pack: " + pack.name);
                strategyRepository.selectPack(StrategiesActivity.this, pack);
                updateActiveStrategyText();
                Toast.makeText(
                    StrategiesActivity.this,
                    getString(R.string.strategies_selected, pack.name),
                    Toast.LENGTH_SHORT
                ).show();
            });
            row.addView(useButton);

            packsContainer.addView(row, UiKit.fullWidth(this, 6, 6));
        }
    }

    private void updateActiveStrategyText() {
        if (activeStrategyText == null) {
            return;
        }
        StrategyProfile profile = EngineSettings.getStrategyProfile(this);
        String label = profile == StrategyProfile.CUSTOM
            ? EngineSettings.getCustomStrategyName(this)
            : getString(profile.getLabelResource());
        activeStrategyText.setText(getString(R.string.strategies_active_format, label));
    }
}
