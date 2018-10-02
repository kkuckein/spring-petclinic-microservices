package org.springframework.samples.petclinic.opencensus;


import io.opencensus.common.Scope;
import io.opencensus.exporter.stats.prometheus.PrometheusStatsCollector;
import io.opencensus.implcore.tags.TagContextImpl;
import io.opencensus.stats.*;
import io.opencensus.tags.*;
import io.opencensus.trace.Span;
import io.opencensus.trace.SpanContext;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.propagation.TextFormat;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.HTTPServer;
import org.springframework.http.HttpRequest;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class OpenCensusService {

    /**
     * OpenCensus Stuff
     * *************************************************
     */
    // The latency in milliseconds
    private static final Measure.MeasureDouble M_LATENCY_MS = Measure.MeasureDouble.create("service_response_time", "The latency in milliseconds", "ms");

    private final TagContextTextSerializer tagContextSerializer = new TagContextTextSerializer();

    private final Tagger tagger = Tags.getTagger();
    private final Tracer tracer = Tracing.getTracer();

    private final TextFormat textFormat = Tracing.getPropagationComponent().getB3Format();
    public final TextFormat.Setter<HttpRequest> httpRequestSetter = new TextFormat.Setter<HttpRequest>() {
        public void put(HttpRequest carrier, String key, String value) {
            carrier.getHeaders().add(key, value);
        }
    };

    public final TextFormat.Getter<HttpServletRequest> httpRequestGetter = new TextFormat.Getter<HttpServletRequest>() {
        @Override
        public String get(HttpServletRequest carrier, String key) {
            return carrier.getHeader(key);
        }
    };

    private final StatsRecorder statsRecorder = Stats.getStatsRecorder();
    // The tags
    public static final TagKey KEY_SERVICE = TagKey.create("service");
    public static final TagKey KEY_APPLICATION = TagKey.create("application");
    public static final TagKey KEY_NODE = TagKey.create("node");
    public static final TagKey KEY_SERVICE_ORIG = TagKey.create("orig_service");
    public static final TagKey KEY_APPLICATION_ORIG = TagKey.create("orig_application");
    public static final TagKey KEY_NODE_ORIG = TagKey.create("orig_node");
    public static final TagKey KEY_BT = TagKey.create("bt");

    private Random rand = new Random(System.nanoTime());

    private String application = "_";
    private String service = "_";
    private String node = "_";

    private OpenCensusService() {
        try {
            setupOpenCensus();
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void registerAllViews() {
        // Defining the distribution aggregations
        Aggregation latencyDistribution = Aggregation.Distribution.create(BucketBoundaries.create(
            Arrays.asList(
                // [>=0ms, >=25ms, >=50ms, >=75ms, >=100ms, >=200ms, >=400ms, >=600ms, >=800ms, >=1s, >=2s, >=4s, >=6s]
                0.0, 25.0, 50.0, 75.0, 100.0, 200.0, 400.0, 600.0, 800.0, 1000.0, 2000.0, 4000.0, 6000.0, 10000.0)
        ));

        // So tagKeys
        List<TagKey> keys = new ArrayList<>();
        keys.add(KEY_APPLICATION);
        keys.add(KEY_SERVICE);
        keys.add(KEY_NODE);
        keys.add(KEY_APPLICATION_ORIG);
        keys.add(KEY_SERVICE_ORIG);
        keys.add(KEY_NODE_ORIG);
        keys.add(KEY_BT);

        // Create the view manager
        Stats.getViewManager().registerView(View.create(View.Name.create("PetClinic/response_time"),
            "The distribution of latencies", M_LATENCY_MS, latencyDistribution, keys));
    }

    private void setupOpenCensus() throws IOException {
        registerAllViews();
        PrometheusStatsCollector.createAndRegister();
        String strPort = System.getProperty("promPort");
        int port = strPort== null? 9091:Integer.parseInt(strPort);

        HTTPServer server =
            new HTTPServer(port,true);
    }

    public void registerContext(String application, String service, String node){
        this.application = application;
        this.service = service;
        this.node = node;
    }

    public Tagger getTagger() {
        return tagger;
    }

    public Tracer getTracer() {
        return tracer;
    }

    public void injectHttpHeaders(HttpRequest request){
        textFormat.inject(tracer.getCurrentSpan().getContext(), request, httpRequestSetter);
        tagContextSerializer.inject(tagger.getCurrentTagContext(),request);
    }

    public Scope createSpanFromIncomingRequest(HttpServletRequest request){
        try {
            SpanContext spanContext = textFormat.extract(request, httpRequestGetter);
            Span span = tracer.spanBuilderWithRemoteParent(request.getMethod(), spanContext).startSpan();
            return tracer.withSpan(span);
        } catch(Exception e){

        }

        return null;
    }

    public Scope createTagContextFromIncomingRequest(HttpServletRequest request){
        TagContextImpl remoteTagContext = (TagContextImpl)tagContextSerializer.deserialize(request, tagger);
        TagContextBuilder tagCtxBuilder = OpenCensusService.getInstance().getTagger().emptyBuilder();
        tagCtxBuilder.put(KEY_APPLICATION, TagValue.create(this.application))
            .put(KEY_SERVICE, TagValue.create(this.service))
            .put(KEY_NODE, TagValue.create(this.node));
        if(remoteTagContext != null){
            TagValue origApplication = remoteTagContext.getTags().getOrDefault(KEY_APPLICATION, TagValue.create("_"));
            TagValue origService = remoteTagContext.getTags().getOrDefault(KEY_SERVICE, TagValue.create("_"));
            TagValue origNode = remoteTagContext.getTags().getOrDefault(KEY_NODE, TagValue.create("_"));
            TagValue bt = remoteTagContext.getTags().getOrDefault(KEY_BT, TagValue.create("Unknown"));
            tagCtxBuilder.put(KEY_APPLICATION_ORIG, origApplication)
                .put(KEY_SERVICE_ORIG, origService)
                .put(KEY_NODE_ORIG, origNode);

            if(bt != null){
                tagCtxBuilder.put(KEY_BT, bt);
            }
        }

        return tagCtxBuilder.buildScoped();
    }

    public void writeMetric(double durationMs){
        statsRecorder.newMeasureMap().put(M_LATENCY_MS, durationMs).record();
    }

    private static OpenCensusService instance = null;

    public static OpenCensusService getInstance() {
        if(instance == null){
            instance = new OpenCensusService();
        }

        return instance;
    }




}
