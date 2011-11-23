package com.yydigital.sipclient;

import java.text.ParseException;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.titanium.kroll.KrollCallback;
import org.appcelerator.titanium.util.Log;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.sip.SipAudioCall;
import android.net.sip.SipException;
import android.net.sip.SipManager;
import android.net.sip.SipProfile;
import android.net.sip.SipRegistrationListener;

public class AndroidSIPClient {
	public static final String LCAT = "SIPClient";

	public static final String ON_REGISTERING = "onregistering";
	public static final String ON_REGISTRATION_DONE = "onregistrationdone";
	public static final String ON_REGISTRATION_FAILED = "onregistrationfailed";
	public static final String ON_CALL_ESTABLISHED = "oncallestablished";
	public static final String ON_CALL_ENDED = "oncallended";
	public static final String ON_CALL_BUSY = "oncallbusy";
	public static final String ON_ERROR = "onerror";
	public static final String ON_RINGING_BACK = "onringingback";
	public static final String ON_INCOMING_CALL = "onincomingcall";

	private KrollProxy proxy;
	private SipManager mSipManager = null;
	private SipProfile mSipProfile = null;
	private SipAudioCall call = null;

	public AndroidSIPClient(KrollProxy proxy) {
		this.proxy = proxy;
		if (mSipManager == null) {
			mSipManager = SipManager.newInstance(proxy.getTiContext()
					.getAndroidContext());
		}
		IntentFilter filter = new IntentFilter();
		filter.addAction("com.yydigital.sipclient.INCOMING_CALL");
		IncomingCallReceiver callReceiver = new IncomingCallReceiver(this);
		proxy.getTiContext().getActivity()
				.registerReceiver(callReceiver, filter);
	}

	public void register() throws ParseException, SipException {
		if (getProperty("username") == null) {
			throw new IllegalArgumentException("Username required");
		}
		if (getProperty("password") == null) {
			throw new IllegalArgumentException("Password required");
		}
		if (getProperty("domain") == null) {
			throw new IllegalArgumentException("Domain required");
		}
		SipProfile.Builder builder = new SipProfile.Builder(
				getProperty("username"), getProperty("domain"));
		builder.setPassword(getProperty("password"));
		// Optional bits
		if (getProperty("outboundProxy") != null) {
			builder.setOutboundProxy(getProperty("outboundProxy"));
		}
		/*
		 * if (getProperty("authUserName") != null) {
		 * builder.setAuthUserName(getProperty("authUserName")); }
		 */
		if (proxy.getProperty("port") != null) {
			builder.setPort(((Double) proxy.getProperty("port")).intValue());
		}
		if (proxy.getProperty("autoRegistration") != null) {
			builder.setAutoRegistration((Boolean)
					proxy.getProperty("autoRegistration"));
		}
		if (proxy.getProperty("sendKeepAlive") != null) {
			builder.setSendKeepAlive((Boolean)
					proxy.getProperty("sendKeepAlive"));
		}
		if (getProperty("protocol") != null) {
			builder.setProtocol(getProperty("protocol"));
		}
		mSipProfile = builder.build();
		Log.d(LCAT, "Setting Incoming Call Broadcaster");
		Intent intent = new Intent();
		intent.setAction("com.yydigital.sipclient.INCOMING_CALL");
		PendingIntent pendingIntent = PendingIntent.getBroadcast(proxy
				.getTiContext().getAndroidContext(), 0, intent,
				Intent.FILL_IN_DATA);

		Log.d(LCAT, "Opening");
		mSipManager.open(mSipProfile, pendingIntent, null);

		Log.d(LCAT, "Registration Listeners");
		mSipManager.setRegistrationListener(mSipProfile.getUriString(),
				new SipRegistrationListener() {

					public void onRegistering(String localProfileUri) {
						fireCallback(ON_REGISTERING);
						Log.d(LCAT, "Registering with SIP Server...");
					}

					public void onRegistrationDone(String localProfileUri,
							long expiryTime) {
						fireCallback(ON_REGISTRATION_DONE);
						Log.d(LCAT, "Ready");
					}

					public void onRegistrationFailed(String localProfileUri,
							int errorCode, String errorMessage) {
						fireCallback(ON_REGISTRATION_FAILED);
						Log.d(LCAT,
								"Registration failed.  Please check settings.");
					}
				}
		);
	}

	public void close() {
		if (mSipManager == null) {
			return;
		}
		try {
			if (mSipProfile != null) {
				mSipManager.close(mSipProfile.getUriString());
			}
		} catch (Exception ee) {
			Log.d(LCAT, "Failed to close local profile.", ee);
		}
	}

