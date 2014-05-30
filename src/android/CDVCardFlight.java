package org.cardflight;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
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
import android.widget.Toast;

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
    CallbackContext onChargeCallback = null;
    CardFlight instance;

    HashMap<String, Card> cards;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        final CDVCardFlight _this = this;

        if (action.equals("setApiTokens")) {
            String apiToken = args.getString(0);
            String accountToken = args.getString(1);

            if (apiToken != null && apiToken.length() > 0 && accountToken != null && accountToken.length() > 0) {
                cards = new HashMap<String, Card>();

                instance = CardFlight.getInstance();
                instance.setApiTokenAndAccountToken(apiToken, accountToken);
                Log.d(LOG_TAG, String.format("API TOKEN: %s ACCOUNT TOKEN: %s\n", instance.getApiToken(), instance.getAccountToken()));
                
                reader = new Reader(this.cordova.getActivity().getApplicationContext(), new CardFlightDeviceHandler() {

                    @Override
                    public void readerIsConnecting() {
                        Log.d(LOG_TAG, "Device connecting");
                        if (_this.onReaderConnectingCallback != null) {
                            openSuccess(_this.onReaderConnectingCallback);
                        }
                    }

                    @Override
                    public void readerIsAttached() {
                        Log.d(LOG_TAG, "Device connected");
                        if (_this.onReaderConnectedCallback != null) {
                            openSuccess(_this.onReaderConnectedCallback);
                        }
                    }

                    @Override
                    public void readerIsDisconnected() {
                        Log.d(LOG_TAG, "Device disconnected");
                        if (_this.onReaderDisconnectedCallback != null) {
                            openSuccess(_this.onReaderDisconnectedCallback);
                        }
                    }

                    @Override
                    public void deviceBeginSwipe() {
                        Log.d(LOG_TAG, "Device begin swipe");
                        Toast.makeText(_this.cordova.getActivity().getApplicationContext(), "Card reader ready", Toast.LENGTH_SHORT).show();
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

                        _this.cards.put(swipeId, card);

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
                
                Toast.makeText(_this.cordova.getActivity().getApplicationContext(), "CardFlight enabled", Toast.LENGTH_SHORT).show();
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
        } else if (action.equals("chargeCard")) {
            String chargeSwipeId = args.getString(0);
            Card chargeCard = cards.get(chargeSwipeId);

            String currency = args.getString(1);
            String description = args.getString(2);
            double amount = args.getDouble(3);

            HashMap chargeDetailsHash = new HashMap();
            chargeDetailsHash.put(chargeCard.REQUEST_KEY_CURRENCY, currency);
            chargeDetailsHash.put(chargeCard.REQUEST_KEY_DESCRIPTION, description);
            chargeDetailsHash.put(chargeCard.REQUEST_KEY_AMOUNT, Double.valueOf(amount));

            _this.onChargeCallback = callbackContext;

            chargeCard.chargeCard(chargeDetailsHash, new CardFlightPaymentHandler() {

                @Override
                public void transactionSuccessful(Charge charge) {
                    JSONObject response = new JSONObject();

                    // try {
                    //     response.put("referenceId", charge.getReferenceId());
                    // } catch (JSONException e) {}

                    _this.onChargeCallback.success(response);
                }

                @Override
                public void transactionFailed(String error) {
                    _this.onChargeCallback.error(error);
                }
            });
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

    private static void openSuccess(CallbackContext context) {
        PluginResult result = new PluginResult(PluginResult.Status.OK);
        result.setKeepCallback(true);
        context.sendPluginResult(result);
    }
    private static void openSuccess(CallbackContext context, JSONObject object) {
        PluginResult result = new PluginResult(PluginResult.Status.OK, object);
        result.setKeepCallback(true);
        context.sendPluginResult(result);
    }
    private static void openError(CallbackContext context) {
        PluginResult result = new PluginResult(PluginResult.Status.ERROR);
        result.setKeepCallback(true);
        context.sendPluginResult(result);
    }
}