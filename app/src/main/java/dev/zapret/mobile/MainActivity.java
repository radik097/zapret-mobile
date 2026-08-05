package dev.zapret.mobile;

import android.app.Activity;
import android.content.Intent;
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
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContent());
        updateStatus(getString(R.string.state_stopped));
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
