package fansirsqi.xposed.sesame.model.modelFieldExt;
import android.app.AlertDialog;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import fansirsqi.xposed.sesame.R;
import fansirsqi.xposed.sesame.model.ModelField;
public class EmptyModelField extends ModelField<Object> {
    private final Runnable clickRunner;
    /** 带上下文的点击回调，用于需要 Context 的操作（如弹出对话框），优先于 clickRunner */
    private final ContextConsumer clickConsumer;

    public EmptyModelField(String code, String name) {
        super(code, name, null);
        this.clickRunner = null;
        this.clickConsumer = null;
    }

    public EmptyModelField(String code, String name, Runnable clickRunner) {
        super(code, name, null);
        this.clickRunner = clickRunner;
        this.clickConsumer = null;
    }

    public EmptyModelField(String code, String name, ContextConsumer clickConsumer) {
        super(code, name, null);
        this.clickRunner = null;
        this.clickConsumer = clickConsumer;
    }

    @Override
    public String getType() {
        return "EMPTY";
    }

    @Override
    public void setObjectValue(Object value) {
    }

    @JsonIgnore
    public View getView(Context context) {
        Button btn = new Button(context);
        btn.setText(getName());
        btn.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        btn.setTextColor(ContextCompat.getColor(context, R.color.selection_color));
        btn.setBackground(ContextCompat.getDrawable(context, R.drawable.dialog_list_button));
        btn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        btn.setMinHeight(150);
        btn.setMaxHeight(180);
        btn.setPaddingRelative(40, 0, 40, 0);
        btn.setAllCaps(false);
        if (clickConsumer != null) {
            btn.setOnClickListener(v -> new AlertDialog.Builder(context)
                    .setTitle("警告")
                    .setMessage("确认执行该操作？")
                    .setPositiveButton(R.string.ok, (dialog, id) -> clickConsumer.accept(context))
                    .setNegativeButton(R.string.cancel, (dialog, id) -> dialog.dismiss())
                    .create()
                    .show());
        } else if (clickRunner != null) {
            btn.setOnClickListener(v -> new AlertDialog.Builder(context)
                    .setTitle("警告")
                    .setMessage("确认执行该操作？")
                    .setPositiveButton(R.string.ok, (dialog, id) -> clickRunner.run())
                    .setNegativeButton(R.string.cancel, (dialog, id) -> dialog.dismiss())
                    .create()
                    .show());
        } else {
            btn.setOnClickListener(v -> Toast.makeText(context, "无配置项", Toast.LENGTH_SHORT).show());
        }
        return btn;
    }

    /**
     * 带 Context 的点击回调接口
     */
    @FunctionalInterface
    public interface ContextConsumer {
        void accept(Context context);
    }
}
