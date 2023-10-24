package com.example.thirtyscs;

import android.content.Context;
import android.util.Log;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;

//custom class that creates a client object
//the object should be created in the main activity as public and static
//so that every object in the app has access to it
public class MQTTClient {
    public Mqtt3AsyncClient client; //HiveMQ library

    //necessary variables for activity
    private Context appContext;

    //server information
    //private final String server = "juantarrat.duckdns.org";

    private final String server = "test.mosquitto.org";
    private final String TAG = "Connected"; //useful for debugging

    public MQTTClient(Context appContext) {
        this.appContext = appContext;
        //create client
        this.client = client = MqttClient.builder()
                .useMqttVersion3()
                .identifier("STUGClient")
                .serverHost(server)
                .serverPort(1883)
                .automaticReconnectWithDefaultConfig()//this keeps connection alive to use app after a long time
                .buildAsync();

        this.reconnect(); //custom method to connect
    }

    public void publishMessage(String payload, String topic){
        client.publishWith()
                .topic(topic)
                .payload(payload.getBytes())
                .retain(false)
                .send()
                .whenComplete((publish, throwable) -> {
                    if (throwable != null) {
                        // handle failure to publish
                        Log.i("Publish", "Failure");
                    } else {
                        Log.i("Publish", "Success");
                    }
                });
    }


    //possibly useful at some point, disconnects client
    //app crashes if app fails to connect to server
    public void disconnect(){
        client.disconnect();
    }

    //connect to server
    public void reconnect(){
        client.connect()
                .whenComplete(((mqtt3ConnAck, throwable) -> {
                    if(throwable != null){
                        Log.i(TAG, "Failure"); //useful for debugging
                    }
                    else{
                        Log.i(TAG, "Success");

                    }
                }));
    }
}
