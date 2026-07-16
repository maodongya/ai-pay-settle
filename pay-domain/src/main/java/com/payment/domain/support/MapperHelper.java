package com.payment.domain.support;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mapper 通用辅助工具，提供 save / saveAll 逻辑。
 */
public final class MapperHelper {

    private static final ConcurrentHashMap<Class<?>, Field> PK_FIELD_CACHE = new ConcurrentHashMap<>();

    private MapperHelper() {
    }

    public static <T> T save(BaseMapper<T> mapper, T entity) {
        if (entity == null) {
            return null;
        }
        Object pk = getPrimaryKeyValue(entity);
        if (pk == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
        return entity;
    }

    /**
     * 逐条 save。热路径批量插入请走 Mapper#insertBatch（见 SplitDetail / AccountVoucher Repository）。
     */
    public static <T> List<T> saveAll(BaseMapper<T> mapper, List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return entities;
        }
        for (T entity : entities) {
            save(mapper, entity);
        }
        return entities;
    }

    private static Object getPrimaryKeyValue(Object entity) {
        Field pkField = PK_FIELD_CACHE.computeIfAbsent(entity.getClass(), MapperHelper::findPrimaryKeyField);
        try {
            pkField.setAccessible(true);
            return pkField.get(entity);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to read primary key from " + entity.getClass().getName(), e);
        }
    }

    private static Field findPrimaryKeyField(Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(TableId.class)) {
                return field;
            }
        }
        throw new IllegalStateException("No @TableId field found on " + clazz.getName());
    }
}
