package fansirsqi.xposed.sesame.hook.rpc.bridge;

public interface RpcCallback {
    void onSuccess(String response);
    void onFailure(Throwable t);
}
