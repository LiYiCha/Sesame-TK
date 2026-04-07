package fansirsqi.xposed.sesame.model.modelFieldExt;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Objects;

public class ExtensionsHandle {
    private static final String TAG = ExtensionsHandle.class.getSimpleName();


    public static Object handleAlphaRequest(String type, String fun, Object data) {
        try {
            return Class.forName("fansirsqi.xposed.sesame.model.modelFieldExt.ExtensionsHandle")
                    .getMethod("handleAlphaRequest", String.class, String.class, Object.class)
                    .invoke(null, type, fun, data);
        } catch (Exception e) {
            return null;
        }
    }


}
