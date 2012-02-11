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

import java.util.*;
import java.io.*;
import java.lang.reflect.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import wwutil.model.annotation.CachePolicy;
import wwutil.model.annotation.CacheByField;
import wwutil.model.annotation.DefaultGUID;
import wwutil.model.annotation.DefaultComposite;
import wwutil.model.annotation.VersionLocking;
import wwutil.model.annotation.ModifiedTime;



@SuppressWarnings("unchecked")
public class Dao<T>
{
    private static Log  log = LogFactory.getLog(Dao.class);

    private Class<T>    modelClass;
    private String      modelName;
    private Jsoda       jsoda;


    public Dao(Class<T> modelClass, Jsoda jsoda) {
        this.modelClass = modelClass;
        this.modelName = jsoda.getModelName(modelClass);
        this.jsoda = jsoda;
    }

    public void put(T dataObj)
        throws JsodaException
    {
        try {
            Field   versionField = jsoda.getVersionField(modelName);

            if (versionField == null) {
                putIf(dataObj, null, null, false);
            } else {
                // Get old version as the expectedVersion before preStoreSteps() incrementing the version number.
                Integer expectedVersion = (Integer)versionField.get(dataObj);
                boolean expectedExists = (expectedVersion != null && expectedVersion.intValue() > 0);
                expectedVersion = (expectedExists ? expectedVersion : new Integer(0));
                putIf(dataObj, versionField.getName(), expectedVersion, expectedExists);
            }
        } catch(JsodaException je) {
            throw je;
        } catch(Exception e) {
            throw new JsodaException("Failed to put object", e);
        }
    }    

    public void putIf(T dataObj, String expectedField, Object expectedValue, boolean expectedExists)
        throws JsodaException
    {
        try {
            preStoreSteps(dataObj);
            jsoda.getDb(modelName).putObj(modelName, dataObj, expectedField, expectedValue, expectedExists);
            jsoda.getObjCacheMgr().cachePut(modelName, dataObj);
        } catch(JsodaException je) {
            throw je;
        } catch(Exception e) {
            throw new JsodaException("Failed to put object", e);
        }
    }

    /** Support batch put on array of objects or varargs of objects */
    public void batchPut(T... dataObjs)
        throws JsodaException
    {
        batchPut(Arrays.asList(dataObjs));
    }

    public void batchPut(List<T> dataObjs)
        throws JsodaException
    {
        if (dataObjs.size() == 0)
            return;

        try {
            for (T dataObj : dataObjs) {
                preStoreSteps(dataObj);
            }
            jsoda.getDb(modelName).putObjs(modelName, dataObjs);
            for (T dataObj : dataObjs) {
                jsoda.getObjCacheMgr().cachePut(modelName, dataObj);
            }
        } catch(JsodaException je) {
            throw je;
        } catch(Exception e) {
            throw new JsodaException("Failed to batch put objects", e);
        }
    }

    private void preStoreSteps(T dataObj)
        throws Exception
    {
        callPrePersist(modelName, dataObj);
        applyDataGenerators(modelName, dataObj);
        applyCompositeDataGenerators(modelName, dataObj);
        callPreValidation(modelName, dataObj);
        validateFields(modelName, dataObj);
    }

    public T get(Object id)
        throws JsodaException
    {
        if (!(id instanceof Integer ||
              id instanceof Long ||
              id instanceof String))
            throw new ValidationException("The Id can only be String, Integer, or Long.");

        return getObj(id, null);
    }

    public T get(Object hashKey, Object rangeKey)
        throws JsodaException
    {
        if (!(hashKey instanceof Integer ||
              hashKey instanceof Long ||
              hashKey instanceof String))
            throw new ValidationException("The hashKey can only be String, Integer, or Long.");
        if (!(rangeKey instanceof Integer ||
              rangeKey instanceof Long ||
              rangeKey instanceof String))
            throw new ValidationException("The rangeKey can only be String, Integer, or Long.");

        return getObj(hashKey, rangeKey);
    }

    private T getObj(Object id, Object rangeKey)
        throws JsodaException
    {
        try {
            T   obj = (T)jsoda.getObjCacheMgr().cacheGet(modelName, id, rangeKey);
            if (obj != null)
                return obj;

            if (rangeKey == null) {
                if (jsoda.getRangeField(modelName) != null) {
                    throw new ValidationException("Model " + modelName + " requires rangeKey for get.");
                }
                obj = (T)jsoda.getDb(modelName).getObj(modelName, id, null);
            } else {
                obj = (T)jsoda.getDb(modelName).getObj(modelName, id, rangeKey);
            }

            if (obj != null)
                postGetSteps(obj);

            return obj;
        } catch(Exception e) {
            throw new JsodaException("Failed to get object", e);
        }
    }

