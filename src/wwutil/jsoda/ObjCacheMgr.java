/******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0.  If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/.
 * 
 * Software distributed under the License is distributed on an "AS IS" basis, 
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for 
 * the specific language governing rights and limitations under the License.
 *
 * The Original Code is: Jsoda
 * The Initial Developer of the Original Code is: William Wong (williamw520@gmail.com)
 * Portions created by William Wong are Copyright (C) 2012 William Wong, All Rights Reserved.
 *
 ******************************************************************************/


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

    private void cachePutObj(String key, int expireInSeconds, Object dataObj) {
        try {
            memCacheable.put(key, expireInSeconds, (Serializable)dataObj);
        } catch(Exception ignored) {
        }
    }

    // Cache by the primary key (id or id/rangekey)
    void cachePut(String modelName, Object dataObj) {
        int expireInSeconds = jsoda.getCachePolicy(modelName);
        if (expireInSeconds < 0)
            return;

        try {
            Field   idField = jsoda.getIdField(modelName);
            Object  idValue = idField.get(dataObj);
            Field   rangeField = jsoda.getRangeField(modelName);
            Object  rangeValue = rangeField == null ? null : rangeField.get(dataObj);
            String  key = makeCachePkKey(modelName, idValue, rangeValue);
            cachePutObj(key, expireInSeconds, dataObj);
        } catch(Exception ignored) {
        }

        // Cache by the CacheByFields
        for (String fieldName : jsoda.getCacheByFields(modelName)) {
            try {
                Field   field = jsoda.getField(modelName, fieldName);
                Object  fieldValue = field.get(dataObj);
                String  key = makeCacheFieldKey(modelName, field.getName(), fieldValue);
                cachePutObj(key, expireInSeconds, dataObj);
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

    Object cacheGet(String modelName, Object idValue, Object rangeValue) {
        // Cache by the primary key (id or id/rangekey)
        String  key = makeCachePkKey(modelName, idValue, rangeValue);
        return (Object)memCacheable.get(key);
    }

    Object cacheGetByField(String modelName, String fieldName, Object fieldValue) {
        return (Object)memCacheable.get(makeCacheFieldKey(modelName, fieldName, fieldValue));
    }

}
