/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package spimedb.server;


import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Objects;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.cache.DirectBufferCache;
import io.undertow.server.handlers.encoding.ContentEncodingRepository;
import io.undertow.server.handlers.encoding.DeflateEncodingProvider;
import io.undertow.server.handlers.encoding.EncodingHandler;
import io.undertow.server.handlers.encoding.GzipEncodingProvider;
import io.undertow.server.handlers.resource.CachingResourceManager;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.eclipse.collections.impl.factory.Sets;
import org.jboss.resteasy.plugins.server.undertow.UndertowJaxrsServer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;
import org.xnio.BufferAllocator;
import spimedb.NObject;
import spimedb.SpimeDB;
import spimedb.index.DObject;
import spimedb.index.SearchResult;
import spimedb.query.Query;
import spimedb.util.HTTP;
import spimedb.util.JSON;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import static io.undertow.Handlers.resource;
import static io.undertow.UndertowOptions.ENABLE_HTTP2;
import static java.lang.Double.parseDouble;
import static spimedb.util.HTTP.getStringParameter;

/**
 * @author me
 *         see:
 *         https://docs.jboss.org/resteasy/docs/3.1.2.Final/userguide/html/
 *         https://docs.jboss.org/resteasy/docs/3.1.2.Final/userguide/html/RESTEasy_Embedded_Container.html#d4e1380
 */
public class WebServer extends PathHandler {

    public UndertowJaxrsServer server;


    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(WebServer.class);

    public static final String staticPath = Paths.get("src/main/resources/public/").toAbsolutePath().toString();

    private static final int BUFFER_SIZE = 32 * 1024;

    public final SpimeDB db;

    @Deprecated
    private final double websocketOutputRateLimitBytesPerSecond = 64 * 1024;

    private int port = 0;
    private String host = null;

    static final ContentEncodingRepository compression = new ContentEncodingRepository()
            .addEncodingHandler("gzip", new GzipEncodingProvider(), 100)
            .addEncodingHandler("deflate", new DeflateEncodingProvider(), 50);


    //final Default nar = NARBuilder.newMultiThreadNAR(1, new RealTime.DS());

//    @Override
//    public void handleRequest(HttpServerExchange exchange) throws Exception {
//        String s = exchange.getQueryString();
//        nar.believe(
//                s.isEmpty() ?
//
//            $.func(
//                $.the(exchange.getDestinationAddress().toString()),
//                $.quote(exchange.getRequestURL())
//             )
//                        :
//            $.func(
//                $.the(exchange.getDestinationAddress().toString()),
//                $.quote(exchange.getRequestURL()),
//                $.the(s)  ),
//
//            Tense.Present
//        );
//
//        super.handleRequest(exchange);
//    }

