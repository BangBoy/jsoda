
package wwutil.jsoda;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.logging.*;
import java.lang.reflect.*;

import javax.persistence.Table;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Transient;
import javax.persistence.PrePersist;
import javax.persistence.PostLoad;

import org.apache.commons.beanutils.ConvertUtils;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.simpledb.AmazonSimpleDBClient;
import com.amazonaws.services.simpledb.model.CreateDomainRequest;
import com.amazonaws.services.simpledb.model.DeleteDomainRequest;
import com.amazonaws.services.simpledb.model.ListDomainsResult;
import com.amazonaws.services.simpledb.model.PutAttributesRequest;
import com.amazonaws.services.simpledb.model.BatchPutAttributesRequest;
import com.amazonaws.services.simpledb.model.ReplaceableItem;
import com.amazonaws.services.simpledb.model.ReplaceableAttribute;
import com.amazonaws.services.simpledb.model.GetAttributesRequest;
import com.amazonaws.services.simpledb.model.GetAttributesResult;
import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.SelectRequest;
import com.amazonaws.services.simpledb.model.SelectResult;
import com.amazonaws.services.simpledb.model.Item;
import com.amazonaws.services.simpledb.model.DeleteAttributesRequest;
import com.amazonaws.services.simpledb.model.BatchDeleteAttributesRequest;
import com.amazonaws.services.simpledb.model.DeletableItem;
import com.amazonaws.services.simpledb.model.UpdateCondition;
import com.amazonaws.services.simpledb.util.SimpleDBUtils;

import wwutil.model.MemCacheable;
import wwutil.model.annotation.DbType;
import wwutil.model.annotation.AModel;
import wwutil.model.annotation.CachePolicy;
import wwutil.model.annotation.DefaultGUID;
import wwutil.model.annotation.DefaultComposite;
import wwutil.model.annotation.CacheByField;


/**
 */
class SimpleDBService implements DbService
{
    public static final String      ITEM_NAME = "itemName()";


    private Jsoda                   jsoda;
    private AmazonSimpleDBClient    sdbClient;
    

    // AWS Access Key ID and Secret Access Key
    public SimpleDBService(Jsoda jsoda, AWSCredentials cred)
        throws Exception
    {
        this.jsoda = jsoda;
        this.sdbClient = new AmazonSimpleDBClient(cred);
    }

    public void shutdown() {
        sdbClient.shutdown();
    }

    public DbType getDbType() {
        return DbType.SimpleDB;
    }
    
    public void setDbEndpoint(String endpoint) {
        sdbClient.setEndpoint(endpoint);
    }


    // Delegated SimpleDB API

    public void createModelTable(String modelName) {
        sdbClient.createDomain(new CreateDomainRequest(jsoda.getModelTable(modelName)));
    }

    public void deleteTable(String tableName) {
        sdbClient.deleteDomain(new DeleteDomainRequest(tableName));
    }

    public List<String> listTables() {
        ListDomainsResult   list = sdbClient.listDomains();
        return list.getDomainNames();
    }

    public void putObj(String modelName, Object dataObj, String expectedField, Object expectedValue)
        throws Exception
    {
        String  table = jsoda.getModelTable(modelName);
        String  idValue = DataUtil.getFieldValueStr(dataObj, jsoda.getIdField(modelName));
        PutAttributesRequest    req =
            expectedField == null ?
                new PutAttributesRequest(table, idValue, buildAttrs(dataObj, modelName)) :
                new PutAttributesRequest(table, idValue, buildAttrs(dataObj, modelName),
                                         buildExpectedValue(modelName, expectedField, expectedValue));
        sdbClient.putAttributes(req);
    }

    public void putObjs(String modelName, List dataObjs)
        throws Exception
    {
        String  table = jsoda.getModelTable(modelName);
        sdbClient.batchPutAttributes(new BatchPutAttributesRequest(table, buildPutItems(dataObjs, modelName)));
    }

    public Object getObj(String modelName, Object id)
        throws Exception
    {
        String              table = jsoda.getModelTable(modelName);
        String              idValue = DataUtil.toValueStr(id, jsoda.getIdField(modelName).getType());
        GetAttributesResult result = sdbClient.getAttributes(new GetAttributesRequest(table, idValue));
        if (result.getAttributes().size() == 0)
            return null;        // not existed.

        return buildLoadObj(modelName, idValue, result.getAttributes());
    }

