package com.netflix.msl.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class JSONUtil {

    private JSONUtil() {

    }

    public static final ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        mapper.registerModule(new JsonOrgModule());
    }
    
    public static JSONObject readValue(String src) throws JSONException {
        try {
            return mapper.readValue(src, JSONObject.class);
        } catch (IOException e) {
            throw new JSONException(e);
        }
    }
}