    public WebServer(final SpimeDB db) {
        super();
        this.db = db;


//        nar.log();
//        nar.loop(10f);


        initStaticResource(db);


//        try {
//            addPrefixPath("/", WebdavServlet.get("/"));
//        } catch (ServletException e) {
//            logger.error("{}", e);
//        }

        addPrefixPath("/tag", ex -> HTTP.stream(ex, (o) -> {
            try {
                o.write(JSON.toJSONBytes(db.tags().stream().map(db::get).toArray(NObject[]::new)));
            } catch (IOException e) {
                logger.error("tag {}", e);
            }
        }));

        addPrefixPath("/thumbnail", ex -> {
            send(getStringParameter(ex, NObject.ID), "thumbnail", "image/jpg", ex);
        });
        addPrefixPath("/data", ex -> {
            send(getStringParameter(ex, NObject.ID), "data", "application/pdf", ex);
        });

        addPrefixPath("/earth", ex -> HTTP.stream(ex, (o) -> {
            String b = getStringParameter(ex, "r");
            String[] bb = b.split("_");
            if (bb.length != 4) {
                ex.setStatusCode(StatusCodes.BAD_REQUEST);
                return;
            }

            double[] lons = new double[2], lats = new double[2];

            lons[0] = parseDouble(bb[0]);
            lats[0] = parseDouble(bb[1]);
            lons[1] = parseDouble(bb[2]);
            lats[1] = parseDouble(bb[3]);

            SearchResult r = db.get(new Query().limit(32).where(lons, lats));
            WebIO.send(r, o, WebIO.searchResultSummary);

        }));


        addPrefixPath("/tell/json", (e) -> {
            //POST only
            if (e.getRequestMethod().equals(HttpString.tryFromString("POST"))) {
                //System.out.println(e);
                //System.out.println(e.getRequestHeaders());

                e.getRequestReceiver().receiveFullString((ex, s) -> {
                    JsonNode x = JSON.fromJSON(s);
                    if (x != null)
                        db.add(x);
                    else {
                        e.setStatusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
                    }
                });

                e.endExchange();
            }
        });


        /* client attention management */
        //addPrefixPath("/client", websocket(new ClientSession(db, websocketOutputRateLimitBytesPerSecond)));

//        addPrefixPath("/anon",
//            websocket( new AnonymousSession(db) )
//        );
//
//        addPrefixPath("/on/tag/",
//                //getRequestPath().substring(8)
//                websocket( AnonymousSession.tag(db, "public") ) );
//
//        addPrefixPath("/console",
//                //getRequestPath().substring(8)
//                websocket( new ConsoleSession(db) ) );

        //addPrefixPath("/admin", websocket(new Admin(db)));

        restart();

    }


    private void initStaticResource(SpimeDB db) {
        File staticPath = Paths.get(WebServer.staticPath).toFile();
        File myStaticPath = db.file != null ? db.file.getParentFile().toPath().resolve("public").toFile() : null;

        int transferMinSize = 1024 * 1024;
        final int METADATA_MAX_AGE = 3 * 1000; //ms

        ChainedResourceManager res = new ChainedResourceManager();
        if (db.indexPath != null && myStaticPath != null && myStaticPath.exists()) {
            //local override
            logger.info("static resource: {}", myStaticPath);
            res.add(
                    new FileResourceManager(myStaticPath, transferMinSize, true, "/")
            );
        }

        ResourceHandler rr;
        if (staticPath != null && staticPath.exists()) {
            //development mode: serve the files from the FS
            logger.info("static resource: {}", staticPath);
            res.add(
                    new FileResourceManager(staticPath, transferMinSize, true, "/")
            );
            rr = resource(res);
        } else {
            logger.info("static resource: (classloader)");
            //production mode: serve from classpath
            res.add(
                    new ClassPathResourceManager(getClass().getClassLoader(), "public")
            );

            DirectBufferCache dataCache = new DirectBufferCache(1000, 10,
                    16 * 1024 * 1024, BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR,
                    METADATA_MAX_AGE);

            CachingResourceManager cres = new CachingResourceManager(
                    100,
                    transferMinSize /* max size */,
                    dataCache, res, METADATA_MAX_AGE);

            rr = resource(cres);
        }

        rr.setCacheTime(24 * 60 * 60 * 1000);
        addPrefixPath("/", rr);
    }

    public void setHost(String host) {

        if (!Objects.equal(this.host, host)) {
            this.host = host;
            restart();
        }
    }

    private synchronized void restart() {
        String host = this.host;

        if (host == null)
            host = "0.0.0.0"; //any IPv4

        if (port == 0)
            return;

        Undertow.Builder b = Undertow.builder()
                .addHttpListener(port, host)
                .setServerOption(ENABLE_HTTP2, true);

        if (compression != null)
            b.setHandler(new EncodingHandler(this, compression));

        UndertowJaxrsServer nextServer = new UndertowJaxrsServer();

        if (server != null) {
            try {
                logger.error("stop: {}", server);
                server.stop();
            } catch (Exception e) {
                logger.error("http stop: {}", e);
                this.server = null;
            }
        }

        try {
            logger.info("listen {}:{}", host, port);
            (this.server = nextServer).start(b);


//            server.deploy(deployment()
//                    .setDeploymentName("swagger")
//                    .setContextPath("/swagger")
//                    .setClassLoader(getClass().getClassLoader())
//                    .addServlet(servlet(Swagger.class))
//            );

            server.deploy(new WebApp(), "/api");
            server.addResourcePrefixPath("/", new NotAServlet(this));

        } catch (Exception e) {
            logger.error("http start: {}", e);
            this.server = null;
        }

    }




