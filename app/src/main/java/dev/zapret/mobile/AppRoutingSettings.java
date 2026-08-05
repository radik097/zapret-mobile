package dev.zapret.mobile;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

final class AppRoutingSettings {
    private static final String PREFERENCES = "app_routing";
    private static final String KEY_SELECTED_ONLY = "selected_only";
    private static final String KEY_PACKAGES = "selected_packages";

    private AppRoutingSettings() {
    }

    static Snapshot load(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        Set<String> storedPackages = preferences.getStringSet(KEY_PACKAGES, Collections.emptySet());
        return new Snapshot(
            preferences.getBoolean(KEY_SELECTED_ONLY, false),
            normalizePackages(storedPackages, context.getPackageName())
        );
    }

    static void save(Context context, boolean selectedOnly, Set<String> packages) {
        Set<String> normalizedPackages = normalizePackages(packages, context.getPackageName());
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SELECTED_ONLY, selectedOnly)
            .putStringSet(KEY_PACKAGES, normalizedPackages)
            .apply();
    }

    private static Set<String> normalizePackages(Set<String> packages, String ownPackage) {
        TreeSet<String> normalized = new TreeSet<>();
        if (packages != null) {
            for (String packageName : packages) {
                if (packageName == null) {
                    continue;
                }
                String trimmed = packageName.trim();
                if (!trimmed.isEmpty() && !trimmed.equals(ownPackage)) {
                    normalized.add(trimmed);
                }
            }
        }
        return normalized;
    }

    static final class Snapshot {
        private final boolean selectedOnly;
        private final Set<String> packages;

        private Snapshot(boolean selectedOnly, Set<String> packages) {
            this.selectedOnly = selectedOnly;
            this.packages = Collections.unmodifiableSet(new LinkedHashSet<>(packages));
        }

        boolean isSelectedOnly() {
            return selectedOnly;
        }

        Set<String> getPackages() {
            return packages;
        }
    }
}
