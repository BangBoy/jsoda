
package wwutil.jsoda;

import java.util.*;
import wwutil.model.annotation.DbType;
import wwutil.model.annotation.AModel;


public interface DbService {
    public DbType getDbType();
    public void setDbEndpoint(String endpoint);

    public void createModelTable(String modelName);
    public void deleteTable(String tableName);
    public List<String> listTables();

    public void putObj(String modelName, Object dataObj, String expectedField, Object expectedValue) throws Exception;
    public void putObjs(String modelName, List dataObjs) throws Exception;
    public Object getObj(String modelName, Object id) throws Exception;
    public Object getObj(String modelName, Object id, Object rangeKey) throws Exception;
    public Object getObj(String modelName, String field1, Object key1, Object... fieldKeys) throws Exception;
    public void delete(String modelName, String id) throws Exception;
    public void delete(String modelName, String id, Object rangeKey) throws Exception;
    public void batchDelete(String modelName, List<String> idList) throws Exception;
    public <T> List<T> runQuery(Class<T> modelClass, String queryStr) throws JsodaException;
    public String getFieldAttrName(String modelName, String fieldName);

}
