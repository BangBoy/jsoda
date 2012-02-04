
package wwutil.jsoda;

import java.io.*;
import java.lang.reflect.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import wwutil.model.MemCacheable;
import wwutil.model.MemCacheableNoop;
import wwutil.model.annotation.CachePolicy;


/**
 * Perform the generic object caching work.
 */
class ObjCacheMgr
{
    private static Log  log = LogFactory.getLog(ObjCacheMgr.class);

    private Jsoda           jsoda;
    private MemCacheable    memCacheable = new MemCacheableNoop();


    ObjCacheMgr(Jsoda jsoda) {
        this.jsoda = jsoda;
    }

    void shutdown() {
    }

    void setMemCacheable(MemCacheable memCacheable) {
        if (memCacheable == null)
            this.memCacheable = new MemCacheableNoop();
        else
            this.memCacheable = memCacheable;
    }

    MemCacheable getMemCacheable() {
        return memCacheable;
    }

    private String makeCachePkKey(String modelName, Object id, Object rangeKey) {
        // Note: the cache keys are in the native string format to ensure always having a string key.
        String  dbId = jsoda.getDb(modelName).getDbTypeId();
        String  idStr = DataUtil.encodeValueToAttrStr(id, jsoda.getIdField(modelName).getType());
        Field   rangeField = jsoda.getRangeField(modelName);
        String  pk = rangeField == null ?
            dbId + "/" + modelName + "/pk/" + idStr :
            dbId + "/" + modelName + "/pk/" + idStr + "/" + DataUtil.encodeValueToAttrStr(rangeKey, rangeField.getType());
        return pk;
    }

    private String makeCacheFieldKey(String modelName, String fieldName, Object fieldValue) {
        String  dbId = jsoda.getDb(modelName).getDbTypeId();
        String  valueStr = DataUtil.encodeValueToAttrStr(fieldValue, jsoda.getField(modelName, fieldName).getType());
        return dbId + "/" + modelName + "/" + fieldName + "/" + valueStr;
    }

    private void cachePutObj(String key, Serializable dataObj) {
        try {
            int expireInSeconds = ReflectUtil.getAnnotationValue(dataObj.getClass(), CachePolicy.class, "expireInSeconds", Integer.class, 0);
            memCacheable.put(key, expireInSeconds, dataObj);
        } catch(Exception ignored) {
        }
    }

    void cachePut(String modelName, Serializable dataObj) {
        // Cache by the primary key (id or id/rangekey)
        try {
            Field   idField = jsoda.getIdField(modelName);
            Object  idValue = idField.get(dataObj);
            Field   rangeField = jsoda.getRangeField(modelName);
            Object  rangeValue = rangeField == null ? null : rangeField.get(dataObj);
            String  key = makeCachePkKey(modelName, idValue, rangeValue);
            cachePutObj(key, dataObj);
        } catch(Exception ignored) {
        }

        // Cache by the CacheByFields
        for (String fieldName : jsoda.getCacheByFields(modelName)) {
            try {
                Field   field = jsoda.getField(modelName, fieldName);
                Object  fieldValue = field.get(dataObj);
                String  key = makeCacheFieldKey(modelName, field.getName(), fieldValue);
                cachePutObj(key, dataObj);
            } catch(Exception ignore) {
            }
        }
    }

    void cacheDelete(String modelName, Object idValue, Object rangeValue)
    {
        Object  dataObj = cacheGet(modelName, idValue, rangeValue);
        if (dataObj != null) {
            for (String fieldName : jsoda.getCacheByFields(modelName)) {
                try {
                    Field   field = jsoda.getField(modelName, fieldName);
                    Object  fieldValue = field.get(dataObj);
                    String  key = makeCacheFieldKey(modelName, field.getName(), fieldValue);
                    memCacheable.delete(key);
                } catch(Exception ignored) {
                }
            }
        }

        String  key = makeCachePkKey(modelName, idValue, rangeValue);
        memCacheable.delete(key);
    }

    Serializable cacheGet(String modelName, Object idValue, Object rangeValue) {
        // Cache by the primary key (id or id/rangekey)
        String  key = makeCachePkKey(modelName, idValue, rangeValue);
        return memCacheable.get(key);
    }

    Serializable cacheGetByField(String modelName, String fieldName, Object fieldValue) {
        return memCacheable.get(makeCacheFieldKey(modelName, fieldName, fieldValue));
    }

}
