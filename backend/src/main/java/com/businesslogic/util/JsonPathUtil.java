package com.businesslogic.util;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JsonPathUtil {

    private static final Logger logger = LoggerFactory.getLogger(JsonPathUtil.class);

    private static final Configuration configuration = Configuration.defaultConfiguration()
            .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL)
            .addOptions(Option.SUPPRESS_EXCEPTIONS);

    public static Object read(String json, String path) {
        try {
            return JsonPath.using(configuration).parse(json).read(path);
        } catch (PathNotFoundException e) {
            logger.warn("Path not found: {}", path);
            return null;
        } catch (Exception e) {
            logger.error("Error reading JSON path: {}", path, e);
            return null;
        }
    }

    public static String readString(String json, String path) {
        Object result = read(json, path);
        return result != null ? result.toString() : null;
    }

    public static Integer readInt(String json, String path) {
        Object result = read(json, path);
        if (result == null) {
            return null;
        }
        if (result instanceof Number) {
            return ((Number) result).intValue();
        }
        try {
            return Integer.parseInt(result.toString());
        } catch (NumberFormatException e) {
            logger.warn("Cannot convert to int: {}", result);
            return null;
        }
    }

    public static Long readLong(String json, String path) {
        Object result = read(json, path);
        if (result == null) {
            return null;
        }
        if (result instanceof Number) {
            return ((Number) result).longValue();
        }
        try {
            return Long.parseLong(result.toString());
        } catch (NumberFormatException e) {
            logger.warn("Cannot convert to long: {}", result);
            return null;
        }
    }

    public static Double readDouble(String json, String path) {
        Object result = read(json, path);
        if (result == null) {
            return null;
        }
        if (result instanceof Number) {
            return ((Number) result).doubleValue();
        }
        try {
            return Double.parseDouble(result.toString());
        } catch (NumberFormatException e) {
            logger.warn("Cannot convert to double: {}", result);
            return null;
        }
    }

    public static Boolean readBoolean(String json, String path) {
        Object result = read(json, path);
        if (result == null) {
            return null;
        }
        if (result instanceof Boolean) {
            return (Boolean) result;
        }
        return Boolean.parseBoolean(result.toString());
    }

    public static <T> List<T> readList(String json, String path, Class<T> clazz) {
        try {
            return JsonPath.using(configuration).parse(json).read(path);
        } catch (Exception e) {
            logger.error("Error reading JSON list: {}", path, e);
            return null;
        }
    }

    public static List<Object> readList(String json, String path) {
        try {
            return JsonPath.using(configuration).parse(json).read(path);
        } catch (Exception e) {
            logger.error("Error reading JSON list: {}", path, e);
            return null;
        }
    }

    public static boolean exists(String json, String path) {
        try {
            Object result = JsonPath.using(configuration).parse(json).read(path);
            return result != null;
        } catch (PathNotFoundException e) {
            return false;
        } catch (Exception e) {
            logger.error("Error checking JSON path existence: {}", path, e);
            return false;
        }
    }
}
