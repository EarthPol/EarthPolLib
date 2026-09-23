package com.earthpol.earthpollib.translation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Lazy translation wrapper for building translated messages with optional appends.
 */
@SuppressWarnings("unused")
public class Translatable {
    private String key;
    private Object[] args;
    private Locale locale;
    private final List<Object> appended = new ArrayList<>();

    protected Translatable(String key, Object... args) {
        this.key = Objects.requireNonNull(key, "key");
        this.args = args;
    }

    public static Translatable of(String key, Object... args) {
        return new Translatable(key, args);
    }

    public static Translatable literal(String text) {
        return new LiteralTranslatable(text);
    }

    public String key() {
        return key;
    }

    public Object[] args() {
        return args;
    }

    public Locale locale() {
        return locale;
    }

    public Translatable key(String key) {
        this.key = Objects.requireNonNull(key, "key");
        return this;
    }

    public Translatable args(Object... args) {
        this.args = args;
        return this;
    }

    public Translatable locale(Locale locale) {
        this.locale = locale;
        return this;
    }

    public Translatable append(String text) {
        appended.add(text);
        return this;
    }

    public Translatable append(Translatable translatable) {
        appended.add(translatable);
        return this;
    }

    public String translate(TranslationService service) {
        Objects.requireNonNull(service, "service");

        Object[] processedArgs = args == null ? null : Arrays.copyOf(args, args.length);
        if (processedArgs != null) {
            for (int i = 0; i < processedArgs.length; i++) {
                if (processedArgs[i] instanceof Translatable nested) {
                    processedArgs[i] = nested.locale(locale).translate(service);
                }
            }
        }

        String translated = locale == null
            ? service.translate(key, processedArgs)
            : service.translate(key, locale, processedArgs);

        if (appended.isEmpty()) {
            return translated;
        }

        StringBuilder sb = new StringBuilder(translated);
        for (Object append : appended) {
            if (append instanceof String text) {
                sb.append(text);
            } else if (append instanceof Translatable translatable) {
                sb.append(translatable.locale(locale).translate(service));
            }
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return "Translatable{" +
            "key='" + key + '\'' +
            ", args=" + Arrays.toString(args) +
            ", locale=" + locale +
            '}';
    }

    private static final class LiteralTranslatable extends Translatable {
        private LiteralTranslatable(String text) {
            super(text);
        }

        @Override
        public String translate(TranslationService service) {
            return key();
        }
    }
}
