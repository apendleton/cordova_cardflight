package org.cardflight;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.getcardflight.models.Card;
import com.getcardflight.models.CardFlight;
import com.getcardflight.models.Charge;
import com.getcardflight.models.Reader;
import com.getcardflight.interfaces.CardFlightDeviceHandler;
import com.getcardflight.interfaces.CardFlightPaymentHandler;
import com.getcardflight.views.CustomView;

import android.util.Log;

import java.util.UUID;
import java.util.HashMap;

public class CDVCardFlight extends CordovaPlugin {

    private static final String LOG_TAG = "CDVCardFlight";
    private Reader reader;

    CallbackContext onSwipeCallback = null;
    CallbackContext onReaderAttachedCallback = null;
    CallbackContext onReaderDisconnectedCallback = null;
    CallbackContext onReaderConnectingCallback = null;
    CallbackContext onReaderConnectedCallback = null;
    CardFlight instance;

    HashMap<String, Card> cards;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("setApiTokens")) {
            String apiToken = args.getString(0);
            String accountToken = args.getString(1);

            if (apiToken != null && apiToken.length() > 0 && accountToken != null && accountToken.length() > 0) {
                cards = new HashMap<String, Card>();

                instance = CardFlight.getInstance();
                instance.setApiTokenAndAccountToken(apiToken, accountToken);
                Log.d(LOG_TAG, String.format("API TOKEN: %s ACCOUNT TOKEN: %s\n", instance.getApiToken(), instance.getAccountToken()));

                final CDVCardFlight _this = this;
                
                reader = new Reader(this.cordova.getActivity().getApplicationContext(), new CardFlightDeviceHandler() {

                    @Override
                    public void readerIsConnecting() {
                        Log.d(LOG_TAG, "Device connecting");
                        if (_this.onReaderConnectingCallback != null) {
                            _this.onReaderConnectingCallback.success();
                        }
                    }

                    @Override
                    public void readerIsAttached() {
                        Log.d(LOG_TAG, "Device connected");
                        if (_this.onReaderConnectedCallback != null) {
                            _this.onReaderConnectedCallback.success();
                        }
                    }

                    @Override
                    public void readerIsDisconnected() {
                        Log.d(LOG_TAG, "Device disconnected");
                        if (_this.onReaderDisconnectedCallback != null) {
                            _this.onReaderDisconnectedCallback.success();
                        }
                    }

                    @Override
                    public void deviceBeginSwipe() {
                        Log.d(LOG_TAG, "Device begin swipe");
                    }

                    @Override
                    public void readerCardResponse(Card card) {
                        Log.d(LOG_TAG, "Device swipe completed");
                        JSONObject response = new JSONObject();

                        String swipeId = UUID.randomUUID().toString();

                        try {
                            response.put("name", card.getName());
                            response.put("cardNumber", card.getCardNumber());
                            response.put("CVVCode", card.getCVVCode());
                            response.put("ExpirationMonth", card.getExpirationMonth());
                            response.put("ExpirationYear", card.getExpirationYear());
                            response.put("swipeId", swipeId);
                        } catch (JSONException e) {}
                        
                        _this.cards.put("swipeId", card);

                        _this.onSwipeCallback.success(response);

                        // callbacks for swipe are one-time use
                        _this.onSwipeCallback = null;
                    }

                    @Override
                    public void deviceSwipeFailed() {
                        Log.d(LOG_TAG, "Device swipe failed");
                        JSONObject response = new JSONObject();

                        try {
                            response.put("error", "Device swipe failed");
                        } catch (JSONException e) {}

                        _this.onSwipeCallback.error(response);
                        _this.onSwipeCallback = null;
                    }

                    @Override
                    public void deviceSwipeTimeout() {
                        Log.d(LOG_TAG, "Device swipe time out");
                        JSONObject response = new JSONObject();

                        try {
                            response.put("error", "Device swipe time out");
                        } catch (JSONException e) {}

                        _this.onSwipeCallback.error(response);
                        _this.onSwipeCallback = null;
                    }

                    @Override
                    public void deviceNotSupported() {
                        Log.d(LOG_TAG, "Device not supported");
                    }

                });

                callbackContext.success();
                return true;
            } else {
                Log.d(LOG_TAG, "Error setting API token");
                callbackContext.error("Error setting API token");
                return true;
            }
        } else if (action.equals("swipeCard")) {
            this.onSwipeCallback = callbackContext;
            this.reader.beginSwipe();
            return true;
        } else if (action.equals("startOnReaderAttached")) {
            this.onReaderConnectingCallback = callbackContext;
            return true;
        } else if (action.equals("startOnReaderDisconnected")) {
            this.onReaderDisconnectedCallback = callbackContext;
            return true;
        } else if (action.equals("startOnReaderConnected")) {
            this.onReaderConnectedCallback = callbackContext;
            return true;
        } else if (action.equals("startOnReaderConnecting")) {
            this.onReaderConnectingCallback = callbackContext;
            return true;
        } else {
            return false;
        }
    }
}