    @ApplicationPath("/")
    public final class WebApp extends Application {

        @Override
        public Set getSingletons() {
            return Sets.mutable.of(
                new WebAPI(WebServer.this)
            );
        }

        @Override
        public Set<Class<?>> getClasses() {
            HashSet<Class<?>> classes = new HashSet<Class<?>>();
            classes.add(ExampleJaxResource.class);
            classes.add(io.swagger.jaxrs.listing.ApiListingResource.class);
            classes.add(io.swagger.jaxrs.listing.SwaggerSerializers.class);
            return classes;
        }

    }
    /**
     * servlets - wtf!!!!!!
     */
    private final static class NotAServlet extends ResourceHandler {

        private final HttpHandler notAServlet;

        public NotAServlet(HttpHandler thankfullyNotAServlet) {
            this.notAServlet = thankfullyNotAServlet;
        }

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            notAServlet.handleRequest(exchange);
        }

    }

    public void setPort(int port) {
        if (this.port != port) {
            this.port = port;
            restart();
        }
    }

    private void send(@Nullable String id, String field, @Deprecated @Nullable String contentType, HttpServerExchange ex) {
        if (id != null) {

            DObject d = db.get(id);
            if (d != null) {
                Object f = d.get(field);

                if (f instanceof String) {
                    //interpret the string stored at this as a URL or a redirect to another field
                    String s = (String) f;
                    switch (s) {
                        case "data":
                            if (!field.equals("data"))
                                send(id, "data", contentType, ex);
                            else {
                                //infinite loop
                                throw new UnsupportedOperationException("document field redirect cycle");
                            }
                            break;
                        default:
                            if (s.startsWith("file:")) {
                                File ff = new File(s.substring(5));
                                if (ff.exists()) {
                                    HTTP.stream(ex, (o) -> {
                                        try {
                                            IOUtils.copyLarge(new FileInputStream(ff), o, new byte[BUFFER_SIZE]);
                                        } catch (IOException e) {
                                            ex.setStatusCode(404);
                                        }
                                    }, contentType != null ? contentType : "text/plain");

                                } else {
                                    ex.setStatusCode(404);
                                }
                            }
                            break;
                    }
                } else if (f instanceof byte[]) {

                    byte[] b = (byte[]) f;

                    HTTP.stream(ex, (o) -> {
                        try {
                            o.write(b);
                        } catch (IOException e) {
                        }
                    }, contentType != null ? contentType : "text/plain");

                } else {
                    ex.setStatusCode(404);
                }

            } else {
                ex.setStatusCode(404);
            }

        } else {
            ex.setStatusCode(404);
        }
    }


//       @Test
//   public void testApplicationPath() throws Exception
//   {
//      server.deploy(MyApp.class);
//      Client client = ClientBuilder.newClient();
//      String val = client.target(TestPortProvider.generateURL("/base/test"))
//                         .request().get(String.class);
//      Assert.assertEquals("hello world", val);
//      client.close();
//   }
//
//   @Test
//   public void testApplicationContext() throws Exception
//   {
//      server.deploy(MyApp.class, "/root");
//      Client client = ClientBuilder.newClient();
//      String val = client.target(TestPortProvider.generateURL("/root/test"))
//                         .request().get(String.class);
//      Assert.assertEquals("hello world", val);
//      client.close();
//   }
//
//   @Test
//   public void testDeploymentInfo() throws Exception
//   {
//      DeploymentInfo di = server.undertowDeployment(MyApp.class);
//      di.setContextPath("/di");
//      di.setDeploymentName("DI");
//      server.deploy(di);
//      Client client = ClientBuilder.newClient();
//      String val = client.target(TestPortProvider.generateURL("/di/base/test"))
//                         .request().get(String.class);
//      Assert.assertEquals("hello world", val);
//      client.close();
//   }
}


