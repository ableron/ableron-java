package io.github.ableron;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.RemovalCause;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class FragmentCache {

  private final static long ONE_MINUTE_IN_MILLIS = Duration.ofMinutes(1).toMillis();
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final Cache<String, Fragment> fragmentCache;
  private final boolean autoRefreshEnabled;
  private final int maxRefreshAttempts;
  private final Map<String, Integer> refreshAttempts = new ConcurrentHashMap<>();
  private final Set<String> activeFragments = new ConcurrentHashMap<String, Boolean>().keySet(true);
  private final Integer inactiveFragmentsMaxRefreshs;
  private final Map<String, Integer> inactiveFragmentRefreshs = new ConcurrentHashMap<>();
  private final CacheStats stats = new CacheStats();
  private final ScheduledExecutorService autoRefreshScheduler = Executors.newScheduledThreadPool(3);

  public FragmentCache(AbleronConfig config) {
    this.autoRefreshEnabled = config.cacheAutoRefreshEnabled();
    this.maxRefreshAttempts = config.getCacheAutoRefreshMaxAttempts();
    this.inactiveFragmentsMaxRefreshs = config.getCacheAutoRefreshInactiveFragmentsMaxRefreshs();
    this.fragmentCache = buildFragmentCache(config.getCacheMaxSizeInBytes());
  }

  public Optional<Fragment> get(String cacheKey) {
    var fragmentFromCache = Optional.ofNullable(fragmentCache.getIfPresent(cacheKey));

    if (fragmentFromCache.isPresent()) {
      this.stats.recordHit();

      if (this.autoRefreshEnabled) {
        this.activeFragments.add(cacheKey);
      }
    } else {
      this.stats.recordMiss();
    }

    this.stats.setItemCount(fragmentCache.estimatedSize());
    return fragmentFromCache;
  }

  public FragmentCache set(String cacheKey, Fragment fragment) {
    return set(cacheKey, fragment, null);
  }

  public FragmentCache set(String cacheKey, Fragment fragment, Supplier<Fragment> autoRefresh) {
    this.fragmentCache.put(cacheKey, fragment);

    if (this.autoRefreshEnabled && autoRefresh != null && fragment.getExpirationTime().isAfter(Instant.now())) {
      this.registerAutoRefresh(cacheKey, autoRefresh, this.calculateFragmentRefreshDelay(fragment));
    }

    return this;
  }

  public FragmentCache clear() {
    this.autoRefreshScheduler.shutdownNow();
    this.refreshAttempts.clear();
    this.activeFragments.clear();
    this.inactiveFragmentRefreshs.clear();
    this.fragmentCache.invalidateAll();
    return this;
  }

  public long estimatedCacheEntryCount() {
    return this.fragmentCache.estimatedSize();
  }

  public CacheStats stats() {
    return this.stats;
  }

  private void registerAutoRefresh(String cacheKey, Supplier<Fragment> autoRefresh, long refreshDelayMs) {
    autoRefreshScheduler.schedule(() -> {
      if (shouldPerformAutoRefresh(cacheKey)) {
        try {
          var fragment = autoRefresh.get();

          if (isFragmentCacheable(fragment)) {
            var oldCacheEntry = fragmentCache.getIfPresent(cacheKey);
            this.set(cacheKey, fragment, autoRefresh);
            this.handleSuccessfulCacheRefresh(cacheKey, oldCacheEntry);
          } else {
            this.handleFailedCacheRefreshAttempt(cacheKey, autoRefresh);
          }
        } catch (Exception e) {
          logger.error("[Ableron] Unable to refresh cached fragment '{}'", cacheKey, e);
          this.handleFailedCacheRefreshAttempt(cacheKey, autoRefresh);
        }
      } else {
        inactiveFragmentRefreshs.remove(cacheKey);
        logger.debug("[Ableron] Stopping auto refresh of fragment '{}': Inactive fragment", cacheKey);
      }
    }, refreshDelayMs, TimeUnit.MILLISECONDS);
  }

  private long calculateFragmentRefreshDelay(Fragment fragment) {
    return Math.max(Math.round((fragment.getExpirationTime().toEpochMilli() - Instant.now().toEpochMilli()) * 0.85), 10);
  }

  private boolean shouldPerformAutoRefresh(String cacheKey) {
    return activeFragments.contains(cacheKey)
      || Optional.ofNullable(inactiveFragmentRefreshs.get(cacheKey)).orElse(0) < inactiveFragmentsMaxRefreshs;
  }

  private boolean isFragmentCacheable(Fragment fragment) {
    return fragment != null
      && HttpUtil.HTTP_STATUS_CODES_CACHEABLE.contains(fragment.getStatusCode())
      && fragment.getExpirationTime().isAfter(Instant.now());
  }

  private void handleSuccessfulCacheRefresh(String cacheKey, Fragment oldCacheEntry) {
    this.refreshAttempts.remove(cacheKey);

    if (this.activeFragments.contains(cacheKey)) {
      this.activeFragments.remove(cacheKey);
      this.inactiveFragmentRefreshs.remove(cacheKey);
    } else {
      this.inactiveFragmentRefreshs.put(
        cacheKey,
        Optional.ofNullable(this.inactiveFragmentRefreshs.get(cacheKey)).orElse(0) + 1
      );
    }

    this.stats.recordRefreshSuccess();

    if (oldCacheEntry != null) {
      this.logger.debug("[Ableron] Refreshed cached fragment '{}' {}ms before expiration", cacheKey, oldCacheEntry.getExpirationTime().minusMillis(Instant.now().toEpochMilli()).toEpochMilli());
    } else {
      this.logger.debug("[Ableron] Refreshed expired cached fragment '{}'", cacheKey);
    }
  }

  private void handleFailedCacheRefreshAttempt(String cacheKey, Supplier<Fragment> autoRefresh) {
    var attempts = Optional.ofNullable(this.refreshAttempts.get(cacheKey)).orElse(0) + 1;
    this.stats.recordRefreshFailure();

    if (attempts < this.maxRefreshAttempts) {
      this.logger.error("[Ableron] Unable to refresh cached fragment '{}': Attempt #{} failed. Retry in 1s", cacheKey, attempts);
      this.refreshAttempts.put(cacheKey, attempts);
      this.registerAutoRefresh(cacheKey, autoRefresh, 1000);
    } else {
      this.logger.error("[Ableron] Unable to refresh cached fragment '{}' after {} attempts. Stopping auto refresh", cacheKey, this.maxRefreshAttempts);
      this.refreshAttempts.remove(cacheKey);
      this.activeFragments.remove(cacheKey);
      this.inactiveFragmentRefreshs.remove(cacheKey);
    }
  }

  private Cache<String, Fragment> buildFragmentCache(long cacheMaxSizeInBytes) {
    final var evictedCacheItemCount = new AtomicLong();
    final var evictedCacheItemCounterStartTimeMillis = new AtomicLong(System.currentTimeMillis());

    return Caffeine.newBuilder()
      .maximumWeight(cacheMaxSizeInBytes)
      .weigher((String fragmentCacheKey, Fragment fragment) -> fragmentCacheKey.length() + fragment.getContent().length())
      .expireAfter(new Expiry<String, Fragment>() {
        public long expireAfterCreate(String fragmentCacheKey, Fragment fragment, long currentTime) {
          long milliseconds = fragment.getExpirationTime()
            .minusMillis(Instant.now().toEpochMilli())
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
          evictedCacheItemCount.incrementAndGet();

          if (evictedCacheItemCount.get() == 1
            || System.currentTimeMillis() - evictedCacheItemCounterStartTimeMillis.get() > ONE_MINUTE_IN_MILLIS) {
            logger.warn("[Ableron] Fragment cache size exceeded. Removed {} items from cache due to capacity. Consider increasing cache size", evictedCacheItemCount.get());
            evictedCacheItemCounterStartTimeMillis.set(System.currentTimeMillis());
          }
        }
      })
      .build();
  }
}
