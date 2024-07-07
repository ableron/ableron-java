package io.github.ableron;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.RemovalCause;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class FragmentCache {

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final Cache<String, Fragment> fragmentCache;
  private final boolean autoRefreshEnabled;
  private final CacheStats stats;

  public FragmentCache(long cacheMaxSizeInBytes, boolean autoRefreshEnabled, CacheStats stats) {
    this.fragmentCache = buildFragmentCache(cacheMaxSizeInBytes);
    this.autoRefreshEnabled = autoRefreshEnabled;
    this.stats = stats;
  }

  public Optional<Fragment> get(String cacheKey) {
    var fragmentFromCache = Optional.ofNullable(fragmentCache.getIfPresent(cacheKey));

    if (fragmentFromCache.isPresent()) {
      this.stats.recordHit();
    } else {
      this.stats.recordMiss();
    }

    return fragmentFromCache;
  }

  public void set(String cacheKey, Fragment fragment) {
    //TODO: Implement
    this.fragmentCache.put(cacheKey, fragment);
  }

  public long estimatedSize() {
    return this.fragmentCache.estimatedSize();
  }

  private Cache<String, Fragment> buildFragmentCache(long cacheMaxSizeInBytes) {
    return Caffeine.newBuilder()
      .maximumWeight(cacheMaxSizeInBytes)
      .weigher((String fragmentCacheKey, Fragment fragment) -> fragment.getContent().length())
      .expireAfter(new Expiry<String, Fragment>() {
        public long expireAfterCreate(String fragmentCacheKey, Fragment fragment, long currentTime) {
          long milliseconds = fragment.getExpirationTime()
            .minusMillis(System.currentTimeMillis())
            .toEpochMilli();
          return TimeUnit.MILLISECONDS.toNanos(milliseconds);
        }
        public long expireAfterUpdate(String fragmentCacheKey, Fragment fragment, long currentTime, long currentDuration) {
          return expireAfterCreate(fragmentCacheKey, fragment, currentTime);
        }
        public long expireAfterRead(String fragmentCacheKey, Fragment fragment, long currentTime, long currentDuration) {
          return currentDuration;
        }
      })
      .evictionListener((String fragmentCacheKey, Fragment fragment, RemovalCause cause) -> {
        if (cause == RemovalCause.SIZE) {
          logger.info("[Ableron] Fragment cache size exceeded. Removing key '{}' from cache. Consider increasing cache size", fragmentCacheKey);
        }
      })
      .build();
  }
}