    public void delete(Object id)
        throws JsodaException
    {
        if (!(id instanceof Integer ||
              id instanceof Long ||
              id instanceof String))
            throw new ValidationException("The Id can only be String, Integer, or Long.");

        if (jsoda.getRangeField(modelName) != null) {
            throw new ValidationException("Model " + modelName + " requires rangeKey for delete.");
        }

        deleteObj(id, null);
    }

    public void delete(Object hashKey, Object rangeKey)
        throws JsodaException
    {
        if (!(hashKey instanceof Integer ||
              hashKey instanceof Long ||
              hashKey instanceof String))
            throw new ValidationException("The hashKey can only be String, Integer, or Long.");
        if (!(rangeKey instanceof Integer ||
              rangeKey instanceof Long ||
              rangeKey instanceof String))
            throw new ValidationException("The rangeKey can only be String, Integer, or Long.");

        deleteObj(hashKey, rangeKey);
    }

    private void deleteObj(Object id, Object rangeKey)
        throws JsodaException
    {
        try {
            jsoda.getObjCacheMgr().cacheDelete(modelName, id, rangeKey);
            if (rangeKey == null) {
                jsoda.getDb(modelName).delete(modelName, id, null);
            } else {
                jsoda.getDb(modelName).delete(modelName, id, rangeKey);
            }
        } catch(Exception e) {
            throw new JsodaException("Failed to delete object " + id + "/" + rangeKey, e);
        }
    }

    /** Support batch delete on array of objects or varargs of objects */
    public void batchDelete(Object... idList)
        throws JsodaException
    {
        batchDelete(Arrays.asList(idList));
    }

    public void batchDelete(List idList)
        throws JsodaException
    {
        try {
            for (Object id : idList) {
                jsoda.getObjCacheMgr().cacheDelete(modelName, id, null);
            }
            jsoda.getDb(modelName).batchDelete(modelName, idList, null);
        } catch(Exception e) {
            throw new JsodaException("Failed to batch delete objects", e);
        }
    }

    public void batchDelete(List idList, List rangeKeyList)
        throws JsodaException
    {
        try {
            for (int i = 0; i < idList.size(); i++) {
                jsoda.getObjCacheMgr().cacheDelete(modelName, idList.get(i), rangeKeyList.get(i));
            }
            jsoda.getDb(modelName).batchDelete(modelName, idList, rangeKeyList);
        } catch(Exception e) {
            throw new JsodaException("Failed to batch delete objects", e);
        }
    }

    /** Get an object by one of its field, beside the Id field. */
    public T findBy(String field, Object fieldValue)
        throws JsodaException
    {
        T       obj = (T)jsoda.getObjCacheMgr().cacheGetByField(modelName, field, fieldValue);
        if (obj != null)
            return obj;

        List<T> items = jsoda.query(modelClass).eq(field, fieldValue).run();
        // query.run() has already cached the object.  No need to cache it here.
        return items.size() == 0 ? null : items.get(0);
    }


    void postGetSteps(Object obj)
        throws JsodaException
    {
        callPostLoad(modelName, obj);
        jsoda.getObjCacheMgr().cachePut(modelName, obj);
    }
    
    protected void validateFields(String modelName, Object dataObj)
        throws Exception
    {
        for (Field field : jsoda.getAllFields(modelName)) {

            // TODO: Use new annotation class for check null
            // Boolean isAttrNullable = ReflectUtil.getAnnotationValue(field, Column.class, "nullable", Boolean.class, Boolean.TRUE);
            // if (!isAttrNullable && field.get(dataObj) == null)
            //     throw new ValidationException("Field " + field.getName() + " cannot be null.");

        }
    }

    protected T applyDataGenerators(String modelName, T dataObj)
        throws Exception
    {
        for (Field field : jsoda.getAllFields(modelName)) {
            Object  value = field.get(dataObj);
            
            if (value == null || value.toString().length() == 0) {
                
                if (ReflectUtil.hasAnnotation(field, DefaultGUID.class)) {
                    fillDefaultGUID(modelName, field, dataObj);
                }
                
            }

            if (ReflectUtil.hasAnnotation(field, VersionLocking.class)) {
                incrementField(dataObj, field, 1);
            }

            if (ReflectUtil.hasAnnotation(field, ModifiedTime.class)) {
                field.set(dataObj, new Date());
            }

        }
        return dataObj;
    }

