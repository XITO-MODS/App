package com.xito.aurorapanel;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    private EditText etLicenseKey;
    private TextView tvLoginStatus;
    private TextView tvDeviceIdInfo;
    private Button btnLoginVerify;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etLicenseKey = findViewById(R.id.etLicenseKey);
        tvLoginStatus = findViewById(R.id.tvLoginStatus);
        tvDeviceIdInfo = findViewById(R.id.tvDeviceIdInfo);
        btnLoginVerify = findViewById(R.id.btnLoginVerify);

        String deviceId = LicenseManager.getDeviceId(this);
        tvDeviceIdInfo.setText("ID Dispositivo: " + deviceId);

        String savedKey = LicenseManager.getSavedKey(this);
        if (savedKey != null && !savedKey.isEmpty()) {
            etLicenseKey.setText(savedKey);
            verifyKeyAsync(savedKey, true);
        }

        btnLoginVerify.setOnClickListener(v -> {
            String inputKey = etLicenseKey.getText().toString().trim();
            verifyKeyAsync(inputKey, false);
        });
    }

    private void verifyKeyAsync(String inputKey, boolean silentIfFail) {
        btnLoginVerify.setEnabled(false);
        tvLoginStatus.setText("Verificando key en el servidor (xito-mods)...");
        tvLoginStatus.setTextColor(0xFFFFD166);

        new Thread(() -> {
            LicenseManager.ValidationResult result = LicenseManager.validateAndActivateKeySync(LoginActivity.this, inputKey);
            runOnUiThread(() -> {
                btnLoginVerify.setEnabled(true);
                if (result.valid) {
                    tvLoginStatus.setText(result.message);
                    tvLoginStatus.setTextColor(0xFF00E676);
                    Toast.makeText(LoginActivity.this, result.message, Toast.LENGTH_SHORT).show();
                    openMainPanel();
                } else {
                    if (silentIfFail) {
                        LicenseManager.clearSession(LoginActivity.this);
                        tvLoginStatus.setText("Ingresa tu key para desbloquear el panel RGB");
                        tvLoginStatus.setTextColor(0xFFB9F6CA);
                    } else {
                        tvLoginStatus.setText(result.message);
                        tvLoginStatus.setTextColor(0xFFFF5252);
                        Toast.makeText(LoginActivity.this, result.message, Toast.LENGTH_LONG).show();
                    }
                }
            });
        }).start();
    }

    private void openMainPanel() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
