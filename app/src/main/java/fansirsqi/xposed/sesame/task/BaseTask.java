package fansirsqi.xposed.sesame.task;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import fansirsqi.xposed.sesame.util.Log;
import fansirsqi.xposed.sesame.util.ThreadUtil;
import lombok.Getter;
public abstract class BaseTask {
    @Getter
    private volatile Thread thread;
    private final Map<String, BaseTask> childTaskMap = new ConcurrentHashMap<>();
    private final AtomicBoolean stopping = new AtomicBoolean(false); // 防止重复停止
    
    public BaseTask() {
        this.thread = null;
    }
    
    /**
     * 检查任务是否正在运行
     * @return true 如果任务线程存在且活跃
     */
    public boolean isRunning() {
        Thread t = this.thread;
        return t != null && t.isAlive();
    }
    public String getId() {
        return toString();
    }
    public abstract Boolean check();
    public abstract void run();
    public void startTask() {
        startTask(false);
    }
    private void startChildTasks() {
        for (BaseTask childTask : childTaskMap.values()) {
            if (childTask != null) {
                try {
                    childTask.startTask(false); // 子任务不强制重启，避免重复启动
                } catch (Exception e) {
                    Log.runtime("启动子任务失败: " + childTask.getId());
                    Log.printStackTrace(e);
                }
            }
        }
    }
    private void stopChildTasks() {
        for (BaseTask childTask : childTaskMap.values()) {
            if (childTask != null) {
                ThreadUtil.shutdownAndWait(childTask.getThread(), -1, TimeUnit.SECONDS);
            }
        }
        childTaskMap.clear();
    }
    /**
     * 启动任务执行
     *
     * @param force 是否强制启动，如果为true则会先停止正在运行的任务再重新启动
     */
    public synchronized void startTask(Boolean force) {
        try {
            Thread currentThread = this.thread; // 本地变量避免竞态条件
            
            if (currentThread != null && currentThread.isAlive()) {
                if (!force) {
                    Log.runtime("任务正在运行中，跳过启动: " + getId());
                    return;
                }
                // 强制启动时，先停止当前任务
                Log.runtime("强制重启任务: " + getId());
                stopTaskInternal(currentThread);
            }
            
            // 重置停止标志
            stopping.set(false);
            
            // 创建新的任务线程
            thread = new Thread(this::run, "Task-" + getId());
            
            // 检查任务启动条件，满足条件则启动任务和子任务
            if (check()) {
                thread.start();
                Log.runtime("任务启动成功: " + getId());
                startChildTasks();
            } else {
                Log.runtime("任务启动条件不满足，取消启动: " + getId());
                thread = null;
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
            thread = null;
        }
    }

    public synchronized void stopTask() {
        if (stopping.getAndSet(true)) {
            Log.runtime("任务正在停止中，跳过重复停止: " + getId());
            return;
        }
        try {
            stopTaskInternal(this.thread);
        } finally {
            stopping.set(false);
        }
    }
    
    /**
     * 内部停止任务方法
     */
    private void stopTaskInternal(Thread targetThread) {
        try {
            Log.runtime("停止任务: " + getId());
            
            // 先停止所有子任务
            for (BaseTask childTask : childTaskMap.values()) {
                if (childTask != null) {
                    try {
                        childTask.stopTask();
                    } catch (Exception e) {
                        Log.runtime("停止子任务失败: " + childTask.getId());
                        Log.printStackTrace(e);
                    }
                }
            }
            childTaskMap.clear();
            
            // 停止主任务线程
            if (targetThread != null && targetThread.isAlive()) {
                targetThread.interrupt();
                try {
                    targetThread.join(5000); // 等待5秒
                    if (targetThread.isAlive()) {
                        Log.runtime("任务未能在5秒内停止: " + getId());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            thread = null;
            Log.runtime("任务已停止: " + getId());
        } catch (Exception e) {
            Log.printStackTrace(e);
            thread = null;
        }
    }
    public static BaseTask newInstance(String id, Runnable runnable) {
        return new BaseTask() {
            @Override
            public String getId() {
                return id;
            }
            @Override
            public void run() {
                runnable.run();
            }
            @Override
            public Boolean check() {
                return true;
            }
        };
    }
}
