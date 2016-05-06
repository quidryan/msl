/**
 * Copyright (c) 2014 Netflix, Inc.  All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package burp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

import com.netflix.msl.util.ReadJson;
import org.json.JSONException;
import org.json.JSONObject;

import com.netflix.msl.MslConstants;
import com.netflix.msl.MslCryptoException;
import com.netflix.msl.MslEncodingException;
import com.netflix.msl.MslError;
import com.netflix.msl.MslException;
import com.netflix.msl.MslKeyExchangeException;
import com.netflix.msl.crypto.ICryptoContext;
import com.netflix.msl.entityauth.EntityAuthenticationFactory;
import com.netflix.msl.entityauth.EntityAuthenticationScheme;
import com.netflix.msl.msg.ErrorHeader;
import com.netflix.msl.msg.MessageHeader;
import com.netflix.msl.tokens.MasterToken;
import com.netflix.msl.tokens.UserIdToken;
import com.netflix.msl.userauth.UserAuthenticationFactory;
import com.netflix.msl.util.Base64;
import com.netflix.msl.util.JsonUtils;

import burp.msl.WiretapException;
import burp.msl.WiretapModule;
import burp.msl.msg.CaptureMessageDebugContext;
import burp.msl.msg.WiretapMessageContext;
import burp.msl.msg.WiretapMessageInputStream;
import burp.msl.util.WiretapMslContext;

/**
 * User: skommidi
 * Date: 9/25/14
 */
public class MSLHttpListener implements IHttpListener {

    private static final String KEY_ENTITY_AUTHENTICATION_DATA = "entityauthdata";
    private static final String KEY_MASTER_TOKEN = "mastertoken";
    private static final String KEY_HEADERDATA = "headerdata";
    private static final String KEY_SIGNATURE = "signature";
    private static final String KEY_SENDER = "sender";
    private static final String KEY_MESSAGE_ID = "messageid";
    private static final String KEY_NON_REPLAYABLE = "nonreplayable";
    private static final String KEY_RENEWABLE = "renewable";
    private static final String KEY_CAPABILITIES = "capabilities";
    private static final String KEY_KEY_REQUEST_DATA = "keyrequestdata";
    private static final String KEY_KEY_RESPONSE_DATA = "keyresponsedata";
    private static final String KEY_USER_AUTHENTICATION_DATA = "userauthdata";
    private static final String KEY_USER_ID_TOKEN = "useridtoken";
    private static final String KEY_SERVICE_TOKENS = "servicetokens";
    private static final String KEY_RECIPIENT = "recipient";
    private static final String KEY_NON_REPLAYABLE_ID = "nonreplayableid";
    private static final String KEY_HANDSHAKE = "handshake";
    private static final String KEY_PAYLOAD = "payload";
    private static final String KEY_ERRORDATA = "errordata";
    private static final String KEY_ERROR_CODE = "errorcode";
    private static final String KEY_INTERNAL_CODE = "internalcode";
    private static final String KEY_ERROR_MESSAGE = "errormsg";
    private static final String KEY_USER_MESSAGE = "usermsg";
    private static final String KEY_DATA = "data";
    private static final String KEY_TOKENDATA = "tokendata";
    private static final String KEY_RENEWAL_WINDOW = "renewalwindow";
    private static final String KEY_EXPIRATION = "expiration";
    private static final String KEY_SEQUENCE_NUMBER = "sequencenumber";
    private static final String KEY_SERIAL_NUMBER = "serialnumber";
    private static final String KEY_SESSIONDATA = "sessiondata";
    private static final String KEY_MASTER_TOKEN_SERIAL_NUMBER = "mtserialnumber";
    private static final String KEY_USERDATA = "userdata";

    public MSLHttpListener() throws MslCryptoException {
        this(null, null);
    }

