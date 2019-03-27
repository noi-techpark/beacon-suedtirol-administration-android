package it.bz.beacon.adminapp.ui.main.beacon;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.kontakt.sdk.android.ble.connection.OnServiceReadyListener;
import com.kontakt.sdk.android.ble.manager.ProximityManager;
import com.kontakt.sdk.android.ble.manager.ProximityManagerFactory;
import com.kontakt.sdk.android.ble.manager.listeners.SecureProfileListener;
import com.kontakt.sdk.android.ble.manager.listeners.simple.SimpleSecureProfileListener;
import com.kontakt.sdk.android.common.KontaktSDK;
import com.kontakt.sdk.android.common.profile.ISecureProfile;
import com.squareup.otto.Subscribe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import it.bz.beacon.adminapp.R;
import it.bz.beacon.adminapp.data.entity.BeaconMinimal;
import it.bz.beacon.adminapp.data.event.LoadBeaconMinimalEvent;
import it.bz.beacon.adminapp.data.viewmodel.BeaconViewModel;
import it.bz.beacon.adminapp.eventbus.PubSub;
import it.bz.beacon.adminapp.eventbus.StatusFilterEvent;
import it.bz.beacon.beaconsuedtirolsdk.swagger.client.ApiCallback;
import it.bz.beacon.beaconsuedtirolsdk.swagger.client.ApiException;
import it.bz.beacon.beaconsuedtirolsdk.swagger.client.api.TrustedBeaconControllerApi;
import it.bz.beacon.beaconsuedtirolsdk.swagger.client.model.Beacon;
import it.bz.beacon.beaconsuedtirolsdk.swagger.client.model.BeaconBatteryLevelUpdate;

public class NearbyBeaconsFragment extends BaseBeaconsFragment {

    private static final int LOCATION_PERMISSION_REQUEST = 1;

    private ProximityManager proximityManager;
    private BeaconViewModel beaconViewModel;
    private MutableLiveData<List<BeaconMinimal>> nearbyBeacons;
    private TrustedBeaconControllerApi trustedApi;

    public NearbyBeaconsFragment() {
        // Required empty public constructor
    }

    public static NearbyBeaconsFragment newInstance(String statusFilter) {
        NearbyBeaconsFragment fragment = new NearbyBeaconsFragment();
        fragment.prepareStatusFilter(statusFilter);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        trustedApi = new TrustedBeaconControllerApi();
        trustedApi.getApiClient().setUsername(getString(R.string.trustedApiUser));
        trustedApi.getApiClient().setPassword(getString(R.string.trustedApiPassword));
        beaconViewModel = ViewModelProviders.of(this).get(BeaconViewModel.class);
        KontaktSDK.initialize(getString(R.string.apiKey));
        proximityManager = ProximityManagerFactory.create(getContext());
        proximityManager.setSecureProfileListener(createSecureProfileListener());
    }