    public Object getObj(String modelName, Object id, Object rangeKey)
        throws Exception
    {
        // throw new UnsupportedOperationException("Unsupported method");

        // Ignore rangeKey
        return getObj(modelName, id);
    }

    public Object getObj(String modelName, String field1, Object key1, Object... fieldKeys)
        throws Exception
    {
        throw new UnsupportedOperationException("Unsupported method");
    }


    public void delete(String modelName, Object id)
        throws Exception
    {
        String  table = jsoda.getModelTable(modelName);
        String  idValue = DataUtil.toValueStr(id, jsoda.getIdField(modelName).getType());
        sdbClient.deleteAttributes(new DeleteAttributesRequest(table, idValue));
    }

    public void delete(String modelName, Object id, Object rangeKey)
        throws Exception
    {
        // throw new UnsupportedOperationException("Unsupported method");

        // Ignore rangeKey
        delete(modelName, id);
    }

    public void batchDelete(String modelName, List<String> idList)
        throws Exception
    {
        String  table = jsoda.getModelTable(modelName);
        List<DeletableItem> items = new ArrayList<DeletableItem>();
        for (String id : idList) {
            String  idValue = DataUtil.toValueStr(id, jsoda.getIdField(modelName).getType());
            items.add(new DeletableItem().withName(idValue));
        }
        sdbClient.batchDeleteAttributes(new BatchDeleteAttributesRequest(table, items));
    }

    
    // /** Get by a field beside the id */
    // public <T> T findBy(Class<T> modelClass, String field, Object fieldValue)
    //     throws Exception
    // {
    //     String  modelName = getModelName(modelClass);
    //     T       obj = (T)cacheGet(modelName, field, fieldValue);
    //     if (obj != null)
    //         return obj;

    //     List<T> items = query(modelClass).filter(field, "=", fieldValue).run();
    //     // runQuery() has already cached the object.  No need to cache it here.
    //     return items.size() == 0 ? null : items.get(0);
    // }

    // public <T> SdbQuery<T> query(Class<T> modelClass)
    //     throws Exception
    // {
    //     SdbQuery<T> query = new SdbQuery<T>(this, modelClass);
    //     return query;
    // }

    @SuppressWarnings("unchecked")
    public <T> List<T> runQuery(Class<T> modelClass, Query<T> query)
        throws JsodaException
    {
        String          modelName = jsoda.getModelName(modelClass);
        List<T>         resultObjs = new ArrayList<T>();
        String          queryStr = toQueryStr(query);
        SelectRequest   request = new SelectRequest(queryStr);

        try {
            for (Item item : sdbClient.select(request).getItems()) {
                T   obj = (T)buildLoadObj(modelName, item.getName(), item.getAttributes());
                resultObjs.add(obj);
            }
            return resultObjs;
        } catch(Exception e) {
            throw new JsodaException("Query failed.  Query: " + request.getSelectExpression() + "  Error: " + e.getMessage(), e);
        }
    }


    public String getFieldAttrName(String modelName, String fieldName) {
        // SimpleDB's attribute name for Id always maps to "itemName()"
        if (jsoda.isIdField(modelName, fieldName))
            return ITEM_NAME;

        String  attrName = jsoda.getFieldAttrMap(modelName).get(fieldName);
        return attrName != null ? SimpleDBUtils.quoteName(attrName) : null;
    }


    private List<ReplaceableAttribute> buildAttrs(Object dataObj, String modelName)
        throws Exception
    {
        // TODO: test load object in SimpleDB
        List<ReplaceableAttribute>  attrs = new ArrayList<ReplaceableAttribute>();
        for (Map.Entry<String, String> fieldAttr : jsoda.getFieldAttrMap(modelName).entrySet()) {
            String  fieldName = fieldAttr.getKey();
            String  attrName  = fieldAttr.getValue();
            Field   field = jsoda.getField(modelName, fieldName);
            String  fieldValueStr = DataUtil.getFieldValueStr(dataObj, field);

            // Add attr:fieldValueStr to list.  Skip Id field.  Treats Id field as the itemName key in SimpleDB.
            if (!jsoda.isIdField(modelName, fieldName))
                attrs.add(new ReplaceableAttribute(attrName, fieldValueStr, true));
        }
        
        // Field[]                     fields = jsoda.modelAttrFields.get(modelName);
        // Map<String, String>         fieldAttrMap = jsoda.modelFieldAttrMap.get(modelName);
        // List<ReplaceableAttribute>  attrs = new ArrayList<ReplaceableAttribute>();

        // for (Field field : fields) {
        //     String  attrName = fieldAttrMap.get(field.getName());
        //     String  fieldValue = DataUtil.getFieldValueStr(dataObj, field);
        //     attrs.add(new ReplaceableAttribute(attrName, fieldValue, true));
        // }

        return attrs;
    }

