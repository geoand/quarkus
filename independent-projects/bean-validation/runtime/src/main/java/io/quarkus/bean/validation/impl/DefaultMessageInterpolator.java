package io.quarkus.bean.validation.impl;

import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.validation.MessageInterpolator;

import org.jboss.logging.Logger;

/**
 * Default {@link MessageInterpolator} implementation for Quarkus Bean Validation.
 * <p>
 * Performs simple {@code {parameter}} replacement from constraint annotation attributes
 * and looks up messages from {@code ValidationMessages.properties} resource bundles.
 * EL expressions ({@code ${...}}) are not supported.
 */
public class DefaultMessageInterpolator implements MessageInterpolator {

    private static final Logger LOG = Logger.getLogger(DefaultMessageInterpolator.class);

    private static final String VALIDATION_MESSAGES = "ValidationMessages";
    private static final String PROVIDER_MESSAGES = "io.quarkus.bean.validation.DefaultValidationMessages";
    // Matches {param} but not \{param} (escaped braces are handled later in unescaping)
    private static final Pattern MESSAGE_PARAMETER_PATTERN = Pattern.compile("(?<!\\\\)\\{([^}]+)\\}");

    private final Locale defaultLocale;

    public DefaultMessageInterpolator() {
        this(null);
    }

    public DefaultMessageInterpolator(Locale defaultLocale) {
        this.defaultLocale = defaultLocale;
    }

    @Override
    public String interpolate(String messageTemplate, Context context) {
        Locale locale = defaultLocale != null ? defaultLocale : Locale.getDefault();
        return interpolate(messageTemplate, context, locale);
    }

    @Override
    public String interpolate(String messageTemplate, Context context, Locale locale) {
        if (messageTemplate == null) {
            return null;
        }

        String resolvedMessage = messageTemplate;

        // Step 1: If message is a resource bundle key (e.g. {jakarta.validation.constraints.NotNull.message}),
        // resolve it from the ValidationMessages resource bundle
        resolvedMessage = resolveResourceBundle(resolvedMessage, locale);

        // Step 2: Replace {paramName} placeholders with values from constraint annotation attributes
        resolvedMessage = replaceAnnotationAttributes(resolvedMessage, context);

        // Step 3: Unescape escaped characters per BV spec 6.3.1.1
        // \{ → {, \} → }, \$ → $, \\ → \
        resolvedMessage = unescapeMessage(resolvedMessage);

        return resolvedMessage;
    }

    private String resolveResourceBundle(String message, Locale locale) {
        // Repeatedly resolve resource bundle references until no more are found
        // This handles the case where a bundle value itself contains bundle references
        String previous;
        do {
            previous = message;
            message = resolveResourceBundleOnce(message, locale);
        } while (!message.equals(previous));
        return message;
    }

    private String resolveResourceBundleOnce(String message, Locale locale) {
        Matcher matcher = MESSAGE_PARAMETER_PATTERN.matcher(message);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            // Skip EL expressions
            if (key.startsWith("$")) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            String resolved = lookupBundle(key, locale);
            if (resolved != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(resolved));
            } else {
                // Leave it for annotation attribute replacement
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String lookupBundle(String key, Locale locale) {
        // Try user-defined ValidationMessages first
        try {
            ResourceBundle bundle = ResourceBundle.getBundle(VALIDATION_MESSAGES, locale,
                    Thread.currentThread().getContextClassLoader());
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            // Not found in user bundle, fall through to provider bundle
        }
        // Fall back to provider's built-in message bundle (per BV spec 5.3.1)
        try {
            ResourceBundle bundle = ResourceBundle.getBundle(PROVIDER_MESSAGES, locale,
                    DefaultMessageInterpolator.class.getClassLoader());
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            // Not found in provider bundle either
        }
        return null;
    }

    private String unescapeMessage(String message) {
        if (message == null || message.indexOf('\\') == -1) {
            return message;
        }
        StringBuilder sb = new StringBuilder(message.length());
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (c == '\\' && i + 1 < message.length()) {
                char next = message.charAt(i + 1);
                if (next == '{' || next == '}' || next == '$' || next == '\\') {
                    sb.append(next);
                    i++; // skip next char
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private String replaceAnnotationAttributes(String message, Context context) {
        if (context == null || context.getConstraintDescriptor() == null) {
            return message;
        }
        Map<String, Object> attributes = context.getConstraintDescriptor().getAttributes();
        if (attributes == null || attributes.isEmpty()) {
            return message;
        }

        Matcher matcher = MESSAGE_PARAMETER_PATTERN.matcher(message);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String paramName = matcher.group(1);
            if (attributes.containsKey(paramName)) {
                Object value = attributes.get(paramName);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(value)));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
