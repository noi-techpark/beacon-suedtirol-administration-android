package it.bz.beacon.adminapp.ui.detail;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;
import com.kontakt.sdk.android.ble.connection.ErrorCause;
import com.kontakt.sdk.android.ble.connection.KontaktDeviceConnection;
import com.kontakt.sdk.android.ble.connection.KontaktDeviceConnectionFactory;
import com.kontakt.sdk.android.ble.connection.OnServiceReadyListener;
import com.kontakt.sdk.android.ble.connection.SyncableKontaktDeviceConnection;
import com.kontakt.sdk.android.ble.connection.WriteListener;
import com.kontakt.sdk.android.ble.manager.ProximityManager;
import com.kontakt.sdk.android.ble.manager.ProximityManagerFactory;
import com.kontakt.sdk.android.ble.manager.listeners.SecureProfileListener;
import com.kontakt.sdk.android.ble.manager.listeners.simple.SimpleSecureProfileListener;
import com.kontakt.sdk.android.cloud.KontaktCloud;
import com.kontakt.sdk.android.cloud.KontaktCloudFactory;
import com.kontakt.sdk.android.cloud.response.CloudCallback;
import com.kontakt.sdk.android.cloud.response.CloudError;
import com.kontakt.sdk.android.cloud.response.CloudHeaders;
import com.kontakt.sdk.android.cloud.response.paginated.Configs;
import com.kontakt.sdk.android.common.KontaktSDK;
import com.kontakt.sdk.android.common.model.Config;
import com.kontakt.sdk.android.common.profile.ISecureProfile;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import butterknife.BindView;
import butterknife.ButterKnife;
import io.swagger.client.model.PendingConfiguration;
import it.bz.beacon.adminapp.AdminApplication;
import it.bz.beacon.adminapp.R;
import it.bz.beacon.adminapp.data.entity.Beacon;
import it.bz.beacon.adminapp.data.viewmodel.BeaconViewModel;
import it.bz.beacon.adminapp.ui.BaseActivity;

import static it.bz.beacon.adminapp.ui.detail.DetailActivity.EXTRA_BEACON_ID;
import static it.bz.beacon.adminapp.ui.detail.DetailActivity.EXTRA_BEACON_NAME;

public class PendingConfigurationActivity extends BaseActivity {

    private static final int LOCATION_PERMISSION_REQUEST = 1;

    @BindView(R.id.fab_apply_now)
    protected MaterialButton btnApplyNow;

    @BindView(R.id.containerView)
    protected LinearLayout containerView;

    private long beaconId;
    private String beaconName;
    private Beacon beacon;
    private boolean isPendingConfigEmpty = true;

    private BeaconViewModel beaconViewModel;

    private SyncableKontaktDeviceConnection syncableKontaktDeviceConnection;
    private KontaktCloud kontaktCloud;
    private KontaktDeviceConnection kontaktDeviceConnection;
    private ProximityManager proximityManager;
    private ISecureProfile secureProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ButterKnife.bind(this);
        setSupportActionBar(toolbar);

        if (getIntent() != null) {
            beaconName = getIntent().getStringExtra(EXTRA_BEACON_NAME);
            beaconId = getIntent().getLongExtra(EXTRA_BEACON_ID, -1L);
        }
        beaconViewModel = ViewModelProviders.of(this).get(BeaconViewModel.class);

