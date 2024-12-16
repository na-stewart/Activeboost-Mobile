package com.na_stewart.activeboost.ui;

import android.util.Pair;
import android.view.View;


import java.util.ArrayList;


public class ComponentManager {
    ArrayList<Pair<String, View>> components = new ArrayList<>();


    public void switchView(String view) {
        for (Pair<String, View> component : components) {
            if (component.first.equals(view))
                component.second.setVisibility(View.VISIBLE);
            else
                component.second.setVisibility(View.GONE);
        }
    }

    public void addComponent(String activity, View component) {
        components.add(new Pair<>(activity, component));
    }
}
