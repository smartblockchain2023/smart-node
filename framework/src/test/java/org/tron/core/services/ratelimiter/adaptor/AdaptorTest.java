package org.tron.core.services.ratelimiter.adaptor;

import com.google.common.cache.Cache;
import com.google.common.util.concurrent.RateLimiter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import org.junit.Assert;
import org.junit.Test;
import org.tron.common.utils.ReflectUtils;
import org.tron.core.services.ratelimiter.adapter.GlobalPreemptibleAdapter;
import org.tron.core.services.ratelimiter.adapter.IPQPSRateLimiterAdapter;
import org.tron.core.services.ratelimiter.adapter.QpsRateLimiterAdapter;
import org.tron.core.services.ratelimiter.strategy.GlobalPreemptibleStrategy;
import org.tron.core.services.ratelimiter.strategy.IPQpsStrategy;
import org.tron.core.services.ratelimiter.strategy.QpsStrategy;

public class AdaptorTest {

  @Test
  public void testStrategy() {
    String paramString1 = "qps=5 notExist=6";
    IPQPSRateLimiterAdapter adapter1 = new IPQPSRateLimiterAdapter(paramString1);
    IPQpsStrategy strategy1 = (IPQpsStrategy) ReflectUtils.getFieldObject(adapter1,
        "strategy");

    Assert.assertEquals(5.0d, Double.parseDouble(
        ReflectUtils.getFieldValue(strategy1.getMapParams().get("qps"),
            "value").toString()), 0.0);
    Assert.assertNull(strategy1.getMapParams().get("notExist"));

    String paramString2 = "qps=5xyz";
    IPQPSRateLimiterAdapter adapter2 = new IPQPSRateLimiterAdapter(paramString2);
    IPQpsStrategy strategy2 = (IPQpsStrategy) ReflectUtils.getFieldObject(adapter2,
        "strategy");

    Assert.assertEquals(IPQpsStrategy.DEFAULT_IPQPS, Double.valueOf(
        ReflectUtils.getFieldValue(strategy2.getMapParams().get("qps"),
            "value").toString()));
  }

  @Test
  public void testIPQPSRateLimiterAdapter() {
    String paramString = "qps=5";
    IPQPSRateLimiterAdapter adapter = new IPQPSRateLimiterAdapter(paramString);

    IPQpsStrategy strategy = (IPQpsStrategy) ReflectUtils.getFieldObject(adapter,
        "strategy");
    Assert.assertEquals(5.0d, Double
        .parseDouble(ReflectUtils.getFieldValue(strategy.getMapParams().get("qps"),
            "value").toString()), 0.0);

    long t0 = System.currentTimeMillis();
    for (int i = 0; i < 20; i++) {
      strategy.acquire("1.2.3.4");
    }
    long t1 = System.currentTimeMillis();
    Assert.assertTrue(t1 - t0 > 3500);

    t0 = System.currentTimeMillis();
    for (int i = 0; i < 20; i++) {
      if (i % 2 == 0) {
        strategy.acquire("1.2.3.4");
      } else {
        strategy.acquire("4.3.2.1");
      }
    }
    t1 = System.currentTimeMillis();
    Assert.assertTrue(t1 - t0 > 1500);
    Cache<String, RateLimiter> ipLimiter = (Cache<String, RateLimiter>) ReflectUtils
        .getFieldObject(strategy, "ipLimiter");
    Assert.assertEquals(2, ipLimiter.size());
  }

  @Test
  public void testGlobalPreemptibleAdapter() {
    String paramString1 = "permit=1";
    GlobalPreemptibleAdapter adapter1 = new GlobalPreemptibleAdapter(paramString1);
    GlobalPreemptibleStrategy strategy1 = (GlobalPreemptibleStrategy) ReflectUtils
        .getFieldObject(adapter1, "strategy");
    Assert.assertEquals(1, Integer.parseInt(
        ReflectUtils.getFieldValue(strategy1.getMapParams().get("permit"),
            "value").toString()));
    boolean first = strategy1.acquire();
    Assert.assertTrue(first);

    boolean second = strategy1.acquire();
    Assert.assertFalse(second);

    strategy1.release();
    boolean secondAfterOneRelease = strategy1.acquire();
    Assert.assertTrue(secondAfterOneRelease);

    String paramString2 = "permit=3";
    GlobalPreemptibleAdapter adapter2 = new GlobalPreemptibleAdapter(paramString2);
    GlobalPreemptibleStrategy strategy2 = (GlobalPreemptibleStrategy) ReflectUtils
        .getFieldObject(adapter2, "strategy");
    Assert.assertEquals(3, Integer.parseInt(
        ReflectUtils.getFieldValue(strategy2.getMapParams().get("permit"),
            "value").toString()));

    first = strategy2.acquire();
    Assert.assertTrue(first);
    second = strategy2.acquire();
    Assert.assertTrue(second);
    boolean third = strategy2.acquire();
    Assert.assertTrue(third);

    boolean four = strategy2.acquire();
    Assert.assertFalse(four);

    strategy2.release();
    boolean fourAfterOneRelease = strategy2.acquire();
    Assert.assertTrue(fourAfterOneRelease);

    Semaphore sp = (Semaphore) ReflectUtils.getFieldObject(strategy2, "sp");
    Assert.assertEquals(0, sp.availablePermits());
    strategy2.release();
    strategy2.release();
    strategy2.release();
    Assert.assertEquals(3, sp.availablePermits());

  }

  @Test
  public void testQpsRateLimiterAdapter() {
    String paramString = "qps=5";
    QpsRateLimiterAdapter adapter = new QpsRateLimiterAdapter(paramString);

    QpsStrategy strategy = (QpsStrategy) ReflectUtils.getFieldObject(adapter, "strategy");
    Assert.assertEquals(5, Double
        .parseDouble(ReflectUtils.getFieldValue(strategy.getMapParams().get("qps"),
            "value").toString()), 0.0);
    strategy.acquire();

    long t0 = System.currentTimeMillis();
    CountDownLatch latch = new CountDownLatch(20);
    for (int i = 0; i < 20; i++) {
      Thread thread = new Thread(new AdaptorThread(latch, strategy));
      thread.start();
    }

    try {
      latch.await();
    } catch (InterruptedException e) {
      System.out.println(e.getMessage());
    }
    long t1 = System.currentTimeMillis();
    Assert.assertTrue(t1 - t0 > 4000);
  }
}