    private UpdateCondition buildExpectedValue(String modelName, String expectedField, Object expectedValue)
        throws Exception
    {
        String      attrName = jsoda.getFieldAttrMap(modelName).get(expectedField);
        String      fieldValue = DataUtil.toValueStr(expectedValue, jsoda.getField(modelName, expectedField).getType());
        return new UpdateCondition(attrName, fieldValue, true);
    }

    private List<ReplaceableItem> buildPutItems(List dataObjs, String modelName)
        throws Exception
    {
        String                  table = jsoda.getModelTable(modelName);
        List<ReplaceableItem>   items = new ArrayList<ReplaceableItem>();
        String                  idValue;

        for (Object dataObj : dataObjs) {
            idValue = DataUtil.getFieldValueStr(dataObj, jsoda.getIdField(modelName));
            items.add(new ReplaceableItem(idValue, buildAttrs(dataObj, modelName)));
        }
        return items;
    }

    private Object buildLoadObj(String modelName, String idValue, List<Attribute> attrs)
        throws Exception
    {
        Class               modelClass = jsoda.getModelClass(modelName);
        Object              obj = modelClass.newInstance();
        Map<String, Field>  attrFieldMap = jsoda.getAttrFieldMap(modelName);

        // Set the attr field 
        for (Attribute attr : attrs) {
            String  attrName  = attr.getName();
            String  fieldValue = attr.getValue();
            Field   field = attrFieldMap.get(attrName);

            if (field == null) {
                // TODO: log warning
                //throw new Exception("Attribute name " + attrName + " has no corresponding field in object " + modelClass);
                //logger.severe("Attribute name " + attrName + " has no corresponding field in object " + modelClass);
                continue;
            }

            DataUtil.setFieldValueStr(obj, field, fieldValue);
        }

        // Set the Id field
        DataUtil.setFieldValueStr(obj, jsoda.getIdField(modelName), idValue);

        return obj;
    }

    private <T> String toQueryStr(Query<T> query) {
        StringBuilder   sb = new StringBuilder();
        addSelectStr(query, sb);
        addFromStr(query, sb);
        addFilterStr(query, sb);
        addOrderbyStr(query, sb);
        addLimitStr(query, sb);
        return sb.toString();
    }

    private <T> void addSelectStr(Query<T> query, StringBuilder sb) {
        sb.append("select");

        if (query.selectTerms.size() == 0) {
            sb.append(" * ");
            return;
        }

        for (int i = 0; i < query.selectTerms.size(); i++) {
            if (i > 0)
                sb.append(", ");
            else
                sb.append(" ");
            String  term = (String)query.selectTerms.get(i);
            sb.append(getFieldAttrName(query.modelName, term));
        }
    }

    private <T> void addFromStr(Query<T> query, StringBuilder sb) {
        sb.append(" from ").append(SimpleDBUtils.quoteName(jsoda.getModelTable(query.modelName)));
    }

    private <T> void addFilterStr(Query<T> query, StringBuilder sb) {
        if (query.filters.size() == 0) {
            return;
        }

        sb.append(" where ");

        for (int i = 0; i < query.filters.size(); i++) {
            if (i > 0)
                sb.append(" and ");
            query.filters.get(i).addFilterStr(sb);
        }
    }

    private <T> void addOrderbyStr(Query<T> query, StringBuilder sb) {
        if (query.orderbyFields.size() == 0)
            return;

        sb.append(" order by");

        for (int i = 0; i < query.orderbyFields.size(); i++) {
            if (i > 0)
                sb.append(", ");
            else
                sb.append(" ");
            String  orderby = query.orderbyFields.get(i);
            String  ascDesc = orderby.charAt(0) == '+' ? " asc" : " desc";
            String  term = orderby.substring(1);
            sb.append(jsoda.getDb(query.modelName).getFieldAttrName(query.modelName, term));
            sb.append(ascDesc);
        }
    }

    private <T> void addLimitStr(Query<T> query, StringBuilder sb) {
        if (query.limit > 0)
            sb.append(" limit ").append(query.limit);
    }

}

