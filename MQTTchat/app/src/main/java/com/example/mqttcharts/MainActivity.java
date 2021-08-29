package com.example.mqttcharts;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.alibaba.fastjson.JSONObject;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    public final String TAG = MainActivity.class.getSimpleName();
    private TextView content;
    private EditText input;
    private Button cleanup;
    private Button send;
    private Button reset;
    Integer[] sum = new Integer[]{9, 4, 5, 8, 11, 15, 17, 16, 14};
    String token;
    String clientId;//设备ID
    String apiUrl = "http://a3.easemob.com";
    String appKey = "xxxxxxxxxxxxxx#xxxxx";
    //mqtt
    String appId = "xxxxxxx";
    String host = "tcp://xxxxxxx.cn1.mqtt.chat:1883";
    String topic = "mqtt-chart";
    String username = "chart"+System.currentTimeMillis();
    String password = "xxxxxxx";
    private static MqttAndroidClient  mqttAndroidClient;
    final ExecutorService executorService = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        new Thread(new Runnable(){
            @Override
            public void run() {
                try {
                    initMQTT();
                } catch (MqttException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        content = findViewById(R.id.content);
        input = findViewById(R.id.input);
        cleanup = findViewById(R.id.cleanup);
        send = findViewById(R.id.send);
        reset = findViewById(R.id.reset);
        initContent();//初始化
        cleanup.setOnClickListener(this);
        send.setOnClickListener(this);
        reset.setOnClickListener(this);
        content.setMovementMethod(new ScrollingMovementMethod());
    }

    private void initMQTT() throws MqttException, InterruptedException {
        clientId = username+"@" + appId ;//System.currentTimeMillis() + "@" + appId;
        mqttAndroidClient = new MqttAndroidClient(this,"tcp://broker.emqx.io:1883",clientId);// "tcp://1NQ1E9.sandbox.mqtt.chat:1883", clientId);
//        mqttAndroidClient.wait(5000);

        Log.d(TAG, "获取token前，token=："+token);
        mqttGetToken();
        Log.d(TAG, "注册token前，token=："+token);
        mqttRegister(username,password);
        Log.d(TAG, "登录token前，token=："+token);
//        mqttLogin(username,password);
        Log.d(TAG, "连接token前，token=："+token);
        mqttConnect();
        Log.d(TAG, "连接后，token=："+token);

    }

    private void mqttRegister(String uname,String passwd) {
        Log.d(TAG, "开始注册用户："+uname);
        JSONObject json = new JSONObject();
        json.put("username", uname);
        json.put("password", passwd);
        json.put("password", passwd);
        try{
            String ret = httpPost(apiUrl + "/" + appKey.replace("#", "/") + "/users", json.toJSONString(),"Bearer "+token);
            Log.d(TAG, "注册返回："+ret);
        }catch (Exception e) {
            Log.d(TAG, uname+"注册失败："+e.toString());
        }
    }
    private void mqttGetToken() {
        Log.d(TAG, "开始获取token");
        JSONObject json = new JSONObject();
        json.put("client_id", "xxxxxxxxxxxxxxx-xxxxxxx");
        json.put("client_secret", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
        json.put("grant_type", "client_credentials");
        try {
            String ret = httpPost(apiUrl + "/" + appKey.replace("#", "/") + "/token", json.toJSONString(),"getToken");
            Log.d(TAG, "获取token返回："+ret);
            JSONObject result = JSONObject.parseObject(ret);
            token = result.getString("access_token");
            Log.d(TAG, "拿到token："+token);
        } catch (Exception e) {
            token = "";
            Log.d(TAG, "获取token失败："+e.toString());
        }
    }
    private void mqttLogin(String uname,String passwd) {
        Log.d(TAG, "开始登录："+uname);
        JSONObject json = new JSONObject();
        json.put("username", uname);
        json.put("password", passwd);
        json.put("grant_type", "password");
        try {
            String ret = httpPost(apiUrl + "/" + appKey.replace("#", "/") + "/token", json.toJSONString(),"getToken");
            Log.d(TAG, uname+"登录返回："+ret);
            JSONObject result = JSONObject.parseObject(ret);
            token = result.getString("access_token");
        } catch (Exception e) {
            Log.d(TAG, uname+"登录失败："+e.toString());
        }
    }
    private void mqttConnect() throws MqttException, InterruptedException {
        Log.d(TAG, "开始连接,token="+token);
        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setPassword(token.toCharArray());
        mqttConnectOptions.setCleanSession(true);
        mqttConnectOptions.setKeepAliveInterval(90);
        mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setMqttVersion(4);
        mqttConnectOptions.setConnectionTimeout(5000);

        mqttAndroidClient.connect(mqttConnectOptions);
        Log.d(TAG, "连接 success");
        //暂停1秒钟，等待连接订阅完成
        Thread.sleep(1000);
        try {
            mqttAndroidClient.subscribe(topic, 2);
            Log.d(TAG, "已订阅成功，token="+token);
        } catch (MqttException e) {
            Log.d(TAG, "订阅失败，原因："+e.getMessage());
            e.printStackTrace();
        }
        mqttAndroidClient.setCallback(new MqttCallbackExtended() {
            /**
             * 连接完成回调方法
             * @param b
             * @param s
             */
            @Override
            public void connectComplete(boolean b, String s) {
                /**
                 * 客户端连接成功后就需要尽快订阅需要的Topic。
                 */
                Log.d(TAG, "connect success");
                executorService.submit(() -> {
                    try {
                        final String[] topicFilter = {topic};
                        final int[] qos = {2};
                        mqttAndroidClient.subscribe(topicFilter, qos);
                        Log.d(TAG, "已订阅,token="+token);
                    } catch (Exception e) {
                        Log.d(TAG, "订阅失败："+e.toString());
                        e.printStackTrace();
                    }
                });
            }
            /**
             * 连接失败回调方法
             * @param throwable
             */
            @Override
            public void connectionLost(Throwable throwable) {
                System.out.println("connection lost");
                throwable.printStackTrace();
            }
            /**
             * 接收消息回调方法
             * @param s
             * @param mqttMessage
             */
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void messageArrived(String s, MqttMessage mqttMessage) {
                Log.d(TAG,"MQTT receive msg from topic " + s + " , body is " + new String(mqttMessage.getPayload()));
                String str = new String(mqttMessage.getPayload());
                String[] info = str.split(";");
                for(String ss:info){
                    Log.d(TAG,"MQTT info i ==  " + ss);
                }
                if(!info[0].equals(username)){
                    String sstr = "<br />"+info[0]+":"+info[1];
                    content.append(Html.fromHtml(sstr));
                }
            }
            @Override
            public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
                try {
                    Log.d(TAG,"MQTT send msg succeed MessageId is : " + iMqttDeliveryToken.getMessageId()+",Message="+iMqttDeliveryToken.getMessage().toString());
                } catch (MqttException e) {
                    Log.d(TAG,"MQTT send msg succeed MessageId is : " + iMqttDeliveryToken.getMessageId());
                    e.printStackTrace();
                }
            }
        });
    }

    private void initContent() {

    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.cleanup:
                Toast.makeText(this,input.getText(),Toast.LENGTH_LONG).show();
                input.setText("");
                break;
            case R.id.send:
                String str = ""+input.getText();
                if(str.equals("")){
                    Toast.makeText(this,"请输入发送内容！",Toast.LENGTH_LONG).show();
                    break;
                }
                content.append(Html.fromHtml("<br />"+username+":"+input.getText()));
                input.setText("");
                new Thread(new Runnable(){
                    @Override
                    public void run() {
                        MqttMessage message = new MqttMessage((username+";"+str).getBytes());
                        //设置传输质量
                        message.setQos(2);
                        Log.d(TAG,"send message ="+message.toString());
                        try {
                            mqttConnect();
                        } catch (MqttException e) {
                            e.printStackTrace();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        try {
                            mqttAndroidClient.publish(topic, message);
                        } catch (MqttException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
                break;
            case R.id.reset:
                content.setText("");
                break;
            default:
                break;
        }
    }

    private String httpPost(String url, String params,String auth) {
        String ret = "";
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        Request request = new Request.Builder()
                .addHeader("Authorization", auth)
                .url(url)
                .post(RequestBody.create(mediaType, params))
                .build();
        OkHttpClient okHttpClient = new OkHttpClient();
        Call call = okHttpClient.newCall(request);
        try {
            Response response = call.execute();
            ret = response.body().string();
            Log.d(TAG,"ret="+ret);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ret;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

}