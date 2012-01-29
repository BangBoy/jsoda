
package wwutil.model;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.io.Serializable;



/**
 * Simple cache service for single process JVM.  Thread-safe.
 */
public class MemCacheableSimple implements MemCacheable {

    private Map<String, CacheEntry> lruCache;
    private AtomicInteger           hits = new AtomicInteger();
    private AtomicInteger           misses = new AtomicInteger();
    private int                     defaultExpirationSec = 0;
    private Loadable                objectLoader;


    /**
     * Create a LRU cache.
     * @param maxEntries  the maximum entries in the cache.  Oldest entries will be removed when capacity exceeded.
     */
    public MemCacheableSimple(int maxEntries) {
        this.lruCache = Collections.synchronizedMap(new LruCache<String, CacheEntry>(maxEntries));
    }

    /**
     * Create a LRU cache.
     * @param maxEntries  the maximum entries in the cache.  Oldest entries will be removed when capacity exceeded.
     * @param objectLoader  the callback interface to load object if it can't be found in the cache.
     * @param defaultExpirationSec  the expiration for cached objects when loading via objectLoader.
     */
    public MemCacheableSimple(int maxEntries, Loadable objectLoader, int defaultExpirationSec) {
        this.lruCache = Collections.synchronizedMap(new LruCache<String, CacheEntry>(maxEntries));
        this.objectLoader = objectLoader;
        this.defaultExpirationSec = defaultExpirationSec;
    }

    /**
     * Get an object from the cache.  If it doesn't exist, load it via the objectLoader.  If objectLoader is not set, return null.
     * @param key  Unique key of the object.
     */
    public Serializable get(String key) {
        Serializable    obj = getFromCache(key);
        if (obj == null && objectLoader != null) {
            obj = objectLoader.load(key);
            put(key, defaultExpirationSec, obj);
        }
        return obj;
    }

    @SuppressWarnings("unchecked")
    private Serializable getFromCache(String key) {
        CacheEntry<Serializable>    entry = (CacheEntry<Serializable>)lruCache.get(key);
        if (entry == null) {
            misses.incrementAndGet();
            return null;
        }
        if (entry.hasExpired()) {
            delete(key);
            misses.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        return entry.obj;
    }

    /**
     * Put an object into the cache.
     * @param key  Unique key of the object.
     * @param expireInSeconds  time to let object stay in cache before eviction.
     * @param obj  Object to cache.
     */
    public void put(String key, int expireInSeconds, Serializable obj) {
        lruCache.put(key, new CacheEntry<Serializable>(expireInSeconds, obj));
    }

    /**
     * Remove an object from the cache.
     * @param key  Unique key of the object.
     */
    public void delete(String key) {
        lruCache.remove(key);
    }

    /**
     * Reset caching statistics.
     */
    public void resetStats() {
        hits.set(0);
        misses.set(0);
    }

    /**
     * Get the number of cache hits.
     */
    public int getHits() {
        return hits.intValue();
    }

    /**
     * Get the number cache misses.
     */
    public int getMisses() {
        return misses.intValue();
    }

    /**
     * Dump caching statistics.
     */
    public String dumpStats() {
        int     hits = this.hits.intValue();
        int     misses = this.misses.intValue();
        int     total =  hits + misses;
        total = total == 0 ? 1 : total;
        return "total: " + total + "  hits: " + hits + " " + (hits*100/total) + "%  misses: " + misses + " " + (misses*100/total) + "%";
    }


    /**
     * Interface for objectLoader
     */
    public static interface Loadable {
        public Serializable load(String key);
    }


    private static class CacheEntry<B> {
        long    expirationMS;
        B       obj;

        CacheEntry(int expireInSeconds, B obj) {
            this.expirationMS = expireInSeconds == 0 ? 0 : System.currentTimeMillis() + expireInSeconds*1000L;
            this.obj = obj;
        }

        boolean hasExpired() {
            return expirationMS != 0 && (expirationMS - System.currentTimeMillis()) < 0;
        }
    }

    private static class LruCache<A, B> extends LinkedHashMap<A, B> {

        private final int maxEntries;

        public LruCache(final int maxEntries) {
            super(maxEntries + 1, 1.0f, true);
            this.maxEntries = maxEntries;
        }

        @Override protected boolean removeEldestEntry(final Map.Entry<A, B> eldest) {
            return super.size() > maxEntries;
        }
    }

}

