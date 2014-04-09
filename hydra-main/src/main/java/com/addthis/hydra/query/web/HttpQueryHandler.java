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

import java.io.InputStream;
import java.io.StringWriter;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import java.nio.CharBuffer;

import com.addthis.basis.kv.KVPairs;

import com.addthis.codec.CodecJSON;
import com.addthis.hydra.data.query.Query;
import com.addthis.hydra.job.IJob;
import com.addthis.hydra.job.JobTask;
import com.addthis.hydra.query.MeshQueryMaster;
import com.addthis.hydra.query.QueryTracker;
import com.addthis.hydra.query.util.HostEntryInfo;
import com.addthis.hydra.util.MetricsServletShim;
import com.addthis.maljson.JSONArray;
import com.addthis.maljson.JSONObject;

import com.google.common.base.Optional;

import com.fasterxml.jackson.core.JsonGenerator;

import org.apache.commons.io.output.StringBuilderWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.addthis.hydra.query.web.HttpUtils.sendError;
import static com.addthis.hydra.query.web.HttpUtils.sendRedirect;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.CharsetUtil;

@ChannelHandler.Sharable
public class HttpQueryHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger log = LoggerFactory.getLogger(HttpQueryHandler.class);

    private final HttpStaticFileHandler staticFileHandler = new HttpStaticFileHandler();

    private final QueryServer queryServer;

    /**
     * used for tracking metrics and other interesting things about queries
     * that we have run.  Provides insight into currently running queries
     * and gives ability to cancel a query before it completes.
     */
    private final QueryTracker tracker;

    /**
     * primary query source
     */
    private final MeshQueryMaster meshQueryMaster;

    private final MetricsServletShim fakeMetricsServlet;

    public HttpQueryHandler(QueryServer queryServer, QueryTracker tracker, MeshQueryMaster meshQueryMaster) {
        super(true); // auto release
        this.queryServer = queryServer;
        this.tracker = tracker;
        this.meshQueryMaster = meshQueryMaster;
        this.fakeMetricsServlet = new MetricsServletShim();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.warn("Exception caught while serving http query endpoint", cause);
        if (ctx.channel().isActive()) {
            sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        messageReceived(ctx, request); // redirect to more sensible netty5 naming scheme
    }

    protected void messageReceived(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (!request.getDecoderResult().isSuccess()) {
            sendError(ctx, HttpResponseStatus.BAD_REQUEST);
            return;
        }
        QueryStringDecoder urlDecoder = new QueryStringDecoder(request.getUri());
        String target = urlDecoder.path();
        if (request.getMethod() == HttpMethod.POST) {
            log.trace("POST Method handling triggered for {}", request);
            String postBody = request.content().toString(CharsetUtil.UTF_8);
            log.trace("POST body {}", postBody);
            urlDecoder = new QueryStringDecoder(postBody, false);
        }
        log.trace("target uri {}", target);
        KVPairs kv = new KVPairs();
        for (Map.Entry<String, List<String>> entry : urlDecoder.parameters().entrySet()) {
            String k = entry.getKey();
            String v = entry.getValue().get(0); // ignore duplicates
            kv.add(k, v);
        }
        log.trace("kv pairs {}", kv);
        switch (target) {
            case "/":
                sendRedirect(ctx, "/query/index.html");
                break;
            case "/q/":
                sendRedirect(ctx, "/query/call?" + kv.toString());
                break;
            case "/query/call":
            case "/query/call/":
                // TODO jsonp enable
                QueryServer.rawQueryCalls.inc();
                HttpQueryCallHandler.handleQuery(meshQueryMaster, kv, request, ctx);
                break;
            default:
                fastHandle(ctx, request, target, kv);
                break;
        }
    }

    private void fastHandle(ChannelHandlerContext ctx, FullHttpRequest request, String target,
            KVPairs kv) throws Exception {

        String cbf = kv.getValue("cbfunc");
        String cba = kv.getValue("cbfunc-arg");
        boolean jsonp = cbf != null;
        StringBuilderWriter writer = new StringBuilderWriter(50);
        HttpResponse response = HttpUtils.startResponse(writer, cbf, cba);

        switch (target) {
            case "/metrics":
                fakeMetricsServlet.writeMetrics(writer, kv);
                break;
            case "/query/list":
                writer.write("[\n");
                for (QueryTracker.QueryEntryInfo stat : tracker.getRunning()) {
                    writer.write(CodecJSON.encodeString(stat).concat(",\n"));
                }
                writer.write("]");
                break;
            case "/completed/list":
                writer.write("[\n");
                for (QueryTracker.QueryEntryInfo stat : tracker.getCompleted()) {
                    writer.write(CodecJSON.encodeString(stat).concat(",\n"));
                }
                writer.write("]");
                break;
            case "/host/list":
                writer.write("[\n");
                for (HostEntryInfo hostEntryInfo : tracker.getQueryHosts(kv.getValue("uuid"))) {
                    writer.write("{'hostname':'" + hostEntryInfo.getHostName() + "','lines':'" + hostEntryInfo.getLines() + "','starttime':" + hostEntryInfo.getStarttime() + ", 'finished':'" + hostEntryInfo.getFinished() + "', 'endtime':" + hostEntryInfo.getEndtime() + ", 'runtime':" + hostEntryInfo.getRuntime() + "},");
                }
                writer.write("]");
                break;
            case "/query/cancel":
                if (tracker.cancelRunning(kv.getValue("uuid"))) {
                    if (jsonp) writer.write("{canceled:true,message:'");
                    writer.write("canceled " + kv.getValue("uuid"));
                } else {
                    if (jsonp) writer.write("{canceled:false,message:'");
                    writer.write("canceled failed for " + kv.getValue("uuid"));
                    response.setStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
                }
                if (jsonp) writer.write("'}");
                break;
            case "/query/encode": {
                Query q = new Query(null, kv.getValue("query", kv.getValue("path", "")), null);
                JSONArray path = CodecJSON.encodeJSON(q).getJSONArray("path");
                writer.write(path.toString());
                break;
            }
            case "/query/decode": {
                String qo = "{path:" + kv.getValue("query", kv.getValue("path", "")) + "}";
                Query q = CodecJSON.decodeString(new Query(), qo);
                writer.write(q.getPaths()[0]);
                break;
            }
            case "/v2/queries/finished.list": {
                JSONArray runningEntries = new JSONArray();
                for (QueryTracker.QueryEntryInfo entryInfo : tracker.getCompleted()) {
                    JSONObject entryJSON = CodecJSON.encodeJSON(entryInfo);
                    //TODO: replace this with some high level summary
                    entryJSON.put("hostInfoSet", "");
                    runningEntries.put(entryJSON);
                }
                writer.write(runningEntries.toString());
                break;
            }
            case "/v2/queries/running.list": {
                JSONArray runningEntries = new JSONArray();
                for (QueryTracker.QueryEntryInfo entryInfo : tracker.getRunning()) {
                    JSONObject entryJSON = CodecJSON.encodeJSON(entryInfo);
                    //TODO: replace this with some high level summary
                    entryJSON.put("hostInfoSet", "");
                    runningEntries.put(entryJSON);
                }
                writer.write(runningEntries.toString());
                break;
            }
            case "/v2/queries/list":
                JSONArray queries = new JSONArray();
                for (QueryTracker.QueryEntryInfo entryInfo : tracker.getCompleted()) {
                    JSONObject entryJSON = CodecJSON.encodeJSON(entryInfo);
                    entryJSON.put("hostEntries", entryInfo.hostInfoSet.size());
                    entryJSON.put("state", 0);
                    queries.put(entryJSON);
                }
                for (QueryTracker.QueryEntryInfo entryInfo : tracker.getQueued()) {
                    JSONObject entryJSON = CodecJSON.encodeJSON(entryInfo);
                    entryJSON.put("hostEntries", entryInfo.hostInfoSet.size());
                    entryJSON.put("state", 2);
                    queries.put(entryJSON);
                }
                for (QueryTracker.QueryEntryInfo entryInfo : tracker.getRunning()) {
                    JSONObject entryJSON = CodecJSON.encodeJSON(entryInfo);
                    entryJSON.put("hostEntries", entryInfo.hostInfoSet.size());
                    entryJSON.put("state", 3);
                    queries.put(entryJSON);
                }
                writer.write(queries.toString());
                break;
            case "/v2/job/list": {
                StringWriter swriter = new StringWriter();
                final JsonGenerator json = QueryServer.factory.createJsonGenerator(swriter);
                json.writeStartArray();
                for (IJob job : meshQueryMaster.getJobs()) {
                    if (job.getQueryConfig() != null && job.getQueryConfig().getCanQuery()) {
                        List<JobTask> tasks = job.getCopyOfTasks();
                        String uuid = job.getId();
                        json.writeStartObject();
                        json.writeStringField("id", uuid);
                        json.writeStringField("description", Optional.fromNullable(job.getDescription()).or(""));
                        json.writeNumberField("state", job.getState().ordinal());
                        json.writeStringField("creator", job.getCreator());
                        json.writeNumberField("submitTime", Optional.fromNullable(job.getSubmitTime()).or(-1L));
                        json.writeNumberField("startTime", Optional.fromNullable(job.getStartTime()).or(-1L));
                        json.writeNumberField("endTime", Optional.fromNullable(job.getStartTime()).or(-1L));
                        json.writeNumberField("replicas", Optional.fromNullable(job.getReplicas()).or(0));
                        json.writeNumberField("backups", Optional.fromNullable(job.getBackups()).or(0));
                        json.writeNumberField("nodes", tasks.size());
                        json.writeEndObject();
                    }
                }
                json.writeEndArray();
                json.close();
                writer.write(swriter.toString());
                break;
            }
            case "/v2/host/list": {
                StringWriter swriter = new StringWriter();
                final JsonGenerator json = QueryServer.factory.createJsonGenerator(swriter);
                json.writeStartArray();
                for (HostEntryInfo hostEntryInfo : tracker.getQueryHosts(kv.getValue("uuid"))) {
                    json.writeStartObject();
                    json.writeStringField("hostname", hostEntryInfo.getHostName());
                    json.writeNumberField("lines", hostEntryInfo.getLines());
                    json.writeNumberField("startTime", hostEntryInfo.getStarttime());
                    json.writeNumberField("endTime", hostEntryInfo.getEndtime());
                    json.writeNumberField("taskId", hostEntryInfo.getTaskId());
                    json.writeBooleanField("finished", hostEntryInfo.getFinished());
                    json.writeEndObject();
                }
                json.writeEndArray();
                json.close();
                writer.write(swriter.toString());
                break;
            }
            case "/v2/settings/git.properties": {
                StringWriter swriter = new StringWriter();
                final JsonGenerator json = QueryServer.factory.createJsonGenerator(swriter);
                Properties gitProperties = new Properties();
                json.writeStartObject();
                try {
                    InputStream in = queryServer.getClass().getResourceAsStream("/git.properties");
                    gitProperties.load(in);
                    in.close();
                    json.writeStringField("commitIdAbbrev", gitProperties.getProperty("git.commit.id.abbrev"));
                    json.writeStringField("commitUserEmail", gitProperties.getProperty("git.commit.user.email"));
                    json.writeStringField("commitMessageFull", gitProperties.getProperty("git.commit.message.full"));
                    json.writeStringField("commitId", gitProperties.getProperty("git.commit.id"));
                    json.writeStringField("commitUserName", gitProperties.getProperty("git.commit.user.name"));
                    json.writeStringField("buildUserName", gitProperties.getProperty("git.build.user.name"));
                    json.writeStringField("commitIdDescribe", gitProperties.getProperty("git.commit.id.describe"));
                    json.writeStringField("buildUserEmail", gitProperties.getProperty("git.build.user.email"));
                    json.writeStringField("branch", gitProperties.getProperty("git.branch"));
                    json.writeStringField("commitTime", gitProperties.getProperty("git.commit.time"));
                    json.writeStringField("buildTime", gitProperties.getProperty("git.build.time"));
                } catch (Exception ex) {
                    log.warn("Error loading git.properties, possibly jar was not compiled with maven.");
                }
                json.writeEndObject();
                json.close();
                writer.write(swriter.toString());
                break;
            }
            case "/v2/hosts/list":
                String hosts = meshQueryMaster.getMeshHostJSON();
                writer.write(hosts);
                break;
            default:
                // forward to static file server
                ctx.pipeline().addLast(staticFileHandler);
                request.retain();
                ctx.fireChannelRead(request);
                return; // don't do text response clean up
        }
        HttpUtils.endResponse(writer, cbf);
        log.trace("response being sent {}", writer);
        ByteBuf textResponse = ByteBufUtil.encodeString(ctx.alloc(),
                CharBuffer.wrap(writer.getBuilder()), CharsetUtil.UTF_8);
        HttpContent content = new DefaultHttpContent(textResponse);
        response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, textResponse.readableBytes());
        if (HttpHeaders.isKeepAlive(request)) {
            response.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        }
        ctx.write(response);
        ctx.write(content);
        ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        log.trace("response pending");
        if (!HttpHeaders.isKeepAlive(request)) {
            log.trace("Setting close listener");
            lastContentFuture.addListener(ChannelFutureListener.CLOSE);
        }
    }
}