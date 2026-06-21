package dev.lu212.bv.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

public final class Messages {

    private static final String BASE = "dev.lu212.bv.i18n.messages";
    private static final String DEFAULT_LANG = "en";
    private static String currentLang = DEFAULT_LANG;
    private static ResourceBundle bundle;

    private Messages() {}

    public static void init(String lang) {
        currentLang = lang != null && !lang.isBlank() ? lang : DEFAULT_LANG;
        bundle = ResourceBundle.getBundle(BASE, new Locale(currentLang));
    }

    public static String get(String key) {
        if (bundle == null) init(null);
        try {
            return bundle.getString(key);
        } catch (java.util.MissingResourceException e) {
            return "!" + key + "!";
        }
    }

    public static String get(String key, Object... args) {
        return MessageFormat.format(get(key), args);
    }

    public static String currentLang() {
        return currentLang;
    }
}
