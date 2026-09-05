package com.quadrilateral.kudi9ja.domain.notification;

/** Which app store the installation came from. */
public enum DevicePlatform {

    ANDROID("Android"),
    IOS("iPhone"),

    /** The admin panel in a browser, if push is ever wanted there. */
    WEB("Web");

    private final String label;

    DevicePlatform(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
