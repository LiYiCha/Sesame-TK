package fansirsqi.xposed.sesame.ui;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import fansirsqi.xposed.sesame.R;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.ToastUtil;
/**
 * 扩展功能页面
 */
public class ExtendActivity extends BaseActivity {
    private String debugTips;
    //------------------------------
    // 添加点击时间间隔限制常量
    private static final long CLICK_INTERVAL_THRESHOLD = 30000; // 30秒
    // 点击时间记录
    private long lastReRunClickTime = 0;
    //------------------------------
    /**
     * 初始化Activity
     *
     * @param savedInstanceState 保存的实例状态
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extend); // 设置布局文件
        debugTips = getString(R.string.debug_tips);
        // 初始化按钮并设置点击事件
        initButtonsAndSetListeners();
    }
    /**
     * 初始化按钮并设置监听器
     */
    private void initButtonsAndSetListeners() {
        // 定义按钮变量并绑定按钮到对应的View
        Button btnGetTreeItems = findViewById(R.id.get_tree_items);
        Button btnGetNewTreeItems = findViewById(R.id.get_newTree_items);
        //完善下面这两个按钮对应功能
        Button btnQueryAreaTrees = findViewById(R.id.query_area_trees);
        Button btnGetUnlockTreeItems = findViewById(R.id.get_unlock_treeItems);
        // 设置Activity标题
        setBaseTitle(getString(R.string.extended_func));
        // 为每个按钮设置点击事件
        btnGetTreeItems.setOnClickListener(new TreeItemsOnClickListener());
        btnGetNewTreeItems.setOnClickListener(new NewTreeItemsOnClickListener());
        btnQueryAreaTrees.setOnClickListener(new AreaTreesOnClickListener());
        btnGetUnlockTreeItems.setOnClickListener(new UnlockTreeItemsOnClickListener());

        //重新运行
        Button restart = findViewById(R.id.re_run);
        restart.setOnClickListener(new ReRun());
        //继续运行
        Button continueRun = findViewById(R.id.keep_run);
        continueRun.setOnClickListener(new ContinueRun());
        //重新登录
        Button relogin = findViewById(R.id.relogin);
        relogin.setOnClickListener(new ReLogin());
        //模块重新加载
        Button restartModule = findViewById(R.id.restart_module);
        restartModule.setOnClickListener(new RestartModule());
        // 状态检查
        Button checkStatus = findViewById(R.id.check_status);
        checkStatus.setOnClickListener(new CheckStatusListener());

    }
    /**
     * 发送广播事件
     *
     * @param type 广播类型
     */
    private void sendItemsBroadcast(String type) {
        Intent intent = new Intent("com.eg.android.AlipayGphone.sesame.rpctest");
        intent.putExtra("method", "");
        intent.putExtra("data", "");
        intent.putExtra("type", type);
        intent.putExtra("startTime", SystemClock.elapsedRealtime());
        sendBroadcast(intent); // 发送广播
        Log.debug("扩展工具主动调用广播查询📢：" + type);
    }

    /**
     * 检查运行状态监听器
     */
    private class CheckStatusListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            // 发送检查状态广播
            Intent intent = new Intent("com.eg.android.AlipayGphone.sesame.checkStatus");
            sendBroadcast(intent);
            ToastUtil.makeText(ExtendActivity.this, "已发送状态检查请求", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 重新运行任务
     */
    private class ReRun implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastReRunClickTime < CLICK_INTERVAL_THRESHOLD) {
                long remainingTime = (CLICK_INTERVAL_THRESHOLD - (currentTime - lastReRunClickTime)) / 1000;
                ToastUtil.makeText(ExtendActivity.this, "操作过于频繁，请" + remainingTime + "秒后再试", Toast.LENGTH_SHORT).show();
                return;
            }
            // 更新上次点击时间
            lastReRunClickTime = currentTime;
            // 发送全局广播给 ApplicationHook
            Intent intent = new Intent("com.eg.android.AlipayGphone.sesame.rerun");
            intent.putExtra("actionType", "rerun"); // 指定为重新运行
            sendBroadcast(intent);
            ToastUtil.makeText(ExtendActivity.this, "已发送重新执行任务请求", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 继续运行任务
     */
    private class ContinueRun implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastReRunClickTime < CLICK_INTERVAL_THRESHOLD) {
                long remainingTime = (CLICK_INTERVAL_THRESHOLD - (currentTime - lastReRunClickTime)) / 1000;
                ToastUtil.makeText(ExtendActivity.this, "操作过于频繁，请" + remainingTime + "秒后再试", Toast.LENGTH_SHORT).show();
                return;
            }
            // 更新上次点击时间
            lastReRunClickTime = currentTime;
            // 发送全局广播给 ApplicationHook
            Intent intent = new Intent("com.eg.android.AlipayGphone.sesame.rerun");
            intent.putExtra("actionType", "continue"); // 指定为继续运行
            sendBroadcast(intent);
            ToastUtil.makeText(ExtendActivity.this, "已发送继续执行任务请求", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 重新登录
     */
    private class ReLogin implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastReRunClickTime < CLICK_INTERVAL_THRESHOLD) {
                long remainingTime = (CLICK_INTERVAL_THRESHOLD - (currentTime - lastReRunClickTime)) / 1000;
                ToastUtil.makeText(ExtendActivity.this, "操作过于频繁，请" + remainingTime + "秒后再试", Toast.LENGTH_SHORT).show();
                return;
            }
            // 更新上次点击时间
            lastReRunClickTime = currentTime;
            // 发送重新登录广播
            Intent intent = new Intent("com.eg.android.AlipayGphone.sesame.reLogin");
            sendBroadcast(intent);
            ToastUtil.makeText(ExtendActivity.this, "已发送重新登录请求", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 模块重新加载
     */
    private class RestartModule implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastReRunClickTime < CLICK_INTERVAL_THRESHOLD) {
                long remainingTime = (CLICK_INTERVAL_THRESHOLD - (currentTime - lastReRunClickTime)) / 1000;
                ToastUtil.makeText(ExtendActivity.this, "操作过于频繁，请" + remainingTime + "秒后再试", Toast.LENGTH_SHORT).show();
                return;
            }
            // 更新上次点击时间
            lastReRunClickTime = currentTime;
            // 发送模块重新加载广播
            Intent intent = new Intent("com.eg.android.AlipayGphone.sesame.restart");
            sendBroadcast(intent);
            ToastUtil.makeText(ExtendActivity.this, "已发送模块重新加载请求", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 获取树项目按钮的点击监听器
     */
    private class TreeItemsOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            sendItemsBroadcast("getTreeItems");
            ToastUtil.makeText(ExtendActivity.this, debugTips, Toast.LENGTH_SHORT).show();
        }
    }
    /**
     * 获取新树项目按钮的点击监听器
     */
    private class NewTreeItemsOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            sendItemsBroadcast("getNewTreeItems");
            ToastUtil.makeText(ExtendActivity.this, debugTips, Toast.LENGTH_SHORT).show();
        }
    }
    /**
     * 查询未解锁🔓地区
     */
    private class AreaTreesOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            sendItemsBroadcast("queryAreaTrees");
            ToastUtil.makeText(ExtendActivity.this, debugTips, Toast.LENGTH_SHORT).show();
        }
    }
    /**
     * 查询未解锁🔓🌳木项目
     */
    private class UnlockTreeItemsOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            sendItemsBroadcast("getUnlockTreeItems");
            ToastUtil.makeText(ExtendActivity.this, debugTips, Toast.LENGTH_SHORT).show();
        }
    }
}
