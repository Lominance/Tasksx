package com.kumaraswamy.tasks;

import android.content.Context;
import android.util.Log;
import bsh.EvalError;
import bsh.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;

public class Utils {
    private static final String TAG = "BackgroundTasks.Utils";

    public static Object[] toObjectArray(Object... array) {
        return new ArrayList<>(Arrays.asList(array)).toArray();
    }

    /*
       Taken from: https://github.com/ysfchn/DynamicComponents-AI2
     */

    public static Method findMethod(Method[] methods, String name, int parameterCount) {
        name = name.replaceAll("[^a-zA-Z0-9]", "");
        for (Method method : methods) {
            int methodParameterCount = method.getParameterTypes().length;
            if (method.getName().equals(name) && methodParameterCount == parameterCount) {
                return method;
            }
        }

        return null;
    }

    public static void saveTask(Context context, ArrayList<Object> tasks, int jobID) {
        try {
            FileOutputStream outputStream = new FileOutputStream(getTaskFileName(context, jobID));
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
            objectOutputStream.writeObject(tasks);

            outputStream.close();
            objectOutputStream.close();
            Log.d(TAG, "Saved the task to database");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static ArrayList<Object> readTask(Context context, int jobID) {
        ArrayList<Object> result = null;

        try {
            FileInputStream inputStream = new FileInputStream(getTaskFileName(context, jobID));
            ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
            result = (ArrayList<Object>) objectInputStream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        if(result == null) {
            Log.e(TAG, "Task object is null");
            return null;
        } else {
            return result;
        }
    }

    public static boolean clearTask(int taskID, Context context) {
        File file = getTaskFileName(context, taskID);
        if(file.exists()) {
            return file.delete();
        }
        return true;
    }

    private static File getTaskFileName(Context context, int jobID) {
        return new File(getInternalPath(context), "BackgroundTasksTask" + jobID + ".txt");
    }

    private static String getInternalPath(Context context) {
        File file = new File(context.getExternalFilesDir(null).getPath(), "BackgroundTasks");
        Log.d(TAG, "getInternalPath: " + file.mkdir());
        return file.getPath();
    }

    public static Object interpret(String code, Context activity) {
        Interpreter interpreter = new Interpreter();

        try {
            interpreter.set("context", activity);
            Object result = interpreter.eval(code);

            return result == null
                    ? "" :
                    result;
        } catch (EvalError evalError) {
            evalError.printStackTrace();
        }
        return "";
    }
}
