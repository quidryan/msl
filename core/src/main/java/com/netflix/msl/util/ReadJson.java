package com.netflix.msl.util;

import org.json.JSONException;
import org.json.JSONObject;

public class ReadJson {

    private ReadJson() {

    }

    public interface JsonReader {
        JSONObject readValue(String src) throws JSONException;
    }

    public static class OrgJsonReadJson implements JsonReader {

        @Override
        public JSONObject readValue(String src) throws JSONException {
            return new JSONObject(src);
        }
    }

//    public static class JacksonReadJson implements JsonReader {
//
//        public final ObjectMapper mapper;
//
//        public JacksonReadJson() {
//            mapper = new ObjectMapper();
//            mapper.registerModule(new JsonOrgModule());
//        }
//
//        @Override
//        public JSONObject readValue(String src) throws JSONException {
//            try {
//                return mapper.readValue(src, JSONObject.class);
//            } catch (IOException e) {
//                throw new JSONException(e);
//            }
//        }
//    }

    /**
     * Defaulting implementation to org.json
     */
    private static JsonReader impl = new OrgJsonReadJson();

    public static void setImpl(JsonReader jsonReader) {
        impl = jsonReader;
    }

    public static JSONObject readValue(String str) {
        return impl.readValue(str);
    }
}
