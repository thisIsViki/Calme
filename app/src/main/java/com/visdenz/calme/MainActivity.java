package com.visdenz.calme;

import android.Manifest;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import android.os.Handler;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener, SensorEventListener {

    private static final int REQUEST_SPEECH_INPUT_CODE = 100;
    private static final int MY_PERMISSION_READ_SMS = 101;
    private static final int MY_PERMISSION_SEND_SMS = 102;
    private static final int MY_PERMISSION_RECORD_AUDIO = 103;
    private static final CharSequence GO_TO_MAP = "go to";
    private static final CharSequence READ_SMS = "read SMS";
    private static final CharSequence SEND_MY_SMS = "send SMS";
    private TextView speechText;
    private TextToSpeech tts;
    private String phoneNo, msgContent;
    private SensorManager sensorManager;
    private long lastUpdate;
    private SpeechRecognizer mSpeechRecognizer;
    private Intent mSpeechRecognizerIntent;
    private boolean isOpen = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        speechText = (TextView) findViewById(R.id.speechText);
        tts = new TextToSpeech(this, this);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        lastUpdate = System.currentTimeMillis();

        if(ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.RECORD_AUDIO)) {
                Log.i("permission", "should show request permission rationale");
            } else {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.RECORD_AUDIO}, MY_PERMISSION_RECORD_AUDIO);
            }
        }
        else {
            promptSpeechInput();
        }

    }

    private void promptSpeechInput() {
        mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        mSpeechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        mSpeechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        mSpeechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        mSpeechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.speech_prompt));
        SpeechRecognizerListener listener = new SpeechRecognizerListener();
        mSpeechRecognizer.setRecognitionListener(listener);
        mSpeechRecognizer.startListening(mSpeechRecognizerIntent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case MY_PERMISSION_READ_SMS: {
                getUnreadSMS();
            }
            case MY_PERMISSION_SEND_SMS: {
                speakOut("To whom");
            }
            case MY_PERMISSION_RECORD_AUDIO: {
                promptSpeechInput();
            }
        }
    }

    @Override
    public void onInit(int status) {
        final Handler mHandler = new Handler(getApplicationContext().getMainLooper());
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.US);
            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "This Language is not supported");
            } else {

            }
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {

                }

                @Override
                public void onDone(String utteranceId) {
                    Runnable mDoneRunnable = new Runnable() {
                        @Override
                        public void run() {
                            mSpeechRecognizer.startListening(mSpeechRecognizerIntent);
                        }
                    };
                    mHandler.post(mDoneRunnable);
                    /*mHandler.postAtTime(mDoneRunnable, System.currentTimeMillis()+500);
                    mHandler.postDelayed(mDoneRunnable, 500);*/
                }

                @Override
                public void onError(String utteranceId) {
                    Log.i("msg", "speech error");
                }
            });
        } else {
            Log.e("TTS", "Initilization Failed!");
        }
    }

    private void sendSMS() {
        Intent smsIntent = new Intent(getApplicationContext(), MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 0, smsIntent, 0);
        SmsManager smsManager = SmsManager.getDefault();
        if(phoneNo != null && msgContent != null) {
            smsManager.sendTextMessage(phoneNo, null, msgContent, pi, null);
            speakOut("Message Sent Successfully");
        }
        else {
            speakOut("Message Address or Content is not proper. Please try again");
        }
    }

    private void getUnreadSMS() {
        Cursor cursor = getContentResolver().query(Uri.parse("content://sms/inbox"), null, "read = 0", null, null);
        String sms = "";
        if(cursor.getCount() != 0) {
            if(cursor.moveToFirst()) {
                do {
                    sms += "From " + formatMsg(cursor.getString(2)) + " Message Content " + formatMsg(cursor.getString(13)) + "\n";
                } while(cursor.moveToNext());
            }
        }
        else {
            sms = "No unread message";
        }
        speakOut(sms);
    }

    private String formatMsg(String sender) {
        StringBuilder sb = new StringBuilder();
        for(char c : sender.toCharArray()) {
            if(Character.isDigit(c)) {
                sb.append(c + " ");
            }
            else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private void speakOut(String textToSpeak) {
        HashMap<String, String> map = new HashMap<String, String>();
        map.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "UniqueID");
        tts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, map);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            getAccelerometer(event);
        }
    }

    private void getAccelerometer(SensorEvent event) {
        float[] values = event.values;
        float x = values[0];
        float y = values[1];
        float z = values[2];
        float accelationSquareRoot = (x*x + y*y + z*z)
                / (SensorManager.GRAVITY_EARTH * SensorManager.GRAVITY_EARTH);
        long actualTime = System.currentTimeMillis();
        if (accelationSquareRoot >= 2) {
            if (actualTime-lastUpdate < 200) {
                return;
            }
            lastUpdate = actualTime;
            Log.i("msg1", "Device Shaked");
            if(!isOpen) {
                Log.i("msg1", "Activity Started");
                Intent mainActivityIntent = new Intent(MainActivity.this, MainActivity.class);
                startActivity(mainActivityIntent);
                isOpen = true;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private class SpeechRecognizerListener implements RecognitionListener {
        @Override
        public void onReadyForSpeech(Bundle params) {
            Log.i("msg", "ready for speech");
        }

        @Override
        public void onBeginningOfSpeech() {
            Log.i("msg", "speech began");
        }

        @Override
        public void onRmsChanged(float rmsdB) {

        }

        @Override
        public void onBufferReceived(byte[] buffer) {

        }

        @Override
        public void onEndOfSpeech() {
            Log.i("msg", "speech end");
            mSpeechRecognizer.stopListening();
        }

        @Override
        public void onError(int error) {
            Log.i("msg", "speech error" + error);
            mSpeechRecognizer.startListening(mSpeechRecognizerIntent);
        }

        @Override
        public void onResults(Bundle results) {
            if(results != null) {
                ArrayList<String> result = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String words = result.get(0);
                speechText.setText(result.get(0));
                if(words.contains(GO_TO_MAP)) {
                    String extractedText = words.substring(words.indexOf("go to ") + 6);
                    String[] splited = extractedText.split("\\s+");
                    String address = "";
                    for(int i=0; i<splited.length; i++) {
                        address += splited[i] + "+";
                    }
                    Uri gmmIntentUri = Uri.parse("google.navigation:q=" + address);
                    Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                    mapIntent.setPackage("com.google.android.apps.maps");
                    startActivity(mapIntent);
                }
                else if(words.contains(READ_SMS)) {
                    if(ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                        if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.READ_SMS)) {
                            Log.i("permission", "should show request permission rationale");
                        } else {
                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_SMS}, MY_PERMISSION_READ_SMS);
                        }
                    }
                    else {
                        getUnreadSMS();
                    }
                }
                else if(words.contains(SEND_MY_SMS)) {
                    if(ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                        if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.SEND_SMS)) {
                            Log.i("permission", "should show request permission rationale");
                        } else {
                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.SEND_SMS}, MY_PERMISSION_SEND_SMS);
                        }
                    }
                    else {
                        speakOut("To whom");
                    }
                }
                else if(words.contains("contact")) {
                    phoneNo = words.replace("contact ", "");
                    speakOut("Say Message Body");
                }
                else if(words.contains("type")) {
                    msgContent = words.replace("type", "");
                    sendSMS();
                }
                else {
                    speakOut("Command not recognized. Please try again.");
                }
            }
            else {
                speakOut("Please try again.");
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {

        }

        @Override
        public void onEvent(int eventType, Bundle params) {

        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        mSpeechRecognizer.destroy();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isOpen = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        isOpen = true;
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
    }

}
