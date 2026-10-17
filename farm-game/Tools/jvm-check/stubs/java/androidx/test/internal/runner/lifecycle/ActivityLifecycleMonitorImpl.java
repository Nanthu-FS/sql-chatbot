package androidx.test.internal.runner.lifecycle;
import android.app.Activity;
import androidx.test.runner.lifecycle.*;
import java.util.*;
public final class ActivityLifecycleMonitorImpl implements ActivityLifecycleMonitor {
    private final Map<Activity, Stage> stages = new WeakHashMap<>();
    private final List<ActivityLifecycleCallback> callbacks = new ArrayList<>();
    public ActivityLifecycleMonitorImpl() {}
    public void addLifecycleCallback(ActivityLifecycleCallback c) { callbacks.add(c); }
    public void removeLifecycleCallback(ActivityLifecycleCallback c) { callbacks.remove(c); }
    public Stage getLifecycleStageOf(Activity a) { return stages.get(a); }
    public Collection<Activity> getActivitiesInStage(Stage s) {
        List<Activity> out = new ArrayList<>();
        for (Map.Entry<Activity, Stage> e : stages.entrySet()) if (e.getValue() == s) out.add(e.getKey());
        return out;
    }
    public void signalLifecycleChange(Stage stage, Activity activity) {
        stages.put(activity, stage);
        for (ActivityLifecycleCallback c : new ArrayList<>(callbacks)) c.onActivityLifecycleChanged(activity, stage);
    }
}
