package com.na_stewart.activeboost.ui;

public class ViewSwitchEvent {
    private final String view;

    public ViewSwitchEvent(String view) {
        this.view = view;
    }

    public String getView() {
        return view;
    }
}