    private void startScanningIfLocationPermissionGranted() {
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION)) {
                AlertDialog dialog = new AlertDialog.Builder(new ContextThemeWrapper(getContext(), R.style.AlertDialogCustom))
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
        }
    }

    @Override
    protected void getBeacons(Observer<List<BeaconMinimal>> observer) {
        nearbyBeacons = new MutableLiveData<>();
        nearbyBeacons.observe(this, observer);
    }

    @Subscribe
    public void onStatusFilterChanged(StatusFilterEvent event) {
        setStatusFilter(event.getStatus());
    }

    private void startScanning() {
        proximityManager.connect(new OnServiceReadyListener() {
            @Override
            public void onServiceReady() {
                proximityManager.startScanning();
            }
        });
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.list, menu);
        MenuItem search = menu.findItem(R.id.menu_search);
        SearchView searchView = (SearchView) search.getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                setSearchFilter(s);
                return false;
            }
        });
    }

    private boolean isBeaconInList(@NonNull List<BeaconMinimal> list, @NonNull BeaconMinimal beacon) {
        for (BeaconMinimal beaconMinimal : list) {
            if (beaconMinimal.getId().equals(beacon.getId())) {
                return true;
            }
        }
        return false;
    }

    private List<BeaconMinimal> updateBeaconInList(List<BeaconMinimal> list, BeaconMinimal freshBeacon) {
        for (BeaconMinimal beacon : list) {
            if (freshBeacon.getId().equals(beacon.getId())) {
                beacon.setManufacturerId(freshBeacon.getManufacturerId());
                beacon.setName(freshBeacon.getName());
                beacon.setMajor(freshBeacon.getMajor());
                beacon.setMinor(freshBeacon.getMinor());
                beacon.setBatteryLevel(freshBeacon.getBatteryLevel());
                beacon.setStatus(freshBeacon.getStatus());
                beacon.setLat(freshBeacon.getLat());
                beacon.setLng(freshBeacon.getLng());
                beacon.setRssi(freshBeacon.getRssi());
            }
        }
        Collections.sort(list, new Comparator<BeaconMinimal>() {
            public int compare(BeaconMinimal obj1, BeaconMinimal obj2) {
                if ((obj1 != null) && (obj2 != null) && (obj1.getRssi() != null) && (obj2.getRssi() != null)) {
                    return obj2.getRssi() - obj1.getRssi();
                }
                else {
                    return 0;
                }
            }
        });
        return list;
    }

    private List<BeaconMinimal> addBeaconToList(List<BeaconMinimal> list, BeaconMinimal newBeacon) {
        list.add(newBeacon);
        Collections.sort(list, new Comparator<BeaconMinimal>() {
            public int compare(BeaconMinimal obj1, BeaconMinimal obj2) {
                if ((obj1 != null) && (obj2 != null) && (obj1.getRssi() != null) && (obj2.getRssi() != null)) {
                    return obj2.getRssi() - obj1.getRssi();
                }
                else {
                    return 0;
                }
            }
        });
        return list;
    }

    private List<BeaconMinimal> removeBeaconFromList(List<BeaconMinimal> list, BeaconMinimal beacon) {
        if ((list != null) && (list.size() > 0)) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getId().equals(beacon.getId())) {
                    list.remove(i);
                    break;
                }
            }
        }
        return list;
    }

    private SecureProfileListener createSecureProfileListener() {
        return new SimpleSecureProfileListener() {
            @Override
            public void onProfileDiscovered(final ISecureProfile profile) {
                super.onProfileDiscovered(profile);
                updateBatteryStatus(profile);
                updateList(profile);
            }

            @Override
            public void onProfilesUpdated(List<ISecureProfile> profiles) {
                super.onProfilesUpdated(profiles);
                for (ISecureProfile profile : profiles) {
                    updateList(profile);
                }
            }

            @Override
            public void onProfileLost(ISecureProfile profile) {
                super.onProfileLost(profile);
                beaconViewModel.getByInstanceId(profile.getUniqueId(), new LoadBeaconMinimalEvent() {
                    @Override
                    public void onSuccess(BeaconMinimal beaconMinimal) {
                        List<BeaconMinimal> newList;
                        if (nearbyBeacons.getValue() == null) {
                            newList = new ArrayList<>();
                        }
                        else {
                            newList = nearbyBeacons.getValue();
                        }
                        newList = removeBeaconFromList(newList, beaconMinimal);
                        nearbyBeacons.setValue(newList);
                    }

                    @Override
                    public void onError() {
                        // TODO: show error
                    }
                });
            }

            private void updateList(final ISecureProfile profile) {
                beaconViewModel.getByInstanceId(profile.getUniqueId(), new LoadBeaconMinimalEvent() {
                    @Override
                    public void onSuccess(BeaconMinimal beaconMinimal) {
                        beaconMinimal.setRssi(profile.getRssi());
                        List<BeaconMinimal> newList;
                        if (nearbyBeacons.getValue() == null) {
                            newList = new ArrayList<>();
                        }
                        else {
                            newList = nearbyBeacons.getValue();
                        }

                        if (!isBeaconInList(newList, beaconMinimal)) {
                            newList = addBeaconToList(newList, beaconMinimal);
                            nearbyBeacons.setValue(newList);
                        }
                        else {
                            newList = updateBeaconInList(newList, beaconMinimal);
                            nearbyBeacons.setValue(newList);
                        }
                    }

                    @Override
                    public void onError() {

                    }
                });
            }

            private void updateBatteryStatus(ISecureProfile profile) {
                try {
                    BeaconBatteryLevelUpdate update = new BeaconBatteryLevelUpdate();
                    update.setBatteryLevel(profile.getBatteryLevel());
                    String[] nameParts = profile.getName().split("#");
                    trustedApi.updateUsingPATCH2Async(update, nameParts[1], new ApiCallback<Beacon>() {
                        @Override
                        public void onFailure(ApiException e, int i, Map<String, List<String>> map) {

                        }

                        @Override
                        public void onSuccess(Beacon beacon, int i, Map<String, List<String>> map) {

                        }

                        @Override
                        public void onUploadProgress(long l, long l1, boolean b) {

                        }

                        @Override
                        public void onDownloadProgress(long l, long l1, boolean b) {

                        }
                    }).execute();
                } catch (Exception e) {
                    //
                }
            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();
        startScanningIfLocationPermissionGranted();
        PubSub.getInstance().register(this);
    }

    @Override
    public void onPause() {
        proximityManager.stopScanning();
        proximityManager.disconnect();
        PubSub.getInstance().unregister(this);
        super.onPause();
    }

    @Override
    public void onDetach() {
        proximityManager.disconnect();
        proximityManager = null;
        super.onDetach();
    }

    @Override
    public void onRefresh() {
        startScanningIfLocationPermissionGranted();
        swipeBeacons.setRefreshing(false);
    }
}