	public void answer() throws SipException {
		if (call != null) {
			call.answerCall(30);
			call.startAudio();
			call.setSpeakerMode(true);
            if(call.isMuted()) {
            	call.toggleMute();
            }
		}
	}

	public void hangup() throws SipException {
		if (call != null) {
			call.endCall();
			fireCallback(ON_CALL_ENDED);
			call.close();
		}
	}

	public boolean isInCall() {
		return call != null && call.isInCall();
	}

	public boolean isMuted() {
		return call != null && call.isMuted();
	}

	public boolean isOnHold() {
		return call != null && call.isOnHold();
	}
	
	public boolean isRegistered() {
		try {
			return mSipManager != null && mSipProfile != null && mSipManager.isRegistered(mSipProfile.getUriString());
		} catch (SipException e) {
			return false;
		}
	}

	public void holdCall() throws SipException {
		if (!isOnHold()) {
			call.holdCall(30);
		}
	}

	public void unholdCall() throws SipException {
		if (isOnHold()) {
			call.continueCall(30);
		}
	}

	public void toggleMuted() {
		if (isInCall()) {
			call.toggleMute();
		}
	}

	public void setSpeakerMode(boolean value) {
		if (isInCall()) {
			call.setSpeakerMode(value);
		}
	}

	public void sendDTMF(int code) {
		if (isInCall()) {
			call.sendDtmf(code);
		}
	}

	public void initiateCall(String address) {
		try {
			SipAudioCall.Listener listener = new SipAudioCall.Listener() {
				// Much of the client's interaction with the SIP Stack will
				// happen via listeners. Even making an outgoing call, don't
				// forget to set up a listener to set things up once the call is
				// established.
				@Override
				public void onCallEstablished(SipAudioCall call) {
					call.startAudio();
					call.setSpeakerMode(true);
		            if(call.isMuted()) {
		            	call.toggleMute();
		            }

					fireCallback(ON_CALL_ESTABLISHED);
					Log.d(LCAT, "Call Established");
				}
				
				@Override
				public void onCallBusy(SipAudioCall call) {
					fireCallback(ON_CALL_BUSY);
					Log.d(LCAT, "Call Busy");
				}
				
				
				@Override
				public void onError(SipAudioCall call, int errorCode, String errorMessage) {
		            KrollDict eventProperties = new KrollDict();
		    		eventProperties.put ("errorCode", errorCode);
		    		eventProperties.put ("errorMessage", errorMessage);
		    		fireCallback (ON_CALL_BUSY, new Object [] {eventProperties});
					Log.d(LCAT, "Call Error: " + errorMessage);
				}
				
				@Override
				public void onRingingBack(SipAudioCall call) {
					fireCallback(ON_RINGING_BACK);
					Log.d(LCAT, "Call Ringing Back Received");
				}
				

				@Override
				public void onCallEnded(SipAudioCall call) {
					fireCallback(ON_CALL_ENDED);
					Log.d(LCAT, "Call Ended");
				}
			};

			call = mSipManager.makeAudioCall(mSipProfile.getUriString(),
					address, listener, 30);

		} catch (Exception e) {
			Log.i(LCAT, "Error when trying to close manager.", e);
			if (mSipProfile != null) {
				try {
					mSipManager.close(mSipProfile.getUriString());
				} catch (Exception ee) {
					Log.i("WalkieTalkieActivity/InitiateCall",
							"Error when trying to close manager.", ee);
					ee.printStackTrace();
				}
			}
			if (call != null) {
				call.close();
			}
		}
	}

	public KrollCallback getCallback(String name) {
		Object value = proxy.getProperty(name);
		if (value != null && value instanceof KrollCallback) {
			return (KrollCallback) value;
		}
		return null;
	}

	public void fireCallback(String name) {
		KrollDict eventProperties = new KrollDict();
		eventProperties.put("source", proxy);

		fireCallback(name, new Object[] { eventProperties });
	}

	public void fireCallback(String name, Object[] args) {
		KrollCallback cb = getCallback(name);
		if (cb != null) {
			cb.setThisProxy(proxy);
			cb.callAsync(args);
		}
	}

	public String getProperty(String name) {
		Object value = proxy.getProperty(name);
		if (value != null) {
			return (String) value;
		}
		return null;
	}

	public void setCall(SipAudioCall call) {
		this.call = call;
	}

	public SipManager getManager() {
		return this.mSipManager;
	}

}
