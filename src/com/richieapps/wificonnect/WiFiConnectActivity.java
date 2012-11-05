package com.richieapps.wificonnect;


import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.github.rtyley.android.sherlock.roboguice.activity.RoboSherlockFragmentActivity;

import java.util.List;

public class WiFiConnectActivity extends RoboSherlockFragmentActivity {
    private boolean mResumed = false;
    private boolean mWriteMode = false;
    NfcAdapter mNfcAdapter;
    EditText mSsid, mPass;
    WifiManager wifi;
    Spinner spinMe;

    PendingIntent mNfcPendingIntent;
    IntentFilter[] mWriteTagFilters;
    IntentFilter[] mNdefExchangeFilters;
    private WriteTag writeTag;
    ActionBar bar;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bar = getSupportActionBar();
        wifi = (WifiManager)getSystemService(Context.WIFI_SERVICE);
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        setContentView(R.layout.wificonnect);
        findViewById(R.id.write_tag).setOnClickListener(mTagWriter);
        mSsid = ((EditText) findViewById(R.id.ssid));
        mSsid.addTextChangedListener(mTextWatcher);
        mPass = ((EditText) findViewById(R.id.pass));
        spinMe = ((Spinner) findViewById(R.id.networkSpinner));
        List<WifiConfiguration> savedConfigs = wifi.getConfiguredNetworks();
        spinMe.setAdapter(new NetworkAdapter(savedConfigs));
        spinMe.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                WifiConfiguration c = (WifiConfiguration)adapterView.getItemAtPosition(i);
                Editable text = mSsid.getText();
                text.clear();
                String temp = c.SSID.replaceAll("\"", "");
                text.append(temp);
                Editable key = mPass.getText();
                key.clear();
                //temp = c.preSharedKey.replaceAll("\"", "");
                //key.append(temp);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                //To change body of implemented methods use File | Settings | File Templates.
            }
        });
        // Handle all of our received NFC intents in this activity.
        mNfcPendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

        // Intent filters for reading a note from a tag or exchanging over p2p.
        IntentFilter ndefDetected = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
        try {
            ndefDetected.addDataType("application/wificonnector");
        } catch (IntentFilter.MalformedMimeTypeException e) { }
        mNdefExchangeFilters = new IntentFilter[] { ndefDetected };

        // Intent filters for writing to a tag
        IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        mWriteTagFilters = new IntentFilter[] { tagDetected };

        writeTag = new WriteTag(this, "application/wificonnector", this, mNfcPendingIntent, mWriteTagFilters);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mResumed = true;
        // Sticky notes received from Android
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
            writeTag.getNdefMessages(getIntent());
            setConnection(new String(writeTag.getBytePayload()));
            setIntent(new Intent());
        }
        writeTag.enableNdefExchangeMode();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mResumed = false;
        writeTag.disableNdefExchangeMode();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // NDEF exchange mode
        if (!writeTag.isWriteMode() && writeTag.isNdefDiscovered(intent)) {
            NdefMessage[] msgs = writeTag.getNdefMessages(intent);
            promptForContent(msgs[0]);
        }

        // Tag writing mode
        if (writeTag.isWriteMode() && writeTag.isTagDiscovered(intent)) {
//            Tag detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            writeTag.writeToTag(getNoteAsNdef(), writeTag.getDetectedTag(intent));
        }
    }

    private TextWatcher mTextWatcher = new TextWatcher() {

        @Override
        public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {

        }

        @Override
        public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {

        }

        @Override
        public void afterTextChanged(Editable arg0) {
            if (mResumed) {
                writeTag.enableNdefExchangeMode();
            }
        }
    };

    private View.OnClickListener mTagWriter = new View.OnClickListener() {
        @Override
        public void onClick(View arg0) {
            // Write to a tag for as long as the dialog is shown.
            writeTag.disableNdefExchangeMode();
            writeTag.enableTagWriteMode();

            new AlertDialog.Builder(WiFiConnectActivity.this).setTitle("Touch tag to write")
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            writeTag.disableTagWriteMode();
                            writeTag.enableNdefExchangeMode();
                        }
                    }).create().show();
        }
    };

    private void promptForContent(final NdefMessage msg) {
        new AlertDialog.Builder(this).setTitle("Replace current content?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {
                        String body = new String(msg.getRecords()[0].getPayload());
                        setNoteBody(body);
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {

                    }
                }).show();
    }

    private void setNoteBody(String body) {
        String[] splitBody = body.split(",");
        Editable text = mSsid.getText();
        text.clear();
        text.append(splitBody[0]);
        Editable key = mPass.getText();
        key.clear();
        key.append(splitBody[1]);
    }

    private NdefMessage getNoteAsNdef() {
        String tagText = mSsid.getText().toString()+","+mPass.getText().toString();
        byte[] textBytes = tagText.getBytes();
        NdefRecord textRecord = new NdefRecord(NdefRecord.TNF_MIME_MEDIA, "application/wificonnector".getBytes(),
                new byte[] {}, textBytes);
        return new NdefMessage(new NdefRecord[] {
                textRecord
        });
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }



    public void setConnection(String body){
        wifi.setWifiEnabled(true);
        String[] splitStuff = body.split(",");

        WifiConfiguration conf = new WifiConfiguration();
        conf.SSID = "\""+splitStuff[0]+"\"";
        conf.preSharedKey = "\""+splitStuff[1]+"\"";

        conf.status = WifiConfiguration.Status.ENABLED;
        conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        conf.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        conf.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        conf.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        int res = wifi.addNetwork(conf);
        boolean b = wifi.enableNetwork(res, true);

        if(b) {
            wifi.setWifiEnabled(false);
            toast("WiFi Connection made");
        }
        else {
            toast("FAILURE");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(com.actionbarsherlock.view.MenuItem item) {
        return super.onOptionsItemSelected(item);    //To change body of overridden methods use File | Settings | File Templates.
    }

    private class NetworkAdapter extends BaseAdapter {
        List<WifiConfiguration> list;
        public NetworkAdapter(List<WifiConfiguration> list){
            this.list = list;
        }
        @Override
        public int getCount() {
            return list.size();
        }

        @Override
        public Object getItem(int i) {
            return list.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            if(view == null) {
                LayoutInflater li = getLayoutInflater();
                view = li.inflate(R.layout.text_list_item, null);
            }
            WifiConfiguration l = (WifiConfiguration) getItem(i);
            ((TextView)view.findViewById(R.id.text_list_item_text)).setText(l.SSID);
            return view;
        }
    }

}

