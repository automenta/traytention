package spimedb.server;

import com.google.common.collect.Lists;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.suggest.Lookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spimedb.SpimeDB;
import spimedb.index.SearchResult;
import spimedb.util.JSON;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.util.List;

import static spimedb.server.WebIO.searchResultFull;



@Path("/")
@Api(description = "SpimeDB")
/**
 * http://editor.swagger.io/#/
 */
public class WebAPI {

    final static Logger logger = LoggerFactory.getLogger(WebAPI.class);

    private final SpimeDB db;
    private final WebServer web;

    public WebAPI(WebServer w) {
        this.web = w;
        this.db = w.db;
    }

    final static int MaxSuggestLength = 10;
    final static int MaxSuggestionResults = 16;
    final static int MaxSearchResults = 32;
    final static int MaxFacetResults = 64;

    @GET
    @Path("/suggest")
    //  @ApiOperation(value = "Find person by e-mail", notes = "Find person by e-mail", response = Person.class) //http://stackoverflow.com/questions/21148861/swagger-codegen-simple-jax-rs-example
    @Produces({MediaType.APPLICATION_JSON})
    @ApiOperation("Provides search query suggestions given a partially complete input query")
    public Response suggest(@QueryParam("q") String q) {

        if (q == null || (q = q.trim()).isEmpty() || q.length() > MaxSuggestLength)
            return Response.noContent().build();

        String x = q;
        return Response.ok((StreamingOutput) os -> {
            List<Lookup.LookupResult> x1 = db.suggest(x, MaxSuggestionResults);
            if (x1 != null)
                JSON.toJSON(Lists.transform(x1, y -> y.key), os);
        }).build();
    }

    @GET
    @Path("/find")
    @Produces({MediaType.APPLICATION_JSON})
    @ApiOperation("Finds the results of a text search query")
    public Response find(@QueryParam("q") String q) {

        if (q == null || (q = q.trim()).isEmpty())
            return Response.noContent().build();

        try {
            SearchResult r = db.find(q, MaxSearchResults);
            return Response.ok((StreamingOutput) os -> WebIO.send(r, os, searchResultFull)).build();
        } catch (IOException e) {
            return Response.serverError().build();
        } catch (ParseException e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }


    @GET
    @Path("/facet")
    @Produces({MediaType.APPLICATION_JSON})
    @ApiOperation("Finds matching search facets for a given dimension key")
    public Response facet(@QueryParam("q") String dimension) {
        if (!(dimension == null || (dimension = dimension.trim()).isEmpty())) {
            try {
                FacetResult x = db.facets(dimension, MaxFacetResults);
                if (x != null)
                    return Response.ok((StreamingOutput) os -> WebIO.stream(x, os)).build();
            } catch (IOException e) {
                return Response.serverError().build();
            }
        }

        return Response.noContent().build();
    }


}


//from: https://dzone.com/articles/jax-rs-streaming-response
//package com.markandjim
//
//@Path("/subgraph")
//public class ExtractSubGraphResource {
//    private final GraphDatabaseService database;
//
//    public ExtractSubGraphResource(@Context GraphDatabaseService database) {
//        this.database = database;
//    }
//
//    @GET
//    @Produces(MediaType.TEXT_PLAIN)
//    @Path("/{nodeId}/{depth}")
//    public Response hello(@PathParam("nodeId") long nodeId, @PathParam("depth") int depth) {
//        Node node = database.getNodeById(nodeId);
//
//        final Traverser paths =  Traversal.description()
//                .depthFirst()
//                .relationships(DynamicRelationshipType.withName("whatever"))
//                .evaluator( Evaluators.toDepth(depth) )
//                .traverse(node);
//
//        StreamingOutput stream = new StreamingOutput() {
//            @Override
//            public void write(OutputStream os) throws IOException, WebApplicationException {
//                Writer writer = new BufferedWriter(new OutputStreamWriter(os));
//
//                for (org.neo4j.graphdb.Path path : paths) {
//                    writer.write(path.toString() + "\n");
//                }
//                writer.flush();
//            }
//        };
//
//        return Response.ok(stream).build();
//    }


///**
// * Created by me on 5/4/17.
// */
//@Path("/test")
////@Api(value = "/pet", description = "Operations about pets", authorizations = {
////        @Authorization(value = "petstore_auth",
////                scopes = {
////                        @AuthorizationScope(scope = "write:pets", description = "modify pets in your account"),
////                        @AuthorizationScope(scope = "read:pets", description = "read your pets")
////                })
////})
////@Produces({"application/json", "application/xml"})
//
//public class ExampleJaxResource {
//    @GET
//    @Produces("text/plain")
//    public String get() {
//        return "hello world";
//    }
//}
