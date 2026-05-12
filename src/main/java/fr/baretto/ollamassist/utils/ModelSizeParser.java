package fr.baretto.ollamassist.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ModelSizeParser {

    private static final Pattern SIZE_PATTERN =
            Pattern.compile(":(\\d+(?:\\.\\d+)?)b", Pattern.CASE_INSENSITIVE);

    private ModelSizeParser() {}

    public static double extractParamCountBillions(String modelName) {
        if (modelName == null || modelName.isBlank()) return -1;
        Matcher m = SIZE_PATTERN.matcher(modelName);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1));
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }
}
