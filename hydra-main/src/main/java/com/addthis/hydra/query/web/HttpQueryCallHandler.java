/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.addthis.hydra.query.web;

import java.io.IOException;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import java.util.concurrent.TimeUnit;

import com.addthis.basis.kv.KVPairs;
import com.addthis.basis.util.Parameter;

import com.addthis.hydra.data.query.Query;
import com.addthis.hydra.data.query.QueryException;
import com.addthis.hydra.data.query.source.ErrorHandlingQuerySource;
import com.addthis.hydra.data.query.source.QuerySource;
import com.addthis.hydra.util.StringMapHelper;

import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Timer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.addthis.hydra.query.web.HttpUtils.sendError;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;

public final class HttpQueryCallHandler {

    private static final Logger log = LoggerFactory.getLogger(HttpQueryCallHandler.class);

    static final int maxQueryTime = Parameter.intValue("qmaster.maxQueryTime", 24 * 60 * 60); // one day

    static final Timer queryTimes = Metrics.newTimer(HttpQueryCallHandler.class, "queryTime", TimeUnit.MILLISECONDS, TimeUnit.SECONDS);

    private HttpQueryCallHandler() {
    }

    /**
     * special handler for query
     */
    public static void handleQuery(QuerySource querySource, KVPairs kv, HttpRequest request,
            ChannelHandlerContext ctx) throws Exception {
        String job = kv.getValue("job");
        String path = kv.getValue("path", kv.getValue("q", ""));
        Query query = new Query(job, new String[]{path}, new String[]{kv.getValue("ops"), kv.getValue("rops")});
        query.setTraced(kv.getIntValue("trace", 0) == 1);
        handleQuery(querySource, query, kv, request, ctx);
    }

    public static void handleQuery(QuerySource querySource, Query query, KVPairs kv, HttpRequest request,
            ChannelHandlerContext ctx) throws Exception {
        query.setParameterIfNotYetSet("hosts", kv.getValue("hosts"));
        query.setParameterIfNotYetSet("gate", kv.getValue("gate"));
        query.setParameterIfNotYetSet("originalrequest", kv.getValue("originalrequest"));
        SocketAddress remoteIP = ctx.channel().remoteAddress();
        if (remoteIP instanceof InetSocketAddress) { // only log implementations with known methods
            query.setParameterIfNotYetSet("remoteip", ((InetSocketAddress) remoteIP).getAddress().getHostAddress());
        }
        query.setParameterIfNotYetSet("parallel", kv.getValue("parallel"));
        query.setParameterIfNotYetSet("allowPartial", kv.getValue("allowPartial"));
        query.setParameterIfNotYetSet("dsortcompression", kv.getValue("dsortcompression"));

        String filename = kv.getValue("filename", "query");
        String format = kv.getValue("format", "json");
        String jsonp = kv.getValue("jsonp", kv.getValue("cbfunc"));
        String jargs = kv.getValue("jargs", kv.getValue("cbfunc-arg"));

        int timeout = Math.min(kv.getIntValue("timeout", maxQueryTime), maxQueryTime);
        query.setParameterIfNotYetSet("timeout", timeout);
        query.setParameter("sender", kv.getValue("sender"));

        if (log.isDebugEnabled()) {
            log.debug(new StringMapHelper()
                    .put("type", "query.starting")
                    .put("query.path", query.getPaths()[0])
                    .put("query.hosts", query.getParameter("hosts"))
                    .put("query.ops", query.getOps())
                    .put("trace", query.isTraced())
                    .put("sources", query.getParameter("sources"))
                    .put("time", System.currentTimeMillis())
                    .put("job.id", query.getJob())
                    .put("query.id", query.uuid())
                    .put("sender", query.getParameter("sender"))
                    .put("format", format)
                    .put("filename", filename)
                    .put("originalrequest", query.getParameter("originalrequest"))
                    .put("timeout", query.getParameter("timeout"))
                    .put("requestIP", query.getParameter("remoteip"))
                    .put("parallel", query.getParameter("parallel"))
                    .put("allowPartial", query.getParameter("allowPartial")).createKVPairs().toString());
        }
        try {
            // support legacy async query semantics
            query = LegacyHandler.handleQuery(query, kv, request, ctx);
            if (query == null) {
                return;
            }

            if (query.getJob() == null) {
                sendError(ctx, new HttpResponseStatus(500, "missing job"));
                return;
            }
            AbstractHttpOutput consumer = null;
            switch (format) {
                case "json":
                    consumer = new OutputJson(ctx, jsonp, jargs);
                    break;
                case "html":
                    consumer = new OutputHTML(ctx);
                    break;
                default:
                    consumer = OutputDelimited.create(ctx, filename, format);
                    break;
            }
            if (consumer != null) {
                consumer.writeStart();
                querySource.query(query, consumer); // TODO: use MQM and Outputs as Pipeline Handlers
            } else {
                sendError(ctx, new HttpResponseStatus(400, "Invalid format"));
            }
        } catch (IOException | QueryException e) {
            sendError(ctx, new HttpResponseStatus(500, "General/Query Error " + e.toString()));
            handleError(querySource, query);
        }
    }

    private static void handleError(QuerySource source, Query query) {
        if (source instanceof ErrorHandlingQuerySource) {
            ((ErrorHandlingQuerySource) source).handleError(query);
        }
    }
}