        loadBeacon();
        initializeKontakt();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startScanningIfLocationPermissionGranted();
        setUpToolbar();
    }

    @Override
    public void onPause() {
        super.onPause();
        proximityManager.stopScanning();
    }

    private void initializeKontakt() {
        KontaktSDK.initialize(getString(R.string.apiKey));
        kontaktCloud = KontaktCloudFactory.create();
        proximityManager = ProximityManagerFactory.create(this);
        proximityManager.setSecureProfileListener(createSecureProfileListener());
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_pending_configuration;
    }

    private void loadBeacon() {
//        showProgress(getString(R.string.loading));

        beaconViewModel.getById(beaconId).observe(this, new Observer<Beacon>() {
            @Override
            public void onChanged(@Nullable Beacon changedBeacon) {
                beacon = changedBeacon;
                beaconName = changedBeacon.getName();
                setUpToolbar();
                if (!TextUtils.isEmpty(changedBeacon.getPendingConfiguration())) {
                    showDifferences(changedBeacon, (new Gson()).fromJson(changedBeacon.getPendingConfiguration(), PendingConfiguration.class));
                }
                else {
                    isPendingConfigEmpty = true;
                }
            }
        });
    }

    private void showDifferences(Beacon beacon, PendingConfiguration pendingConfiguration) {
        if (containerView.getChildCount() > 2) {
            containerView.removeViews(2, containerView.getChildCount() - 2);
        }

        LinearLayout section = null;
        section = addTextDifference(String.valueOf(beacon.getTxPower()), String.valueOf(pendingConfiguration.getTxPower()), getString(R.string.details_config_signalstrength), section);
        section = addTextDifference(String.valueOf(beacon.getInterval()), String.valueOf(pendingConfiguration.getInterval()), getString(R.string.details_config_signalinterval), section);
        section = addBooleanDifference(beacon.getTelemetry(), pendingConfiguration.isTelemetry(), getString(R.string.details_info_telemetry), section);
        addSection(section, getString(R.string.details_info));

        section = null;
        section = addBooleanDifference(beacon.isIBeacon(), pendingConfiguration.isIBeacon(), getString(R.string.details_ibeacon), section);
        section = addTextDifference(String.valueOf(beacon.getUuid()), String.valueOf(pendingConfiguration.getUuid()), "UUID", section);
        section = addTextDifference(String.valueOf(beacon.getMajor()), String.valueOf(pendingConfiguration.getMajor()), getString(R.string.details_config_major), section);
        section = addTextDifference(String.valueOf(beacon.getMinor()), String.valueOf(pendingConfiguration.getMinor()), getString(R.string.details_config_minor), section);
        addSection(section, getString(R.string.details_ibeacon));

        section = null;
        section = addBooleanDifference(beacon.isEddystoneUid(), pendingConfiguration.isEddystoneUid(), getString(R.string.details_eddystone_uid), section);
        section = addTextDifference(String.valueOf(beacon.getNamespace()), String.valueOf(pendingConfiguration.getNamespace()), getString(R.string.details_config_namespace), section);
        section = addTextDifference(String.valueOf(beacon.getInstanceId()), String.valueOf(pendingConfiguration.getInstanceId()), getString(R.string.details_config_instanceid), section);
        section = addBooleanDifference(beacon.isEddystoneUrl(), pendingConfiguration.isEddystoneUrl(), getString(R.string.details_eddystone_url), section);
        section = addTextDifference(String.valueOf(beacon.getUrl()), String.valueOf(pendingConfiguration.getUrl()), getString(R.string.details_config_url), section);
        section = addBooleanDifference(beacon.isEddystoneEid(), pendingConfiguration.isEddystoneEid(), getString(R.string.details_eddystone_eid), section);
        section = addBooleanDifference(beacon.isEddystoneEtlm(), pendingConfiguration.isEddystoneEtlm(), getString(R.string.details_eddystone_etlm), section);
        section = addBooleanDifference(beacon.isEddystoneTlm(), pendingConfiguration.isEddystoneTlm(), getString(R.string.details_eddystone_tlm), section);
        addSection(section, getString(R.string.details_eddystone));

        if (containerView.getChildCount() > 2) {
            containerView.removeViews(1, 1);
        }
    }

    private void setSectionTitle(LinearLayout section, String title) {
        TextView textView = section.findViewById(R.id.section_title);
        textView.setText(title);
    }

    private LinearLayout addTextDifference(String oldValue, String newValue, String title, LinearLayout parent) {
        View differenceText = null;
        if (!oldValue.equalsIgnoreCase(newValue)) {
            Log.d(AdminApplication.LOG_TAG, "found difference for " + title);
            if (parent == null) {
                parent = (LinearLayout) getLayoutInflater().inflate(R.layout.section_pending_config, null);
            }
            differenceText = getLayoutInflater().inflate(R.layout.pending_config_text, null);
            TextView textView = differenceText.findViewById(R.id.pending_config_title);
            textView.setText(title);
            textView = differenceText.findViewById(R.id.pending_config_value_old);
            textView.setText(oldValue);
            textView = differenceText.findViewById(R.id.pending_config_value_new);
            textView.setText(newValue);
            parent.addView(differenceText);
        }
        return parent;
    }

    private LinearLayout addBooleanDifference(boolean oldValue, boolean newValue, String title, LinearLayout parent) {
        View differenceBoolean = null;
        if (oldValue != newValue) {
            Log.d(AdminApplication.LOG_TAG, "found difference for " + title);
            if (parent == null) {
                parent = (LinearLayout) getLayoutInflater().inflate(R.layout.section_pending_config, null);
            }
            differenceBoolean = getLayoutInflater().inflate(R.layout.pending_config_boolean, null);
            TextView textView = differenceBoolean.findViewById(R.id.pending_config_title);
            textView.setText(title);
            AppCompatCheckBox checkBox = differenceBoolean.findViewById(R.id.pending_config_value_old);
            checkBox.setChecked(oldValue);
            checkBox = differenceBoolean.findViewById(R.id.pending_config_value_new);
            checkBox.setChecked(newValue);
            parent.addView(differenceBoolean);
        }
        return parent;
    }

    private void addSection(LinearLayout section, String title) {
        if (section != null) {
            setSectionTitle(section, title);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            layoutParams.setMargins(0, (int) getResources().getDimension(R.dimen.app_default_margin), 0, 0);
            containerView.addView(section, layoutParams);
            isPendingConfigEmpty = false;
        }
    }

    private SecureProfileListener createSecureProfileListener() {
        return new SimpleSecureProfileListener() {
            @Override
            public void onProfileDiscovered(final ISecureProfile profile) {
                updateBeaconNearby(profile);
                super.onProfileDiscovered(profile);
            }

            @Override
            public void onProfilesUpdated(List<ISecureProfile> profiles) {
                for (ISecureProfile profile : profiles) {
                    updateBeaconNearby(profile);
                }
                super.onProfilesUpdated(profiles);
            }

            @Override
            public void onProfileLost(ISecureProfile profile) {
                if (beacon.getManufacturerId().equals(profile.getUniqueId())) {
                    secureProfile = null;
                    btnApplyNow.setEnabled(false);
                }
                super.onProfileLost(profile);
            }

            private void updateBeaconNearby(ISecureProfile profile) {
                if (beacon.getManufacturerId().equals(profile.getUniqueId())) {
                    secureProfile = profile;
                    if ((beacon.getStatus().equals(Beacon.STATUS_CONFIGURATION_PENDING)) && !isPendingConfigEmpty) {
                        btnApplyNow.setEnabled(true);
                    }
                }
            }
        };
    }

    private void setUpToolbar() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_arrow_back);
        }
        if (!TextUtils.isEmpty(beaconName)) {
            toolbar.setTitle(beaconName);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case android.R.id.home:
                finish();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void sendToCloud(Config secureConfig, WriteListener.WriteResponse writeResponse) {
        secureConfig.applySecureResponse(writeResponse.getExtra(), writeResponse.getUnixTimestamp());
        kontaktCloud.devices()
                .applySecureConfigs(secureConfig)
                .execute(new CloudCallback<Configs>() {
                    @Override
                    public void onSuccess(Configs response, CloudHeaders headers) {
                        if (response != null && response.getContent() != null && response.getContent().size() > 0) {

                        }
                        Log.d(AdminApplication.LOG_TAG, "Successfully applied config");
                    }

                    @Override
                    public void onError(CloudError error) {
                        Log.e(AdminApplication.LOG_TAG, "config api error");
                    }
                });
    }

    private void sendToBeacon(final ISecureProfile profile) {
        kontaktCloud.configs().secure().withIds(profile.getUniqueId()).execute(new CloudCallback<Configs>() {
            @Override
            public void onSuccess(Configs response, CloudHeaders headers) {
                if (response != null && response.getContent() != null && response.getContent().size() > 0) {
                    final Config config = response.getContent().get(0);
                    Log.d(AdminApplication.LOG_TAG, "Config for beacon received: " + profile.getUniqueId());

                    kontaktDeviceConnection = KontaktDeviceConnectionFactory.create(PendingConfigurationActivity.this, profile, new KontaktDeviceConnection.ConnectionListener() {
                        @Override
                        public void onConnectionOpened() {
                            Log.d(AdminApplication.LOG_TAG, "Connection opened");
                        }

                        @Override
                        public void onConnected() {
                            kontaktDeviceConnection.applySecureConfig(config.getSecureRequest(), new WriteListener() {
                                @Override
                                public void onWriteSuccess(WriteListener.WriteResponse response) {
                                    sendToCloud(config, response);
                                    disconnect();
                                    Log.d(AdminApplication.LOG_TAG, "Written");
                                }

                                @Override
                                public void onWriteFailure(ErrorCause cause) {
                                    Log.d(AdminApplication.LOG_TAG, "Write failure");
                                    disconnect();
                                }
                            });
                        }

                        @Override
                        public void onErrorOccured(int errorCode) {
                            Log.d(AdminApplication.LOG_TAG, "Connection failure");
                        }

                        @Override
                        public void onDisconnected() {
                            Log.d(AdminApplication.LOG_TAG, "Disconnect");
                        }
                    });
                    kontaktDeviceConnection.connect();
                }
            }

            @Override
            public void onError(CloudError error) {

            }
        });
    }


    private void startScanning() {
        proximityManager.connect(new OnServiceReadyListener() {
            @Override
            public void onServiceReady() {
                proximityManager.startScanning();
            }
        });
    }

    private void startScanningIfLocationPermissionGranted() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                AlertDialog dialog = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.AlertDialogCustom))
                        .setTitle(getString(R.string.location_permission_title))
                        .setMessage(getString(R.string.location_permission_message))
                        .setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.dismiss();
                                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                        LOCATION_PERMISSION_REQUEST);
                            }
                        })
                        .create();
                dialog.show();
            }
            else {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        LOCATION_PERMISSION_REQUEST);
            }
        }
        else {
            startScanning();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case LOCATION_PERMISSION_REQUEST:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startScanning();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                break;
        }
    }

    private void disconnect() {
        if (kontaktDeviceConnection != null) {
            kontaktDeviceConnection.close();
            kontaktDeviceConnection = null;
        }
    }
}