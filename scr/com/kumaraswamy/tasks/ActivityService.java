package com.kumaraswamy.tasks;

import android.app.Activity;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.util.Log;
import com.google.appinventor.components.runtime.AndroidViewComponent;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.Form;
import com.google.appinventor.components.runtime.util.YailList;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static com.kumaraswamy.tasks.Utils.findMethod;

public class ActivityService extends JobService {
    private final String TAG = "BackgroundTasks.ActivityService";

    private final HashMap<String, Component> componentsBuilt = new HashMap<>();
    private final HashMap<String, Object[]> createdFunctions = new HashMap<>();
    private final HashMap<String, Object> createdVariables = new HashMap<>();

    private CComponentContainer container;
    private AActivity activity;
    private FForm form;

    private Object invokeResult = null;

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        initService(jobParameters);
        return true;
    }

    private void doBackgroundWork(final JobParameters jobParameters) {
        Log.d(TAG, "The service is started");
        new Thread(new Runnable() {
            @Override
            public void run() {
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        processTasks(jobParameters);
                    }
                }, 100);
            }
        }).start();
    }

    private void processTasks(JobParameters jobParameters) {
        int jobID = jobParameters.getExtras().getInt("JOB_ID");
        ArrayList<Object> tasksRead = Utils.readTask(getApplicationContext(), jobID);

        Log.d(TAG, "Got data from database: " + tasksRead.toString());

        ArrayList<String> tasksID = (ArrayList<String>) tasksRead.get(0);
        HashMap<String, Object[]> pendingTasks = (HashMap<String, Object[]>) tasksRead.get(1);

        Log.d(TAG, "Total tasks: " + tasksID.size());

        for (String tasks: tasksID) {
            Log.d(TAG, "Processing the task: " + tasks);

            String[] taskData = tasks.split(BackgroundTasks.ID_SEPARATOR);

            int taskID = Integer.parseInt(taskData[0]);
            int taskType = Integer.parseInt(taskData[1]);

            Log.d(TAG, "Task ID: " + taskID + ", taskType: " + taskType);

            Object[] taskValues = pendingTasks.get(taskID);
            Log.d(TAG, "Task values: " + Arrays.toString(taskValues));

            if (taskType == BackgroundTasks.TASK_CREATE_COMPONENT) {
                Log.d(TAG, "Received task to create a component");
                createComponent(taskValues);

            } else if (taskType == BackgroundTasks.TASK_CREATE_FUNCTION) {
                Log.d(TAG, "Received task to create a function");
                createFunction(taskValues);
            } else if(taskType == BackgroundTasks.TASK_INVOKE_FUNCTION) {
                Log.d(TAG, "Received task to invoke a function");
                invokeFunction(taskValues[0].toString());
            } else if(taskType == BackgroundTasks.TASK_DELAY) {
                try {
                    Thread.sleep((Long) taskValues[0]);
                } catch (InterruptedException e) {
                    Log.e(TAG, e.getMessage());
                }
            } else if(taskType == BackgroundTasks.TASK_CREATE_VARIABLE) {
                Log.d(TAG, "Received task to create a variable");
                createVariable(taskValues);
            } else if(taskType == BackgroundTasks.TASK_FINISH) {
                Log.d(TAG, "Received task to end the task");
                jobFinished(jobParameters, false);
            } else if(taskType == BackgroundTasks.TASK_EXECUTE_FUNCTION) {
                Log.d(TAG, "processTasks: Received task to execute a function");
                executeFunction(taskValues);
            }
        }
    }

    private void executeFunction(Object[] taskValues) {
        Log.d(TAG, "executeFunction: task values: " + Arrays.toString(taskValues));

        final String function = taskValues[0].toString();

        final int times = (int) taskValues[1];
        final int interval = (int) taskValues[2];

        final int[] timesExecuted = {0};
        final Timer timer = new Timer();

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if(times == timesExecuted[0]) {
                    timer.cancel();
                } else {
                    Log.d(TAG, "run: Executing the function name: " + function);
                    invokeFunction(function);
                    timesExecuted[0]++;
                }
            }
        }, 0, interval);
    }

    private void createVariable(Object[] taskValues) {
        Log.d(TAG, "createVariable: " + createdVariables.toString());

        String variableName = taskValues[0].toString();
        Object variableValue = taskValues[1];

        Object result = variableValue instanceof String ? variableValue.toString() : processValue(variableValue);

        Log.d(TAG, "createVariable: Got value: " + result.toString());

        result = replaceVariables(result);

        createdVariables.put(variableName, result);
        Log.d(TAG, "Created a variable of name: " + variableName + ", and value: " + result.toString());
    }

    private Object replaceVariables(Object result) {
        for(String key: createdVariables.keySet()) {
            String madeKey = "data:" + key;
            Log.d(TAG, "replaceVariables: Made key: " + madeKey);
            if(result.toString().contains(madeKey)) {
                result = result.toString().replaceAll(madeKey, createdVariables.get(key).toString());
            }
        }
        return result;
    }

    private void createFunction(Object[] taskValues) {
        Object[] values = new Object[] {taskValues[0], taskValues[1], taskValues[2]};
        String functionName = taskValues[3].toString();

        createdFunctions.put(functionName, values);
    }

    private void createComponent(Object[] taskValues) {
        String componentName = taskValues[0].toString();
        String componentID = taskValues[1].toString();

        Class<?> CClass;
        Constructor<?> CConstructor;
        try {
            CClass = Class.forName(componentName);
            CConstructor = CClass.getConstructor(ComponentContainer.class);

            Component component = (Component) CConstructor.newInstance(container);

            componentsBuilt.put(componentID, component);
            Log.d(TAG, "Component created of source: " + componentName + ", component ID: " + componentID);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private void invokeFunction(String functionID) {
        Log.d(TAG, createdFunctions.toString());
        final Object[] taskValues = createdFunctions.get(functionID);

        if(taskValues == null || taskValues.length == 0) {
            Log.d(TAG, "Invalid invoke values provided");
            return;
        }

        final String componentID = taskValues[0].toString();
        final String functionName = taskValues[1].toString();
        final Object[] parameters = ((YailList) taskValues[2]).toArray();

        Log.d(TAG, "Invoking function of component ID: " + componentID + ", function name: " + functionName + ", parameters: " + Arrays.toString(parameters));

        invokeComponent(componentID, functionName, parameters);
    }

    private void invokeComponent(String componentID, String functionName, Object[] parameters) {
        try {
            Component component = componentsBuilt.get(componentID);
            Method[] methods = component.getClass().getMethods();

            Method method = findMethod(methods, functionName, parameters.length);

            if(method == null) {
                Log.e(TAG, "Function name: " + functionName + " may not exists");
                return;
            }

            int index = 0;
            for(Object object: parameters) {
                object = replaceVariables(object);
                parameters[index] = processValue(object);
                index++;
            }

            /*
               Taken from: https://github.com/ysfchn/DynamicComponents-AI2
             */

            Class<?>[] mRequestedMethodParameters = method.getParameterTypes();
            ArrayList<Object> mParametersArrayList = new ArrayList<>();

            for (int i = 0; i < mRequestedMethodParameters.length; i++) {
                if ("int".equals(mRequestedMethodParameters[i].getName())) {
                    mParametersArrayList.add(Integer.parseInt(parameters[i].toString()));
                } else if ("float".equals(mRequestedMethodParameters[i].getName())) {
                    mParametersArrayList.add(Float.parseFloat(parameters[i].toString()));
                } else if ("double".equals(mRequestedMethodParameters[i].getName())) {
                    mParametersArrayList.add(Double.parseDouble(parameters[i].toString()));
                } else if ("java.lang.String".equals(mRequestedMethodParameters[i].getName())) {
                    mParametersArrayList.add(parameters[i].toString());
                } else if ("boolean".equals(mRequestedMethodParameters[i].getName())) {
                    mParametersArrayList.add(Boolean.parseBoolean(parameters[i].toString()));
                } else {
                    mParametersArrayList.add(parameters[i]);
                }
            }

            invokeResult = method.invoke(component, mParametersArrayList.toArray());
            Log.d(TAG, "Invoked method name: " + functionName + ", component ID: " + componentID + ", invoke result: " + invokeResult);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private void initService(JobParameters jobParameters) {
        activity = new AActivity();
        activity.init(getApplicationContext());
        form = new FForm();
        container = new CComponentContainer(this);

        doBackgroundWork(jobParameters);
    }

    private Object processValue(Object object) {
        if(object instanceof String[]) {
            String[] value = (String[]) object;

            Log.d(TAG, "Found interpret value class: " + Arrays.toString(value));

            String text = value[0];
            boolean isCode = Boolean.parseBoolean(value[1]);

            if(isCode) {
                Object interpretResult = Utils.interpret(text, getApplicationContext());
                Log.d(TAG, "Got interpretResult: " + interpretResult.toString());
                return interpretResult;
            } else if(text.startsWith("invoke:result:[") && text.charAt(text.length() - 1) == ']') {
                String itemIndex = text.substring(15);
                itemIndex = itemIndex.substring(0, itemIndex.length() - 1);

                Log.d(TAG, "Found item index: " + itemIndex);

                try {
                    int indexParsed = Integer.parseInt(itemIndex) - 1;
                    Object resultItem = "";

                    if(invokeResult instanceof YailList) {
                        YailList yailList = (YailList) invokeResult;
                        resultItem = yailList.toArray()[indexParsed];
                    } else if(invokeResult instanceof List) {
                        resultItem = ((List) invokeResult).get(indexParsed);
                    } else {
                        Log.d(TAG, "Unknown list class found: " + invokeResult.getClass());
                    }

                    Log.d(TAG, "processValue: " + resultItem);

                    return resultItem;
                } catch (NumberFormatException | IndexOutOfBoundsException e) {
                    Log.d(TAG, "The index is not valid");
                }
            } else if(text.equals("invoke:result:empty")) {
                Log.d(TAG, "Received task to check if the invoke result is empty");

                boolean isEmpty = true;

                if(invokeResult instanceof String) {
                    isEmpty = invokeResult.toString().isEmpty();
                } if(invokeResult instanceof YailList) {
                    isEmpty = ((YailList) invokeResult).toArray().length == 0;
                } else if(invokeResult instanceof List) {
                    isEmpty = ((List) invokeResult).toArray().length == 0;
                } else {
                    Log.d(TAG, "Unknown class function received to find if result is empty");
                }

                Log.d(TAG, "Found if the result is empty: " + isEmpty);

                return isEmpty;

            } else if(text.equals("invoke:result:length")) {
                Log.d(TAG, "Found task to find length");

                int invokeLength;

                if(invokeResult instanceof String) {
                    invokeLength = invokeResult.toString().length();
                } else {
                    Object[] arrayList = null;

                    if(invokeResult instanceof YailList) {
                        arrayList = ((YailList) invokeResult).toArray();
                    } else if(invokeResult instanceof List) {
                        arrayList = ((List) invokeResult).toArray();
                    } else {
                        Log.d(TAG, "Unknown class function received to find the length");
                    }

                    Log.d(TAG, Arrays.toString(arrayList));

                    if(arrayList == null) {
                        return -1;
                    } else {
                        invokeLength = arrayList.length;
                    }
                }

                return invokeLength;
            } else if(text.equals("invoke:result")) {
                Log.d(TAG, String.valueOf("Is invoke result null while interpreting due to request: " + invokeResult == null));
                return invokeResult == null ? "" : invokeResult;
            }
        }
        return object;
    }

    /*
       Custom component container
     */

    class FForm extends Form {
        @Override
        public boolean canDispatchEvent(Component component, String str) {
            return true;
        }

        @Override
        public boolean dispatchEvent(Component component, String str, String str2, Object[] objArr) {
            Log.d(TAG, "Dispatch event: " + component.toString() + ", values: " + Arrays.toString(objArr) +  ", str one: " + str + ", str two: " + str2);
            getDispatchDelegate().dispatchEvent(component,str,str2,objArr);
            return true;
        }

        @Override
        public void dispatchGenericEvent(Component component, String str, boolean z, Object[] objArr) {
            Log.d(TAG, "Dispatch Generic Event: " + component.toString() + ", values: " + Arrays.toString(objArr) +  ", str one: " + str + ", boolean two: " + z);
            getDispatchDelegate().dispatchGenericEvent(component,str,z,objArr);
        }
    }

    static class AActivity extends Activity {
        public void init(Context context) {
            attachBaseContext(context);
        }

        /*
            Kindly ignore this
         */

        @Override
        public String getLocalClassName() {
            return "Screen1";
        }

    }

    static class CComponentContainer implements ComponentContainer {
        private final ActivityService service;

        public CComponentContainer(ActivityService service){
            this.service = service;
        }

        @Override
        public Activity $context() {
            return service.activity;
        }

        @Override
        public Form $form() {
            return service.form;
        }

        @Override
        public void $add(AndroidViewComponent androidViewComponent) {

        }

        @Override
        public void setChildWidth(AndroidViewComponent androidViewComponent, int i) {

        }

        @Override
        public void setChildHeight(AndroidViewComponent androidViewComponent, int i) {

        }

        @Override
        public int Width() {
            return 1;
        }

        @Override
        public int Height() {
            return 1;
        }
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        Log.d(TAG, "The service stopped");
        return false;
    }
}
