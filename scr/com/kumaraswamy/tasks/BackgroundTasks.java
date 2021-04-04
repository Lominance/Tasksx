package com.kumaraswamy.tasks;

import android.app.Activity;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.UsesLibraries;
import com.google.appinventor.components.annotations.UsesPermissions;
import com.google.appinventor.components.annotations.UsesServices;
import com.google.appinventor.components.annotations.androidmanifest.ServiceElement;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.OnDestroyListener;
import com.google.appinventor.components.runtime.OnPauseListener;
import com.google.appinventor.components.runtime.OnResumeListener;
import com.google.appinventor.components.runtime.errors.YailRuntimeError;
import com.google.appinventor.components.runtime.util.YailList;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;

import static com.kumaraswamy.tasks.Utils.toObjectArray;

@UsesLibraries(libraries = "beanshell.jar")
@UsesPermissions(permissionNames = "android.permission.READ_EXTERNAL_STORAGE, android.permission.WRITE_EXTERNAL_STORAGE, android.permission.RECEIVE_BOOT_COMPLETED, oppo.permission.OPPO_COMPONENT_SAFE, com.huawei.permission.external_app_settings.USE_COMPONENT")
@UsesServices(services = @ServiceElement(name = "com.kumaraswamy.tasks.ActivityService",
        exported = "true",
        permission = "android.permission.BIND_JOB_SERVICE"))
@DesignerComponent(
        androidMinSdk = 21,
        version = 2,
        versionName = "2.0 A1",
        description = "An extension to run tasks in the Background. An extension developed by Kumaraswamy B.G <br> <br> <a href=\"https://github.com/XomaDev/BackgroundTasks\"><button>BackgroundTasks - GitHub</button></a>",
        helpUrl = "https://community.appinventor.mit.edu/t/background-tasks-extension-1-2-a1/29160?u=kumaraswamy_b.g",
        category = ComponentCategory.EXTENSION,
        nonVisible = true,
        iconName = "aiwebres/icon.png")
@SimpleObject(external = true)
public class BackgroundTasks extends AndroidNonvisibleComponent implements OnDestroyListener, OnPauseListener, OnResumeListener {

