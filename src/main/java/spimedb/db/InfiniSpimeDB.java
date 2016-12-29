package spimedb.db;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.stats.Stats;
import org.jetbrains.annotations.Nullable;
import spimedb.NObject;

/**
 * Infinispan Impl
 * http://infinispan.org/docs/9.0.x/user_guide/user_guide.html
 */
public class InfiniSpimeDB {

    public static RTreeSpimeDB get(@Nullable String path) {

        GlobalConfiguration global = new GlobalConfigurationBuilder()
                .serialization()
//                .addAdvancedExternalizer(new PermanentConceptExternalizer())
//                .addAdvancedExternalizer(new ConceptExternalizer())
                .build();

        ConfigurationBuilder cfg = new ConfigurationBuilder();
        cfg
            .unsafe()
            .storeAsBinary().storeKeysAsBinary(true).storeValuesAsBinary(true)
            .jmxStatistics().disable();
            //.versioning().disable()
            //.passivation(true)
            //cb.locking().concurrencyLevel(1);
            //cb.customInterceptors().addInterceptor();

        if (path!=null)
            cfg.persistence().addSingleFileStore().location(path);

        DefaultCacheManager cm = new DefaultCacheManager(global, cfg.build());

//        this.conceptsLocal = new DecoratedCache<>(
//                concepts.getAdvancedCache(),
//                Flag.CACHE_MODE_LOCAL, /*Flag.SKIP_LOCKING,*/ Flag.SKIP_OWNERSHIP_CHECK,
//                Flag.SKIP_REMOTE_LOOKUP);
//        this.conceptsLocalNoResult = conceptsLocal.withFlags(Flag.IGNORE_RETURN_VALUES, Flag.SKIP_CACHE_LOAD, Flag.SKIP_REMOTE_LOOKUP);


        Cache<String, NObject> vertex =
                cm.getCache("vertex");


        enableStats(vertex);

        RTreeSpimeDB db = new RTreeSpimeDB(vertex);

        return db;
    }

    private static void enableStats(Cache vertex) {
        vertex.getAdvancedCache().getStats().setStatisticsEnabled(true);
    }

    private static String statString(Cache c) {
        Stats s = c.getAdvancedCache().getStats();

        return  "hits=" + Math.round((100f * s.getHits() / s.getRetrievals())) + "%" +
               " read=" + s.getAverageReadTime() + "ms" +
               " write=" + s.getAverageReadTime() + "ms"
                ;
    }
}