    public MSLHttpListener(final IBurpExtenderCallbacks callbacks, final IExtensionHelpers helpers) {
        this.callbacks = callbacks;
        this.helpers = helpers;

        // obtain our output streams
        if(this.callbacks != null && this.helpers != null) {
            stdout = new PrintWriter(callbacks.getStdout(), true);
        } else {
            stdout = new PrintWriter(System.out);
        }

        try {
            initializeMsl();
        } catch (final MslCryptoException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void initializeMsl() throws MslCryptoException {
        final WiretapModule module = new WiretapModule();
        final Set<EntityAuthenticationFactory> entityAuthFactories = module.provideEntityAuthFactories();
        final Set<UserAuthenticationFactory> userAuthFactories = module.provideUserAuthFactories();
        this.ctx = new WiretapMslContext(entityAuthFactories, userAuthFactories);
        
        // Change the entity auth data to your usecase
        ctx.setEntityAuthenticationData(EntityAuthenticationScheme.PSK);

        final CaptureMessageDebugContext dbgCtx = new CaptureMessageDebugContext(true, true);
        try {
            msgCtx = new WiretapMessageContext(dbgCtx);
        } catch (final MslKeyExchangeException e) {
            throw new RuntimeException(e.getMessage());
        } catch (final NoSuchAlgorithmException e) {
            throw new RuntimeException(e.getMessage());
        } catch (final InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public void processHttpMessage(final int toolFlag, final boolean messageIsRequest, final IHttpRequestResponse messageInfo) throws WiretapException {

        if(messageIsRequest) {
            // Get MSL Message
            final String body = getBody(messageIsRequest, messageInfo);
            if(body == null)
                return;

            stdout.println();
            stdout.println("Request:");
//            stdout.println(body);
//            stdout.println("Starting MSL Processing");

            processMslMessage(body);

        } else {
            // Get MSL Message
            final String body = getBody(messageIsRequest, messageInfo);
            if(body == null)
                return;

            stdout.println();
            stdout.println("Response:");
//            stdout.println(body);
//            stdout.println("Starting MSL Processing");

            processMslMessage(body);
        }


    }

    protected String getBody(final boolean messageIsRequest, final IHttpRequestResponse messageInfo) {

        String body = null;
        if(messageIsRequest) {
            final IRequestInfo requestInfo = this.helpers.analyzeRequest(messageInfo);

            // Ignore HTTP Get Requests.
            if(requestInfo.getMethod().equalsIgnoreCase("GET")) {
                ignoreNextResponse = true;
                return body;
            }

            ignoreNextResponse = false;

            // Extracting body part of request message, this is actual MSL message.
            final String request = new String(messageInfo.getRequest());
            body = request.substring(requestInfo.getBodyOffset());

        } else {
            if(ignoreNextResponse) {
                return body;
            }

            final IResponseInfo responseInfo = this.helpers.analyzeResponse(messageInfo.getResponse());

            // Extracting body part of request message, this is actual MSL message.
            final String response = new String(messageInfo.getResponse());
            body = response.substring(responseInfo.getBodyOffset());
        }

        return body;
    }

    protected String getBody(final byte[] message) {

        String body = null;

        final IRequestInfo requestInfo = this.helpers.analyzeRequest(null, message);

        // Extracting body part of request message, this is actual MSL message.
        final String request = new String(message);
        body = request.substring(requestInfo.getBodyOffset());

        return body;
    }

    protected String processMslMessage(final String body) throws WiretapException {

        final StringBuilder retData = new StringBuilder("");

        WiretapMessageInputStream mis;
        try {
            final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(body.getBytes());
            mis = new WiretapMessageInputStream(this.ctx, byteArrayInputStream, Charset.defaultCharset(), this.msgCtx.getKeyRequestData(), this.msgCtx.getCryptoContexts());
        } catch (final MslException e) {
            throw new WiretapException(e.getMessage(), e);
        }

        // Check if instance of ErrorHeader
        final ErrorHeader errorHeader = mis.getErrorHeader();
        try {
            if (errorHeader != null) {
                // Create error headerdata JSON Object
                final JSONObject errHeaderJO = new JSONObject();
    
                // if entity auth data is present add that to the JSON object
                if(errorHeader.getEntityAuthenticationData() != null) {
                    try {
                        errHeaderJO.put(KEY_ENTITY_AUTHENTICATION_DATA, errorHeader.getEntityAuthenticationData());
                    } catch (final JSONException e) {
                        throw new WiretapException(e.getMessage(), e);
                    }
                }
    
                final JSONObject errordataJO = new JSONObject();
                try {
                    errordataJO.put(KEY_RECIPIENT, errorHeader.getRecipient());
                    errordataJO.put(KEY_MESSAGE_ID, errorHeader.getMessageId());
                    errordataJO.put(KEY_ERROR_CODE, errorHeader.getErrorCode().intValue());
                    errordataJO.put(KEY_INTERNAL_CODE, errorHeader.getInternalCode());
                    errordataJO.put(KEY_ERROR_MESSAGE, errorHeader.getErrorMessage());
                    errordataJO.put(KEY_USER_MESSAGE, errorHeader.getUserMessage());
    
                    // Add headerdata in clear
                    errHeaderJO.put(KEY_ERRORDATA, errordataJO);
                    stdout.println(errHeaderJO); retData.append(errHeaderJO.toString() + "\n");
                    stdout.println(); retData.append("\n");
                } catch (final JSONException e) {
                    throw new WiretapException(e.getMessage(), e);
                }
    
                return retData.toString();
            }
        } finally {
            try { mis.close(); } catch (final IOException e) {}
        }
        
        try {
            final MessageHeader messageHeader = mis.getMessageHeader();

            // Create message headerdata JSON object
            final JSONObject msgHeaderJO = new JSONObject();

            // if entity auth data is present add that to the JSON object
            if(messageHeader.getEntityAuthenticationData() != null) {
                try {
                    msgHeaderJO.put(KEY_ENTITY_AUTHENTICATION_DATA, messageHeader.getEntityAuthenticationData());
                } catch (final JSONException e) {
                    throw new WiretapException(e.getMessage(), e);
                }
            }

            MasterToken masterToken = null;

            // if master token is present add that to the JSON object
            if(messageHeader.getMasterToken() != null) {
                masterToken = messageHeader.getMasterToken();
                try {
                    final JSONObject parsedMasterTokenJO = parseMasterToken(masterToken);
                    msgHeaderJO.put(KEY_MASTER_TOKEN, parsedMasterTokenJO);
                } catch (final JSONException e) {
                    throw new WiretapException(e.getMessage(), e);
                } catch (final MslException e) {
                    throw new WiretapException(e.getMessage(), e);
                }
            }

            final JSONObject headerdataJO = new JSONObject();
            try {
                headerdataJO.put(KEY_SENDER, messageHeader.getSender());
                headerdataJO.put(KEY_RECIPIENT, messageHeader.getRecipient());
                headerdataJO.put(KEY_MESSAGE_ID, messageHeader.getMessageId());
                headerdataJO.put(KEY_NON_REPLAYABLE_ID, messageHeader.getNonReplayableId());
                headerdataJO.put(KEY_RENEWABLE, messageHeader.isRenewable());
                headerdataJO.put(KEY_HANDSHAKE, messageHeader.isHandshake());
                headerdataJO.put(KEY_CAPABILITIES, messageHeader.getMessageCapabilities());
                if(!messageHeader.getKeyRequestData().isEmpty())
                    headerdataJO.put(KEY_KEY_REQUEST_DATA, JsonUtils.createArray(messageHeader.getKeyRequestData()));
                if(messageHeader.getKeyResponseData() != null) {
                    final JSONObject keyResponseDataJO = ReadJson.readValue(messageHeader.getKeyResponseData().toJSONString());
                    if(messageHeader.getKeyResponseData().getMasterToken() != null) {
                        masterToken = messageHeader.getKeyResponseData().getMasterToken();
                        keyResponseDataJO.remove(KEY_MASTER_TOKEN);
                        final JSONObject parsedMasterTokenJO = parseMasterToken(messageHeader.getKeyResponseData().getMasterToken());
                        keyResponseDataJO.put(KEY_MASTER_TOKEN, parsedMasterTokenJO);
                    }
                    headerdataJO.put(KEY_KEY_RESPONSE_DATA, keyResponseDataJO);
                }
                if(messageHeader.getUserAuthenticationData() != null)
                    headerdataJO.put(KEY_USER_AUTHENTICATION_DATA, messageHeader.getUserAuthenticationData());
                if(messageHeader.getUserIdToken() != null) {
                    headerdataJO.put(KEY_USER_ID_TOKEN, parseUserIdToken(messageHeader.getUserIdToken(), masterToken));
                }
                if(!messageHeader.getServiceTokens().isEmpty())
                    headerdataJO.put(KEY_SERVICE_TOKENS, JsonUtils.createArray(messageHeader.getServiceTokens()));

                // Add headerdata in clear
                msgHeaderJO.put(KEY_HEADERDATA, headerdataJO);
                stdout.println(msgHeaderJO); retData.append(msgHeaderJO.toString() + "\n");
            } catch (final JSONException e) {
                throw new WiretapException(e.getMessage(), e);
            } catch (final MslException e) {
                throw new WiretapException(e.getMessage(), e);
            }

            try {
                JSONObject payloadTokenJO;
                while((payloadTokenJO = mis.nextPayload()) != null) {
                    final String data = new String(Base64.decode(payloadTokenJO.getString(KEY_DATA)));
                    payloadTokenJO.remove(KEY_DATA);
                    payloadTokenJO.put(KEY_DATA, data);
    
                    final JSONObject payloadJO = new JSONObject();
                    payloadJO.put(KEY_PAYLOAD, payloadTokenJO);
                    stdout.println(payloadJO); retData.append(payloadJO.toString() + "\n");
                }
                stdout.println(); retData.append("\n");
            } catch (final Exception e) {
                throw new WiretapException(e.getMessage(), e);
            }
            stdout.flush();
        } finally {
            try { mis.close(); } catch (final IOException e) {}
        }

        return retData.toString();
    }

    private JSONObject parseUserIdToken(final UserIdToken userIdToken, final MasterToken masterToken) throws JSONException, MslException {

        final JSONObject userIdTokenJO = ReadJson.readValue(userIdToken.toJSONString());
        final ICryptoContext cryptoContext = ctx.getMslCryptoContext();

        byte[] tokendata;
        // Verify the JSON representation.
        boolean verified = false;
        try {
            try {
                tokendata = Base64.decode(userIdTokenJO.getString(KEY_TOKENDATA));
            } catch (final IllegalArgumentException e) {
                throw new MslEncodingException(MslError.USERIDTOKEN_TOKENDATA_INVALID, "useridtoken " + userIdTokenJO.toString(), e).setMasterToken(masterToken);
            }
            if (tokendata == null || tokendata.length == 0)
                throw new MslEncodingException(MslError.USERIDTOKEN_TOKENDATA_MISSING, "useridtoken " + userIdTokenJO.toString()).setMasterToken(masterToken);
            byte[] signature;
            try {
                signature = Base64.decode(userIdTokenJO.getString(KEY_SIGNATURE));
            } catch (final IllegalArgumentException e) {
                throw new MslEncodingException(MslError.USERIDTOKEN_SIGNATURE_INVALID, "useridtoken " + userIdTokenJO.toString(), e).setMasterToken(masterToken);
            }
            verified = cryptoContext.verify(tokendata, signature);
        } catch (final JSONException e) {
            throw new MslEncodingException(MslError.JSON_PARSE_ERROR, "useridtoken " + userIdTokenJO.toString(), e).setMasterToken(masterToken);
        }

        // Pull the token data.
        final String tokenDataJson = new String(tokendata, MslConstants.DEFAULT_CHARSET);
        final JSONObject tokenDataJO;
        byte[] userdata;
        long mtSerialNumber;
        try {
            tokenDataJO = ReadJson.readValue(tokenDataJson);
            final long renewalWindow = tokenDataJO.getLong(KEY_RENEWAL_WINDOW);
            final long expiration = tokenDataJO.getLong(KEY_EXPIRATION);
            if (expiration < renewalWindow)
                throw new MslException(MslError.USERIDTOKEN_EXPIRES_BEFORE_RENEWAL, "usertokendata " + tokenDataJson).setMasterToken(masterToken);
            mtSerialNumber = tokenDataJO.getLong(KEY_MASTER_TOKEN_SERIAL_NUMBER);
            if (mtSerialNumber < 0 || mtSerialNumber > MslConstants.MAX_LONG_VALUE)
                throw new MslException(MslError.USERIDTOKEN_MASTERTOKEN_SERIAL_NUMBER_OUT_OF_RANGE, "usertokendata " + tokenDataJson).setMasterToken(masterToken);
            final long serialNumber = tokenDataJO.getLong(KEY_SERIAL_NUMBER);
            if (serialNumber < 0 || serialNumber > MslConstants.MAX_LONG_VALUE)
                throw new MslException(MslError.USERIDTOKEN_SERIAL_NUMBER_OUT_OF_RANGE, "usertokendata " + tokenDataJson).setMasterToken(masterToken);
            final byte[] ciphertext;
            try {
                ciphertext = Base64.decode(tokenDataJO.getString(KEY_USERDATA));
            } catch (final IllegalArgumentException e) {
                throw new MslException(MslError.USERIDTOKEN_USERDATA_INVALID, tokenDataJO.getString(KEY_USERDATA)).setMasterToken(masterToken);
            }
            if (ciphertext == null || ciphertext.length == 0)
                throw new MslException(MslError.USERIDTOKEN_USERDATA_MISSING, tokenDataJO.getString(KEY_USERDATA)).setMasterToken(masterToken);
            userdata = (verified) ? cryptoContext.decrypt(ciphertext) : null;
            tokenDataJO.remove(KEY_USERDATA);
        } catch (final JSONException e) {
            throw new MslEncodingException(MslError.USERIDTOKEN_TOKENDATA_PARSE_ERROR, "usertokendata " + tokenDataJson, e).setMasterToken(masterToken);
        } catch (final MslCryptoException e) {
            e.setMasterToken(masterToken);
            throw e;
        }

        // Pull the user data.
        if (userdata != null) {
            final String userDataJson = new String(userdata, MslConstants.DEFAULT_CHARSET);
            try {
                final JSONObject userDataJO = ReadJson.readValue(userDataJson);
                tokenDataJO.put(KEY_USERDATA, userDataJO);
            } catch (final JSONException e) {
                throw new MslEncodingException(MslError.USERIDTOKEN_USERDATA_PARSE_ERROR, "userdata " + userDataJson, e).setMasterToken(masterToken);
            }
        }

        // Verify serial numbers.
        if (masterToken == null || mtSerialNumber != masterToken.getSerialNumber())
            throw new MslException(MslError.USERIDTOKEN_MASTERTOKEN_MISMATCH, "uit mtserialnumber " + mtSerialNumber + "; mt " + masterToken).setMasterToken(masterToken);

        return tokenDataJO;
    }

    private JSONObject parseMasterToken(final MasterToken masterToken) throws JSONException, MslException {

        final JSONObject masterTokenJO = ReadJson.readValue(masterToken.toJSONString());
        final ICryptoContext cryptoContext = ctx.getMslCryptoContext();

        byte[] tokendata;
        // Verify the JSON representation.
        boolean verified = false;
        try {
            try {
                tokendata = Base64.decode(masterTokenJO.getString(KEY_TOKENDATA));
            } catch (final IllegalArgumentException e) {
                throw new MslEncodingException(MslError.MASTERTOKEN_TOKENDATA_INVALID, "mastertoken " + masterTokenJO.toString(), e);
            }
            if (tokendata == null || tokendata.length == 0)
                throw new MslEncodingException(MslError.MASTERTOKEN_TOKENDATA_MISSING, "mastertoken " + masterTokenJO.toString());
            byte[] signature;
            try {
                signature = Base64.decode(masterTokenJO.getString(KEY_SIGNATURE));
            } catch (final IllegalArgumentException e) {
                throw new MslEncodingException(MslError.MASTERTOKEN_SIGNATURE_INVALID, "mastertoken " + masterTokenJO.toString(), e);
            }
            verified = cryptoContext.verify(tokendata, signature);
        } catch (final JSONException e) {
            throw new MslEncodingException(MslError.JSON_PARSE_ERROR, "mastertoken " + masterTokenJO.toString(), e);
        }

        // Pull the token data.
        final String tokenDataJson = new String(tokendata, MslConstants.DEFAULT_CHARSET);
        final JSONObject tokenDataJO;
        byte[] sessiondata;
        try {
            tokenDataJO = ReadJson.readValue(tokenDataJson);
            final long renewalWindow = tokenDataJO.getLong(KEY_RENEWAL_WINDOW);
            final long expiration = tokenDataJO.getLong(KEY_EXPIRATION);
            if (expiration < renewalWindow)
                throw new MslException(MslError.MASTERTOKEN_EXPIRES_BEFORE_RENEWAL, "mastertokendata " + tokenDataJson);
            final long sequenceNumber = tokenDataJO.getLong(KEY_SEQUENCE_NUMBER);
            if (sequenceNumber < 0 || sequenceNumber > MslConstants.MAX_LONG_VALUE)
                throw new MslException(MslError.MASTERTOKEN_SEQUENCE_NUMBER_OUT_OF_RANGE, "mastertokendata " + tokenDataJson);
            final long serialNumber = tokenDataJO.getLong(KEY_SERIAL_NUMBER);
            if (serialNumber < 0 || serialNumber > MslConstants.MAX_LONG_VALUE)
                throw new MslException(MslError.MASTERTOKEN_SERIAL_NUMBER_OUT_OF_RANGE, "mastertokendata " + tokenDataJson);
            final byte[] ciphertext;
            try {
                ciphertext = Base64.decode(tokenDataJO.getString(KEY_SESSIONDATA));
            } catch (final IllegalArgumentException e) {
                throw new MslEncodingException(MslError.MASTERTOKEN_SESSIONDATA_INVALID, tokenDataJO.getString(KEY_SESSIONDATA));
            }
            if (ciphertext == null || ciphertext.length == 0)
                throw new MslEncodingException(MslError.MASTERTOKEN_SESSIONDATA_MISSING, tokenDataJO.getString(KEY_SESSIONDATA));
            sessiondata = (verified) ? cryptoContext.decrypt(ciphertext) : null;
            tokenDataJO.remove(KEY_SESSIONDATA);
        } catch (final JSONException e) {
            throw new MslEncodingException(MslError.MASTERTOKEN_TOKENDATA_PARSE_ERROR, "mastertokendata " + tokenDataJson, e);
        }

        // Pull the session data.
        if (sessiondata != null) {
            // Parse JSON.
            final String sessionDataJson = new String(sessiondata, MslConstants.DEFAULT_CHARSET);
            try {
                final JSONObject sessionDataJO = ReadJson.readValue(sessionDataJson);
                tokenDataJO.put(KEY_SESSIONDATA, sessionDataJO);
            } catch (final JSONException e) {
                throw new MslEncodingException(MslError.MASTERTOKEN_SESSIONDATA_PARSE_ERROR, "sessiondata " + sessionDataJson, e);
            }
        }

        return tokenDataJO;
    }

    private final PrintWriter stdout;
    private final IBurpExtenderCallbacks callbacks;
    private final IExtensionHelpers helpers;
    private WiretapMessageContext msgCtx;
    private WiretapMslContext ctx = null;
    private boolean ignoreNextResponse = false;
}
