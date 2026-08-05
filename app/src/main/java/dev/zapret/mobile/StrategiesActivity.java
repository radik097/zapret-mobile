package dev.zapret.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public final class StrategiesActivity extends Activity {
    private static final StrategyProfile[] BUILT_IN_PROFILES = {
        StrategyProfile.COMPATIBLE,
        StrategyProfile.BALANCED,
        StrategyProfile.AGGRESSIVE,
        StrategyProfile.ZAPTRET2
    };

    private AppTheme currentTheme;
    private StrategyRepository strategyRepository;
    private LinearLayout packsContainer;
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

        body.addView(buildBuiltInCard(), UiKit.fullWidth(this, 0, 16));
        body.addView(buildDownloadedCard(), UiKit.fullWidth());

        root.addView(body, UiKit.fullWidth());
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(currentTheme.background);
        scroll.addView(root);
        return scroll;
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
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

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
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                EngineSettings.setStrategyProfile(StrategiesActivity.this, BUILT_IN_PROFILES[position]);
                updateActiveStrategyText();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        card.addView(spinner, UiKit.fullWidth());
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
        refreshButton.setOnClickListener(v -> refreshPacks());
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
