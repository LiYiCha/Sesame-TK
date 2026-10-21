package fansirsqi.xposed.sesame.task.otherTask;

import org.json.JSONException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import fansirsqi.xposed.sesame.data.Status;

public abstract class BaseCommTask {
    protected String displayName;
    protected CompletedKeyEnum hoursKeyEnum;
    protected CompletedKeyEnum keyEnum;
    protected int executeIntervalInt = 2000;
    protected Map<String, Object> mapHandler = new HashMap<>();
    protected Set<String> friendList = new HashSet<>();
    protected final String TAG = getClass().getSimpleName();

    protected abstract void handle() throws JSONException;

    public final void run(int i) throws JSONException {
        run(i, null);
    }

    public final void run(int i, Map<String, Object> map) throws JSONException {
        run(i, map, null);
    }

    public final void run(int i, Map<String, Object> map, Set<String> set) throws JSONException {
        this.mapHandler = map;
        this.executeIntervalInt = i;
        this.friendList = set;

        // 判断是否已完成当天的任务
        if (!isTaskCompletedToday()) {
            handle(); // 执行任务逻辑
            markTaskCompletedToday(); // 标记任务为已完成
        }
    }

    /**
     * 判断任务是否已完成（基于 Status 的状态管理）
     */
    private boolean isTaskCompletedToday() {
        CompletedKeyEnum completedKeyEnum = this.keyEnum;
        if (completedKeyEnum != null && Status.hasFlagToday(completedKeyEnum.name())) {
            return true; // 已完成当天的任务
        }

        CompletedKeyEnum hoursKeyEnum = this.hoursKeyEnum;
        return hoursKeyEnum != null && Status.hasFlagToday(hoursKeyEnum.name()); // 已完成当前时间段的任务
// 任务未完成
    }

    /**
     * 标记任务为已完成（基于 Status 的状态管理）
     */
    private void markTaskCompletedToday() {
        CompletedKeyEnum hoursKeyEnum = this.hoursKeyEnum;
        if (hoursKeyEnum != null) {
            Status.setFlagToday(hoursKeyEnum.name()); // 标记时间段任务为已完成
        }
    }





}
