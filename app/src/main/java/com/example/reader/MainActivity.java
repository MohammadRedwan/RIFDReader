package com.example.reader;

import androidx.appcompat.app.AppCompatActivity;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import org.bson.Document;


import java.text.DateFormat;
import java.util.Date;

import io.realm.Realm;
import io.realm.mongodb.App;
import io.realm.mongodb.AppConfiguration;
import io.realm.mongodb.Credentials;
import io.realm.mongodb.RealmResultTask;
import io.realm.mongodb.User;
import io.realm.mongodb.mongo.MongoClient;
import io.realm.mongodb.mongo.MongoCollection;
import io.realm.mongodb.mongo.MongoDatabase;
import io.realm.mongodb.mongo.iterable.MongoCursor;

public class MainActivity extends AppCompatActivity {
    private Handler dHandler = new Handler();
    NfcAdapter nfcAdapter;
    PendingIntent pendingIntent;
    Tag myTag;
    Context context;
    TextView nfcContent, Status, TagID;
    String AppId = "application-0-keuuu";
    String TagCheck,date,data;
    Integer ZoneNumber = 1;
    private App app;
    MongoClient mongoClient;
    MongoDatabase mongoDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        nfcContent = (TextView) findViewById(R.id.nfcContent);
        Status = (TextView) findViewById(R.id.Status);
        TagID = (TextView) findViewById(R.id.TagID);
        context = this;
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        readFromIntent(getIntent());
        pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        tagDetected.addCategory(Intent.CATEGORY_DEFAULT);
        Realm.init(getApplicationContext());
        app = new App(new AppConfiguration.Builder(AppId).build());
        Credentials credentials = Credentials.emailPassword("w@w","123456");
        app.loginAsync(credentials, new App.Callback<User>() {
            @Override
            public void onResult(App.Result<User> result) {
                if(result.isSuccess())
                {
                    Log.v("User","Successfully authenticated using an email and password.");
                    Toast.makeText(MainActivity.this, "Successfully authenticated using an email and password.", Toast.LENGTH_SHORT).show();
                }
                else
                {
                    Log.v("User","Failed to Login");
                    Toast.makeText(MainActivity.this, "Failed to Login", Toast.LENGTH_SHORT).show();
                }
            }
        });
        dHandler.postDelayed(SendingData, 3000);
    }

    private Runnable SendingData = new Runnable(){
        @Override
        public void run() {
            User user = app.currentUser();
            mongoClient = user.getMongoClient("mongodb-atlas");
            mongoDatabase = mongoClient.getDatabase("myFirstDatabase");
            MongoCollection<Document> mongoCollection = mongoDatabase.getCollection("items");
            String mongoUserId = user.getId();
            TagCheck =  (String) TagID.getText();
            date = DateFormat.getDateTimeInstance().format(new Date());
            data = (String) TagID.getText();
            Document queryFilter = new Document().append("TagID",TagCheck);
            RealmResultTask<MongoCursor<Document>> findTask = mongoCollection.find(queryFilter).iterator();
            findTask.getAsync(task -> {
                if(task.isSuccess())
                {
                    MongoCursor<Document> results = task.get();
                    if(results.hasNext())
                    {
                        Log.v("Find Function","Found Match");
                        Document result = results.next();
                        result.append("Zone",ZoneNumber).append("date",date);
                        mongoCollection.updateOne(queryFilter,result).getAsync(result1 -> {
                            if (result1.isSuccess())
                            {
                                Log.v("Update Function","Updated Data");
                                Toast.makeText(MainActivity.this, "Updated Data", Toast.LENGTH_SHORT).show();
                                Status.setText("Updated Data");

                            }
                            else
                            {
                                Log.v("Update Function","Error"+result1.getError().toString());
                                Toast.makeText(MainActivity.this, "Error"+result1.getError().toString(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    else
                    {
                        Log.v("Find Function","Found Nothing");
                        mongoCollection.insertOne(new Document().append("userid",mongoUserId).append("TagID",data).append("Zone",ZoneNumber).append("date",date)).getAsync(result -> {
                            if(result.isSuccess())
                            {
                                Log.v("Insert Function","Inserted Data");
                                Toast.makeText(MainActivity.this, "Inserted Data", Toast.LENGTH_SHORT).show();
                                Status.setText("Inserted Data");
                            }
                            else
                            {
                                Log.v("Insert Function","Error"+result.getError().toString());
                                Toast.makeText(MainActivity.this, "Error"+result.getError().toString(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
                else
                {
                    Log.v("Error",task.getError().toString());
                }
            });
                Toast.makeText(MainActivity.this, "TagID: " + data, Toast.LENGTH_SHORT).show();

        }
    };

    private void readFromIntent(Intent intent) {
        String action = intent.getAction();
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            NdefMessage[] msgs = null;
            if(rawMsgs != null) {
                msgs = new NdefMessage[rawMsgs.length];
                for(int i = 0; i < rawMsgs.length; i++){
                    msgs[i] = (NdefMessage) rawMsgs[i];
                }
            }
            buildTagViews(msgs);
        }
    }

    private void buildTagViews(NdefMessage[] msgs) {
        if (msgs == null || msgs.length == 0) return;
        String text = "";
        byte[] payload = msgs[0].getRecords()[0].getPayload();
        String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16";
        int languageCodeLength = payload[0] & 0063;
        try{
            text = new String(payload,languageCodeLength+1,payload.length - languageCodeLength-1, textEncoding);
        }catch(Exception e){
            Log.e("UnsupportedEncoding", e.toString());
        }
        TagID.setText(text);
        }

    @Override
    protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        setIntent(intent);
        readFromIntent(intent);
        if(NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())){
            myTag = intent.getParcelableExtra(nfcAdapter.EXTRA_TAG);
        }
    }
}
