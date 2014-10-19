package com.martin.lyrics;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Custom preference class. Calls a method on the owning activity.
 * Created by martin on 19/10/14.
 */
public class MethodPreference extends Preference {
    private Method m_method_call;

    public MethodPreference(Context context) {
        super(context);
        noMethodCallDefined();
    }

    public MethodPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        getMethodCall(attrs);
    }

    public MethodPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        getMethodCall(attrs);
    }

    private void getMethodCall(AttributeSet attrs) {
        String method_name = attrs.getAttributeValue("http://schemas.android.com/apk/res-auto",
                                                     "onClick");
        if (method_name == null) {
            noMethodCallDefined();
        }
        Class<? extends Context> c = getContext().getClass();
        try {
            m_method_call = c.getMethod(method_name);
        } catch (NoSuchMethodException e) {
            noMethodCallDefined();
        }
    }

    private void noMethodCallDefined() {
        throw new RuntimeException("No method call was defined for a MethodPreference!. "
                + "Set onClick attribute to a method name which exists "
                + "on the settings activity!");
    }

    @Override protected void onClick() {
        try {
            m_method_call.invoke(getContext());
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }
}