package fansirsqi.xposed.sesame.task.otherTask;

import org.json.JSONException;

import java.util.LinkedHashMap;

import fansirsqi.xposed.sesame.model.ModelFields;
import fansirsqi.xposed.sesame.model.ModelGroup;
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField;
import fansirsqi.xposed.sesame.model.modelFieldExt.IntegerModelField;
import fansirsqi.xposed.sesame.model.modelFieldExt.StringModelField;
import fansirsqi.xposed.sesame.task.ModelTask;
import fansirsqi.xposed.sesame.task.TaskCommon;
import fansirsqi.xposed.sesame.util.TimeUtil;

public class OtherTask extends ModelTask {
    private static final String TAG = "OtherTask";
    @Override
    public String getName() {
        return "其他任务";
    }
    @Override
    public ModelGroup getGroup() {
        return ModelGroup.OTHER;
    }
    @Override
    public String getIcon() {
        return "AntSports.png";
    }
    protected Integer executeIntervalInt = 2000;  // 执行间隔
    private final StringModelField startTime = new StringModelField("startTime", "开始执行时间(关闭:-1)", "0600");
    private final IntegerModelField executeInterval = new IntegerModelField("executeInterval", "执行间隔(毫秒)", this.executeIntervalInt);

    private final BooleanModelField contentInteract = new BooleanModelField("contentInteract", "看视频领红包", false);
    private final IntegerModelField contentInteractCount = new IntegerModelField("contentInteractCount", "看视频领红包-线程数", 4);
    private final BooleanModelField contentInteractVV = new BooleanModelField("contentInteractVV", "看短视频领红包", false);

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(this.startTime);  //开始执行时间
        modelFields.addField(this.executeInterval); //执行间隔
        modelFields.addField(this.contentInteract);  //看视频领红包
        modelFields.addField(this.contentInteractCount); //视频线程
        modelFields.addField(this.contentInteractVV);  //看短视频领红包
        return modelFields;
    }

    @Override
    public Boolean check() {
        String value = this.startTime.getValue();
        return Boolean.valueOf(!TaskCommon.IS_ENERGY_TIME.booleanValue()
                && ("-1".equals(value) ||
                TimeUtil.isAfterOrCompareTimeStr(Long.valueOf(System.currentTimeMillis()),
                        value).booleanValue()));
    }

    @Override
    public void run() {
        this.executeIntervalInt = Integer.valueOf(Math.max(this.executeInterval.getValue().intValue(), this.executeIntervalInt.intValue()));
        try {
            new ContentInteract(this).run(this.executeIntervalInt.intValue(), new LinkedHashMap<String, Object>() {
                {
                    put("contentInteract", OtherTask.this.contentInteract.getValue());
                    put("contentInteractCount", OtherTask.this.contentInteractCount.getValue());
                    put("contentInteractVV", OtherTask.this.contentInteractVV.getValue());
                }
            });
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

    }
}
