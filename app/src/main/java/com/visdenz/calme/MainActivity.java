package com.visdenz.calme;

import android.Manifest;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.speech.RecognizerIntent;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private static final int REQUEST_SPEECH_INPUT_CODE = 100;
    private static final int MY_PERMISSION_READ_SMS = 101;
    private static final int MY_PERMISSION_SEND_SMS = 102;
    private static final CharSequence GO_TO_MAP = "go to";
    private static final CharSequence READ_SMS = "read SMS";
    private static final CharSequence SEND_MY_SMS = "send SMS";
    private TextView speechText;
    private TextToSpeech tts;
    private String phoneNo, msgContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        speechText = (TextView) findViewById(R.id.speechText);
        tts = new TextToSpeech(this, this);

        promptSpeechInput();
    }

    private void promptSpeechInput() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.speech_prompt));
        try {
            startActivityForResult(i, REQUEST_SPEECH_INPUT_CODE);
        } catch (ActivityNotFoundException e) {
            Log.e("exception", e.toString());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_SPEECH_INPUT_CODE: {
                if(resultCode == RESULT_OK && data != null) {
                    ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
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
                        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_SMS)) {
                                Log.i("permission", "should show request permission rationale");
                            } else {
                                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_SMS}, MY_PERMISSION_READ_SMS);
                            }
                        }
                        else {
                            getUnreadSMS();
                        }
                    }
                    else if(words.contains(SEND_MY_SMS)) {
                        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.SEND_SMS)) {
                                Log.i("permission", "should show request permission rationale");
                            } else {
                                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS}, MY_PERMISSION_SEND_SMS);
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
        }
    }

    private void sendSMS() {
        Log.i("msg", "To = " + phoneNo);
        Log.i("msg", "Body = " + msgContent);
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
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public void onInit(int status) {
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
                    promptSpeechInput();
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

    private void speakOut(String textToSpeak) {
        HashMap<String, String> map = new HashMap<String, String>();
        map.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "UniqueID");
        tts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, map);
    }
}
