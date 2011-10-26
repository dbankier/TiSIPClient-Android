package com.yydigital.sipclient;

import org.appcelerator.kroll.KrollDict;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.sip.*;
import android.util.Log;

public class IncomingCallReceiver extends BroadcastReceiver {
    
	
	private AndroidSIPClient client;
	
	
    public IncomingCallReceiver(AndroidSIPClient client) {
		super();
		this.client = client;
	}


	@Override
    public void onReceive(Context context, Intent intent) {
        SipAudioCall incomingCall = null;
        try {

            SipAudioCall.Listener listener = new SipAudioCall.Listener() {
                @Override
                public void onRinging(SipAudioCall call, SipProfile caller) {
                    try {
                        call.answerCall(30);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            incomingCall = client.getManager().takeAudioCall(intent, listener);
            client.setCall(incomingCall);
            Log.d(AndroidSIPClient.LCAT, "Incoming Call");
            
            
    		KrollDict eventProperties = new KrollDict();
    		eventProperties.put ("displayName", incomingCall.getPeerProfile().getDisplayName());
    		eventProperties.put ("uri", incomingCall.getPeerProfile().getUriString());
    		client.fireCallback (AndroidSIPClient.ON_INCOMING_CALL, new Object [] {eventProperties});
    
        } catch (Exception e) {
            if (incomingCall != null) {
                incomingCall.close();
            }
        }
    }

}