    protected T applyCompositeDataGenerators(String modelName, T dataObj)
        throws Exception
    {
        for (Field field : jsoda.getAllFields(modelName)) {
            Object  value = field.get(dataObj);
            if (value == null || value.toString().length() == 0) {

                if (ReflectUtil.hasAnnotation(field, DefaultComposite.class)) {
                    fillDefaultComposite(modelName, field, dataObj);
                }

            }
        }
        return dataObj;
    }

    private void fillDefaultGUID(String modelName, Field field, Object dataObj)
        throws Exception
    {
        boolean isShort = ReflectUtil.getAnnotationValue(field, DefaultGUID.class, "isShort", Boolean.class, Boolean.FALSE);
        String  uuidStr = isShort ? BaseXUtil.uuid8() : BaseXUtil.uuid16();
        field.set(dataObj, uuidStr);
    }

    private void fillDefaultComposite(String modelName, Field field, Object dataObj)
        throws Exception
    {
        String[]        fromFields = ReflectUtil.getAnnotationValue(field, DefaultComposite.class, "fromFields", String[].class, new String[0]);
        int[]           substrLen = ReflectUtil.getAnnotationValue(field, DefaultComposite.class, "substrLen", int[].class, new int[0]);
        String          separator = ReflectUtil.getAnnotationValue(field, DefaultComposite.class, "separator", "-");
        StringBuilder   sb = new StringBuilder();

        for (int i = 0; i < fromFields.length; i++) {
            Field       subpartField = jsoda.getField(modelName, fromFields[i]);
            if (subpartField == null)
                throw new IllegalArgumentException(fromFields[i] + " specified in the fromFields parameter of the @DefaultComposite field " +
                                                   field.getName() + " doesn't exist.");
            Object      subpartValue = subpartField.get(dataObj);
            String      subpartStr = subpartValue == null ? "" : subpartValue.toString();

            subpartStr = getSubpartMax(subpartStr, i, substrLen);

            if (subpartStr.length() > 0) {
                if (sb.length() > 0)
                    sb.append(separator);
                sb.append(subpartStr);
            }
        }

        field.set(dataObj, sb.toString());
    }

    private static String getSubpartMax(String fieldStr, int fieldPos, int[] substrLen) {
        if (substrLen == null || fieldPos >= substrLen.length || substrLen[fieldPos] == 0)
            return fieldStr;
        int len = substrLen[fieldPos] > fieldStr.length() ? fieldStr.length() : substrLen[fieldPos];
        return fieldStr.substring(0, len);
    }

    private void callPrePersist(String modelName, Object dataObj)
        throws JsodaException
    {
        try {
            Method  prePersistMethod = jsoda.getPrePersistMethod(modelName);
            if (prePersistMethod != null) {
                prePersistMethod.invoke(dataObj);
            }
        } catch(Exception e) {
            throw new JsodaException("callPrePersist", e);
        }
    }

    private void callPreValidation(String modelName, Object dataObj)
        throws JsodaException
    {
        try {
            Method  preValidationMethod = jsoda.getPreValidationMethod(modelName);
            if (preValidationMethod != null) {
                preValidationMethod.invoke(dataObj);
            }
        } catch(Exception e) {
            throw new JsodaException("callPreValidation", e);
        }
    }

    private void callPostLoad(String modelName, Object dataObj)
        throws JsodaException
    {
        try {
            Method  postLoadMethod = jsoda.getPostLoadMethod(modelName);
            if (postLoadMethod != null) {
                postLoadMethod.invoke(dataObj);
            }
        } catch(Exception e) {
            throw new JsodaException("callPostLoad", e);
        }
    }

    private void incrementField(Object dataObj, Field field, int incrementAmount)
        throws Exception
    {
        if (field.getType() == Integer.class || field.getType() == int.class) {
            Integer value = (Integer)field.get(dataObj);
            value = value == null ? new Integer(1) : new Integer(value.intValue() + 1);
            field.set(dataObj, value);
        } else if (field.getType() == Long.class || field.getType() == long.class) {
            Long    value = (Long)field.get(dataObj);
            value = value == null ? new Long(1) : new Long(value.longValue() + 1);
            field.set(dataObj, value);
        } else {
            throw new IllegalArgumentException("Cannot increment non-integer field " + field);
        }
    }

}

