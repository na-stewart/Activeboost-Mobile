package com.na_stewart.activeboost.ui;

import android.util.Pair;
import android.view.View;


import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;


public class ComponentManager {
    ArrayList<Pair<String, View>> components = new ArrayList<>();

    public ComponentManager() {
        EventBus.getDefault().register(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onViewSwitchEvent(ViewSwitchEvent event) {
        for (Pair<String, View> component : components) {
            if (component.first.equals(event.getView()))
                component.second.setVisibility(View.VISIBLE);
            else
                component.second.setVisibility(View.GONE);
        }
    }

    public void addComponent(String activity, View component) {
        components.add(new Pair<>(activity, component));
    }
}