    private static final Intent[] RESOLVE_INTENTS = {
            new Intent().setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            new Intent().setComponent(new ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")),
            new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
            new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
            new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")),
            new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
            new Intent().setComponent(new ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
            new Intent().setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
            new Intent().setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
            new Intent().setComponent(new ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
            new Intent().setComponent(new ComponentName("com.htc.pitroad", "com.htc.pitroad.landingpage.activity.LandingPageActivity")),
            new Intent().setComponent(new ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.MainActivity")),
            new Intent().setComponent(new ComponentName("com.transsion.phonemanager", "com.itel.autobootmanager.activity.AutoBootMgrActivity"))
    };

    private int taskID = 0;
    protected static final String ID_SEPARATOR = "/";
    private final String LOG_TAG = "BackgroundTasks";

    protected static final int TASK_CREATE_COMPONENT = 0;
    protected static final int TASK_CREATE_FUNCTION = 1;
    protected static final int TASK_INVOKE_FUNCTION = 2;
    protected static final int TASK_CREATE_VARIABLE = 5;
    protected static final int TASK_EXECUTE_FUNCTION = 6;
    protected static final int TASK_DELAY = 3;
    protected static final int TASK_FINISH = 4;

    protected static final HashMap<Integer, Object[]> pendingTasks = new HashMap<>();
    protected static final ArrayList<String> tasksID = new ArrayList<>();

    protected static Activity activity;
    protected static ComponentContainer componentContainer;

    public BackgroundTasks(ComponentContainer container) {
        super(container.$form());
        componentContainer = container;
        activity = container.$context();

        form.registerForOnPause(this);
        form.registerForOnDestroy(this);
        form.registerForOnResume(this);

        if(!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)) {
            Log.d(LOG_TAG, "This android version is not supported");
            return;
        }

        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @SimpleFunction(description = "It's good to use this block when the screen initializes to prevent causing issues while starting the service in the background especially on Xiaomi and other devices.")
    public void ResolveActivity() {
        for (Intent intent : RESOLVE_INTENTS) {
            if (activity.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                activity.startActivity(intent);
                break;
            }
        }
    }

    @SimpleFunction(description = "Creates a component. No matter if the app is running in the background or the foreground. All you need to do is to specify the component source name and the name which will be used to invoke functions and do other stuff.")
    public void CreateComponent(Object source, String name) {
        String sourceClass = null;

        if(source instanceof Component) {
            sourceClass = source.getClass().getName();
        } else if(source instanceof String) {
            String sourceString = source.toString();

            if(sourceString.contains(".")) {
                sourceClass = sourceString;
            } else {
                sourceClass = "com.google.appinventor.components.runtime" + sourceString;
            }
        }

        Log.d(LOG_TAG, "The source class found is: " + sourceClass);

        try {
            Class.forName(sourceClass);
        } catch( ClassNotFoundException e ) {
            Log.d(LOG_TAG, "The source class is not valid and does not exists: " + sourceClass);
            throw new YailRuntimeError("The component source name does not exists: " + sourceClass, LOG_TAG);
        }

        tasksID.add(taskID + ID_SEPARATOR + TASK_CREATE_COMPONENT);
        pendingTasks.put(taskID, toObjectArray(sourceClass, name));
        taskID++;
    }

    @SimpleFunction(description = "Creates a function of the component ID specified. Specify the component ID and the values. To access the invoked result use the 'invoke:result' value.")
    public void CreateFunction(String id, String name, String functionName, YailList values) {
        tasksID.add(taskID + ID_SEPARATOR + TASK_CREATE_FUNCTION);
        pendingTasks.put(taskID, toObjectArray(name, functionName, values, id));
        taskID++;
    }

    @SimpleFunction(description = "Calls the created function")
    public void CallFunction(String id) {
        tasksID.add(taskID + ID_SEPARATOR + TASK_INVOKE_FUNCTION);
        pendingTasks.put(taskID, toObjectArray(id));
        taskID++;
    }

    @SimpleFunction(description = "Helps you call the created function multiple times")
    public void ExecuteFunction(String id, int times, int interval) {
        tasksID.add(taskID + ID_SEPARATOR + TASK_EXECUTE_FUNCTION);
        pendingTasks.put(taskID, toObjectArray(id, times, interval));
        taskID++;
    }

    @SimpleFunction(description = "Create a variable with the given variable name which can be accessed by [VAR:<NAME>]. For example \"[VAR:Data]\". Use the extra value block and use the value to access the variable.")
    public void CreateVariable(String name, Object value) {
        tasksID.add(taskID + ID_SEPARATOR + TASK_CREATE_VARIABLE);
        pendingTasks.put(taskID, toObjectArray(name, value));
        taskID++;
    }

    @SimpleFunction(description = "Does a delay in the background. You can use it as intervals between function.")
    public void MakeDelay(long millis) {
        tasksID.add(taskID + ID_SEPARATOR + TASK_DELAY);
        pendingTasks.put(taskID, toObjectArray(millis));
        taskID++;
    }

    @SimpleFunction(description = "Executes the code and returns the result. You can use it to perform actions using Java code, calculate sums and return a value. If the value is null or empty, an empty string or text is returned.")
    public Object Interpret(String code) {
        return Utils.interpret(code, activity);
    }

    @SimpleFunction(description = "Make the value from code that will be executed at the background service")
    public Object MakeExtra(String text, boolean code) {
        return new String[] {text, String.valueOf(code)};
    }

    @SimpleFunction(description = "Starts the service. The app must be alive to call this block or put it in the onDestroy or onPause event. This block only helps if the app is compiled and not the companion.")
    public void Start(int id, Calendar instant) {
        if(activity.getPackageName().equals("edu.mit.appinventor.aicompanion3")) {
            Toast.makeText(activity, "This extension does not work in Companion!", Toast.LENGTH_SHORT).show();
            return;
        }

        saveFunctions(id);

        try {
            startTask(instant, id);
            Log.d(LOG_TAG, "Task created");
        } catch (Exception e) {
            Log.d(LOG_TAG, e.getMessage());
        }
    }

    private void saveFunctions(int id) {
        ArrayList<Object> task = new ArrayList<>();
        task.add(tasksID);
        task.add(pendingTasks);

        Utils.saveTask(activity, task, id);
    }

    private void startTask(Calendar calendar, int id) {
        Log.d(LOG_TAG, "Received activity id: " + id);

        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt("JOB_ID", id);

        JobScheduler activityJobScheduler = (JobScheduler) activity.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        ComponentName serviceName = new ComponentName(activity, ActivityService.class);

        JobInfo myJobInfo = new JobInfo.Builder(id, serviceName)
                .setExtras(bundle)
                .setMinimumLatency(calendar.getTimeInMillis() - System.currentTimeMillis())
                .setBackoffCriteria(1, 0)
                .build();

        int resultCode = activityJobScheduler.schedule(myJobInfo);
        Log.d(LOG_TAG, "Condition: " + (resultCode == JobScheduler.RESULT_SUCCESS));
    }

    @SimpleFunction(description = "Flags the Android system that the task is over. This would help save app resources. Call this block when you're done with all you're tasks.")
    public void FinishTask() {
        tasksID.add(taskID + ID_SEPARATOR + TASK_FINISH);
        pendingTasks.put(taskID, toObjectArray());
        taskID++;
    }

    @SimpleFunction(description = "Stops the given service ID. The service will not be executed.")
    public void Cancel(int id) {
        try {
            ((JobScheduler) activity.getSystemService(Context.JOB_SCHEDULER_SERVICE)).cancel(id);
            Log.d(LOG_TAG, "Cancel: " + Utils.clearTask(id, activity));
        } catch (Exception e) {
            Log.d(LOG_TAG, e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        Log.d(LOG_TAG, "On Destroy");
    }

    @Override
    public void onPause() {
        Log.d(LOG_TAG, "On Pause");
    }

    @Override
    public void onResume() {
        Log.d(LOG_TAG, "On Resume");
    }
}