//.setDirectoryListingEnabled(true)
//.setHandler(path().addPrefixPath("/", ClientResources.handleClientResources())

//        addPrefixPath("/tag/meta", new HttpHandler() {
//
//            @Override
//            public void handleRequest(HttpServerExchange ex) throws Exception {
//
//                sendTags(
//                        db.searchID(
//                                getStringArrayParameter(ex, "id"), 0, 60, "tag"
//                        ),
//                        ex);
//
//            }
//
//        });
//        addPrefixPath("/style/meta", new HttpHandler() {
//
//            @Override
//            public void handleRequest(HttpServerExchange ex) throws Exception {
//
//                send(json(
//                                db.searchID(
//                                        getStringArrayParameter(ex, "id"), 0, 60, "style"
//                                )),
//                        ex);
//
//            }
//
//        });

//CORS fucking sucks
        /*  .header("Access-Control-Allow-Origin", "*")
            .header("Access-Control-Allow-Headers", "origin, content-type, accept, authorization")
            .header("Access-Control-Allow-CredentialMax-Age", "1209600")
         */

//https://github.com/undertow-io/undertow/blob/master/examples/src/main/java/io/undertow/examples/sessionhandling/SessionServer.java

//        addPrefixPath("/socket", new WebSocketCore(
//                index
//        ).handler());
//
//        addPrefixPath("/tag/index", new ChannelSnapshot(index));
//
//
//
//        addPrefixPath("/tag", (new WebSocketCore() {
//
//            final String cachePath = "cache";
//            final int cacheProxyPort = 16000;
//
//            @Override
//            public synchronized Channel getChannel(WebSocketCore.WebSocketConnection socket, String id) {
//                Channel c = super.getChannel(socket, id);
//
//                if (c == null) {
//                    //Tag t = new Tag(id, id);
//                    c = new ElasticChannel(db, id, "tag");
//                    super.addChannel(c);
//                }
//
//                return c;
//            }
//
//            @Override
//            protected void onOperation(String operation, Channel c, JsonNode param, WebSocketChannel socket) {
//
//                //TODO prevent interrupting update operation if already in-progress
//                switch (operation) {
//                    case "update":
//                        try {
//                            ObjectNode meta = (ObjectNode) c.getSnapshot().get("meta");
//                            if (meta!=null && meta.has("kmlLayer")) {
//                                String kml = meta.get("kmlLayer").textValue();
//
//                                {
//                                    ObjectNode nc = c.getSnapshot();
//                                    meta = (ObjectNode) nc.get("meta");
//
//                                    meta.put("status", "Updating");
//                                    c.commit(nc);
//                                }
//
//                                System.out.println("Updating " + c);
//
//                                //TODO replace proxy with HttpRequestCached:
////                                try {
////                                    new ImportKML(db, cache.proxy, c.id, kml).run();
////                                } catch (Exception e) {
////                                    ObjectNode nc = c.getSnapshot();
////                                    meta = (ObjectNode) nc.get("meta");
////                                    meta.put("status", e.toString());
////                                    c.commit(nc);
////                                    throw e;
////                                }
//
//                                {
//                                    ObjectNode nc = c.getSnapshot();
//                                    meta = (ObjectNode) nc.get("meta");
//
//                                    meta.put("status", "Ready");
//                                    meta.put("modifiedAt", new Date().getTime());
//                                    c.commit(nc);
//
//                                }
//
//                            }
//                        } catch (Exception e) {
//                            e.printStackTrace();
//                        }
//
//                        break;
//                }
//
//            }
//
//        }).handler());
//
//

//
//addPrefixPath("/wikipedia", new Wikipedia());

