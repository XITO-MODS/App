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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etLicenseKey = findViewById(R.id.etLicenseKey);
        tvLoginStatus = findViewById(R.id.tvLoginStatus);
        tvDeviceIdInfo = findViewById(R.id.tvDeviceIdInfo);
        Button btnLoginVerify = findViewById(R.id.btnLoginVerify);

        String deviceId = LicenseManager.getDeviceId(this);
        tvDeviceIdInfo.setText("ID Dispositivo: " + deviceId);

        // Si ya tiene una sesión activa y vigente (no vencida), entra directo al panel
        LicenseManager.ValidationResult current = LicenseManager.checkCurrentSession(this);
        if (current.valid) {
            openMainPanel();
            return;
        }

        btnLoginVerify.setOnClickListener(v -> {
            String inputKey = etLicenseKey.getText().toString().trim();
            LicenseManager.ValidationResult result = LicenseManager.validateAndActivateKey(this, inputKey);
            if (result.valid) {
                tvLoginStatus.setText(result.message);
                tvLoginStatus.setTextColor(0xFF00E676);
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show();
                openMainPanel();
            } else {
                tvLoginStatus.setText(result.message);
                tvLoginStatus.setTextColor(0xFFFF5252);
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void openMainPanel() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
