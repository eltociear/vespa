// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A class to set up the default metrics for all services to be forwarded to Yamas
 *
 * @author <a href="mailto:trygve@yahoo-inc.com">Trygve Bolsø Berdal</a>
 */
public class DefaultMetricConsumers {

    /**
     * Populates a map of with consumer as key and metrics for that consumer as value. The metrics
     * are to be forwarded to consumers (ymon and yamas are the options at the moment).
     *
     * @return A map of default metric consumers and default metrics for that consumer.
     */
    public Map<String, MetricsConsumer> getDefaultMetricsConsumers() {
        Map<String, MetricsConsumer> metricsConsumers = new LinkedHashMap<>();
        metricsConsumers.put("yamas", getDefaultYamasConsumer());
        metricsConsumers.put("ymon", getDefaultYmonConsumer());
        return metricsConsumers;
    }

    private MetricsConsumer getDefaultYmonConsumer(){
        Map<String, Metric> metricMap = new LinkedHashMap<>();
        for (Metric metric : commonMetrics()) {
            metricMap.put(metric.getName(), metric);
        }

        return new MetricsConsumer("ymon", metricMap);
    }

    private MetricsConsumer getDefaultYamasConsumer(){
        // include common metrics
        List<Metric> metrics = commonMetrics();

        //Search node
        // jobs
        metrics.add(new Metric("content.proton.documentdb.job.total.average"));
        metrics.add(new Metric("content.proton.documentdb.job.attribute_flush.average"));
        metrics.add(new Metric("content.proton.documentdb.job.memory_index_flush.average"));
        metrics.add(new Metric("content.proton.documentdb.job.disk_index_fusion.average"));
        metrics.add(new Metric("content.proton.documentdb.job.document_store_flush.average"));
        metrics.add(new Metric("content.proton.documentdb.job.document_store_compact.average"));
        metrics.add(new Metric("content.proton.documentdb.job.bucket_move.average"));
        metrics.add(new Metric("content.proton.documentdb.job.lid_space_compact.average"));
        metrics.add(new Metric("content.proton.documentdb.job.removed_documents_prune.average"));

        // lid space
        metrics.add(new Metric("content.proton.documentdb.ready.lid_space.lid_bloat_factor.average"));
        metrics.add(new Metric("content.proton.documentdb.notready.lid_space.lid_bloat_factor.average"));
        metrics.add(new Metric("content.proton.documentdb.removed.lid_space.lid_bloat_factor.average"));
        metrics.add(new Metric("content.proton.documentdb.ready.lid_space.lid_fragmentation_factor.average"));
        metrics.add(new Metric("content.proton.documentdb.notready.lid_space.lid_fragmentation_factor.average"));
        metrics.add(new Metric("content.proton.documentdb.removed.lid_space.lid_fragmentation_factor.average"));

        // resource usage
        metrics.add(new Metric("content.proton.resource_usage.disk.average"));
        metrics.add(new Metric("content.proton.resource_usage.memory.average"));
        metrics.add(new Metric("content.proton.resource_usage.feeding_blocked.last"));
        metrics.add(new Metric("content.proton.documentdb.attribute.resource_usage.enum_store.average"));
        metrics.add(new Metric("content.proton.documentdb.attribute.resource_usage.multi_value.average"));
        metrics.add(new Metric("content.proton.documentdb.attribute.resource_usage.feeding_blocked.last"));

        // transaction log
        metrics.add(new Metric("content.proton.transactionlog.entries.average"));

        // document store
        metrics.add(new Metric("content.proton.documentdb.ready.document_store.disk_usage.average"));
        metrics.add(new Metric("content.proton.documentdb.ready.document_store.disk_bloat.average"));
        metrics.add(new Metric("content.proton.documentdb.ready.document_store.max_bucket_spread.average"));
        metrics.add(new Metric("content.proton.documentdb.notready.document_store.disk_usage.average"));
        metrics.add(new Metric("content.proton.documentdb.notready.document_store.disk_bloat.average"));
        metrics.add(new Metric("content.proton.documentdb.notready.document_store.max_bucket_spread.average"));
        metrics.add(new Metric("content.proton.documentdb.removed.document_store.disk_usage.average"));
        metrics.add(new Metric("content.proton.documentdb.removed.document_store.disk_bloat.average"));
        metrics.add(new Metric("content.proton.documentdb.removed.document_store.max_bucket_spread.average"));


        //Storage
        metrics.add(new Metric("vds.memfilepersistence.cache.files.average"));
        metrics.add(new Metric("vds.memfilepersistence.cache.body.average"));
        metrics.add(new Metric("vds.memfilepersistence.cache.header.average"));
        metrics.add(new Metric("vds.memfilepersistence.cache.meta.average"));
        metrics.add(new Metric("vds.visitor.allthreads.queuesize.count.average"));
        metrics.add(new Metric("vds.visitor.allthreads.completed.sum.average"));
        metrics.add(new Metric("vds.visitor.allthreads.created.sum.rate","visit"));

        metrics.add(new Metric("vds.filestor.alldisks.allthreads.put.sum.latency.average"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.remove.sum.latency.average"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.get.sum.latency.average"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.update.sum.latency.average"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.splitbuckets.count.rate"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.joinbuckets.count.rate"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.setbucketstates.count.rate"));

        metrics.add(new Metric("vds.filestor.spi.put.success.average"));
        metrics.add(new Metric("vds.filestor.spi.remove.success.average"));
        metrics.add(new Metric("vds.filestor.spi.update.success.average"));
        metrics.add(new Metric("vds.filestor.spi.get.success.average"));
        metrics.add(new Metric("vds.filestor.spi.iterate.success.average"));
        metrics.add(new Metric("vds.filestor.spi.put.success.rate"));
        metrics.add(new Metric("vds.filestor.spi.remove.success.rate"));
        metrics.add(new Metric("vds.filestor.spi.update.success.rate"));
        metrics.add(new Metric("vds.filestor.spi.get.success.rate"));
        metrics.add(new Metric("vds.filestor.spi.iterate.success.rate"));


        //Distributor
        metrics.add(new Metric("vds.visitor.sum.latency.average"));
        metrics.add(new Metric("vds.visitor.sum.failed.rate"));
        metrics.add(new Metric("vds.idealstate.buckets_rechecking.average"));
        metrics.add(new Metric("vds.idealstate.idealstate_diff.average"));
        metrics.add(new Metric("vds.idealstate.buckets_toofewcopies.average"));
        metrics.add(new Metric("vds.idealstate.buckets_toomanycopies.average"));
        metrics.add(new Metric("vds.idealstate.buckets.average"));
        metrics.add(new Metric("vds.idealstate.buckets_notrusted.average"));


        metrics.add(new Metric("vds.distributor.puts.sum.latency.average"));
        metrics.add(new Metric("vds.distributor.puts.sum.ok.rate"));
        metrics.add(new Metric("vds.distributor.puts.sum.failures.total.rate"));
        metrics.add(new Metric("vds.distributor.removes.sum.latency.average"));
        metrics.add(new Metric("vds.distributor.removes.sum.ok.rate"));
        metrics.add(new Metric("vds.distributor.removes.sum.failures.total.rate"));
        metrics.add(new Metric("vds.distributor.updates.sum.latency.average"));
        metrics.add(new Metric("vds.distributor.updates.sum.ok.rate"));
        metrics.add(new Metric("vds.distributor.updates.sum.failures.total.rate"));
        metrics.add(new Metric("vds.distributor.removelocations.sum.latency.average"));
        metrics.add(new Metric("vds.distributor.removelocations.sum.ok.rate"));
        metrics.add(new Metric("vds.distributor.removelocations.sum.failures.total.rate"));
        metrics.add(new Metric("vds.distributor.gets.sum.latency.average"));
        metrics.add(new Metric("vds.distributor.gets.sum.ok.rate"));
        metrics.add(new Metric("vds.distributor.gets.sum.failures.total.rate"));
        metrics.add(new Metric("vds.distributor.docsstored.average"));
        metrics.add(new Metric("vds.distributor.bytesstored.average"));
        metrics.add(new Metric("vds.visitor.sum.latency.average"));
        metrics.add(new Metric("vds.visitor.sum.failed.rate"));

        // Cluster Controller

        metrics.add(new Metric("cluster-controller.down.count.last"));
        metrics.add(new Metric("cluster-controller.initializing.count.last"));
        metrics.add(new Metric("cluster-controller.maintenance.count.last"));
        metrics.add(new Metric("cluster-controller.retired.count.last"));
        metrics.add(new Metric("cluster-controller.stopping.count.last"));
        metrics.add(new Metric("cluster-controller.up.count.last"));
        metrics.add(new Metric("cluster-controller.cluster-state-change.count", "content.cluster-controller.cluster-state-change.count"));

        metrics.add(new Metric("cluster-controller.is-master.last"));
        // TODO(hakonhall): Update this name once persistent "count" metrics has been implemented.
        // DO NOT RELY ON THIS METRIC YET.
        metrics.add(new Metric("cluster-controller.node-event.count"));

        //Errors from qrserver
        metrics.add(new Metric("error.timeout.rate","error.timeout"));
        metrics.add(new Metric("error.backends_oos.rate","error.backends_oos"));
        metrics.add(new Metric("error.plugin_failure.rate","error.plugin_failure"));
        metrics.add(new Metric("error.backend_communication_error.rate","error.backend_communication_error"));
        metrics.add(new Metric("error.empty_document_summaries.rate","error.empty_document_summaries"));
        metrics.add(new Metric("error.invalid_query_parameter.rate","error.invalid_query_parameter"));
        metrics.add(new Metric("error.internal_server_error.rate", "error.internal_server_error"));
        metrics.add(new Metric("error.misconfigured_server.rate","error.misconfigured_server"));
        metrics.add(new Metric("error.invalid_query_transformation.rate","error.invalid_query_transformation"));
        metrics.add(new Metric("error.result_with_errors.rate","error.result_with_errors"));
        metrics.add(new Metric("error.unspecified.rate","error.unspecified"));
        metrics.add(new Metric("error.unhandled_exception.rate","error.unhandled_exception"));
        metrics.add(new Metric("http.status.1xx.rate"));
        metrics.add(new Metric("http.status.2xx.rate"));
        metrics.add(new Metric("http.status.3xx.rate"));
        metrics.add(new Metric("http.status.4xx.rate"));
        metrics.add(new Metric("http.status.5xx.rate"));


        // container
        metrics.add(new Metric("serverRejectedRequests.rate"));
        metrics.add(new Metric("serverRejectedRequests.count"));

        metrics.add(new Metric("serverThreadPoolSize.average"));
        metrics.add(new Metric("serverThreadPoolSize.min"));
        metrics.add(new Metric("serverThreadPoolSize.max"));
        metrics.add(new Metric("serverThreadPoolSize.rate"));
        metrics.add(new Metric("serverThreadPoolSize.count"));
        metrics.add(new Metric("serverThreadPoolSize.last"));

        metrics.add(new Metric("serverActiveThreads.average"));
        metrics.add(new Metric("serverActiveThreads.min"));
        metrics.add(new Metric("serverActiveThreads.max"));
        metrics.add(new Metric("serverActiveThreads.rate"));
        metrics.add(new Metric("serverActiveThreads.count"));
        metrics.add(new Metric("serverActiveThreads.last"));

        metrics.add(new Metric("httpapi_latency.average"));
        metrics.add(new Metric("httpapi_pending.average"));
        metrics.add(new Metric("httpapi_num_operations.rate"));
        metrics.add(new Metric("httpapi_num_updates.rate"));
        metrics.add(new Metric("httpapi_num_removes.rate"));
        metrics.add(new Metric("httpapi_num_puts.rate"));
        metrics.add(new Metric("httpapi_succeeded.rate"));
        metrics.add(new Metric("httpapi_failed.rate"));


        // Config server
        metrics.add(new Metric("configserver.requests.count", "configserver.requests"));
        metrics.add(new Metric("configserver.failedRequests.count", "configserver.failedRequests"));
        metrics.add(new Metric("configserver.latency.average", "configserver.latency"));
        metrics.add(new Metric("configserver.cacheConfigElems.last", "configserver.cacheConfigElems"));
        metrics.add(new Metric("configserver.cacheChecksumElems.last", "configserver.cacheChecksumElems"));
        metrics.add(new Metric("configserver.hosts.last", "configserver.hosts"));
        metrics.add(new Metric("configserver.delayedResponses.count", "configserver.delayedResponses"));
        metrics.add(new Metric("configserver.sessionChangeErrors.count", "configserver.sessionChangeErrors"));


        Map<String, Metric> metricMap = new LinkedHashMap<>();
        for (Metric metric : metrics) {
            metricMap.put(metric.getName(), metric);
        }

        return new MetricsConsumer("yamas", metricMap);
    }

    // Common metrics for ymon and yamas. For ymon metric names needs to be less then 19 characters long
    private List<Metric> commonMetrics(){
        List<Metric> metrics = new ArrayList<>();

        //Searchnode
        metrics.add(new Metric("proton.numstoreddocs.last", "documents_total"));
        metrics.add(new Metric("proton.numindexeddocs.last", "documents_ready"));
        metrics.add(new Metric("proton.numactivedocs.last", "documents_active"));
        metrics.add(new Metric("proton.numremoveddocs.last", "documents_removed"));

        metrics.add(new Metric("proton.docsinmemory.last", "documents_inmemory"));
        metrics.add(new Metric("proton.diskusage.last", "diskusage"));
        metrics.add(new Metric("proton.memoryusage.max", "content.proton.memoryusage.max"));
        metrics.add(new Metric("proton.transport.query.count.rate", "query_requests"));
        metrics.add(new Metric("proton.transport.docsum.docs.rate", "document_requests"));
        metrics.add(new Metric("proton.transport.docsum.latency.average", "content.proton.transport.docsum.latency.average"));
        metrics.add(new Metric("proton.transport.query.latency.average", "query_latency"));

        //Docproc - per chain
        metrics.add(new Metric("documents_processed.rate", "documents_processed"));

        //Qrserver
        metrics.add(new Metric("peak_qps.average", "peak_qps"));
        metrics.add(new Metric("search_connections.average", "search_connections"));
        metrics.add(new Metric("active_queries.average", "active_queries"));
        metrics.add(new Metric("queries.rate", "queries"));
        metrics.add(new Metric("query_latency.average", "mean_query_latency"));
        metrics.add(new Metric("query_latency.max", "max_query_latency"));
        metrics.add(new Metric("query_latency.95percentile", "95p_query_latency"));
        metrics.add(new Metric("query_latency.99percentile", "99p_query_latency"));
        metrics.add(new Metric("failed_queries.rate", "failed_queries"));
        metrics.add(new Metric("hits_per_query.average", "hits_per_query"));
        metrics.add(new Metric("empty_results.rate", "empty_results"));
        metrics.add(new Metric("requestsOverQuota.rate"));
        metrics.add(new Metric("requestsOverQuota.count"));

        //Storage
        metrics.add(new Metric("vds.datastored.alldisks.docs.average","docs"));
        metrics.add(new Metric("vds.datastored.alldisks.bytes.average","bytes"));
        metrics.add(new Metric("vds.visitor.allthreads.averagevisitorlifetime.sum.average","visitorlifetime"));
        metrics.add(new Metric("vds.visitor.allthreads.averagequeuewait.sum.average","visitorqueuewait"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.put.sum.count.rate","put"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.remove.sum.count.rate","remove"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.get.sum.count.rate","get"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.update.sum.count.rate","update"));
        metrics.add(new Metric("vds.filestor.alldisks.queuesize.average","diskqueuesize"));
        metrics.add(new Metric("vds.filestor.alldisks.averagequeuewait.sum.average","diskqueuewait"));


        //Distributor
        metrics.add(new Metric("vds.idealstate.delete_bucket.done_ok.rate","deleteok"));
        metrics.add(new Metric("vds.idealstate.delete_bucket.done_failed.rate","deletefailed"));
        metrics.add(new Metric("vds.idealstate.delete_bucket.pending.average","deletepending"));
        metrics.add(new Metric("vds.idealstate.merge_bucket.done_ok.rate","mergeok"));
        metrics.add(new Metric("vds.idealstate.merge_bucket.done_failed.rate","mergefailed"));
        metrics.add(new Metric("vds.idealstate.merge_bucket.pending.average","mergepending"));
        metrics.add(new Metric("vds.idealstate.split_bucket.done_ok.rate","splitok"));
        metrics.add(new Metric("vds.idealstate.split_bucket.done_failed.rate","splitfailed"));
        metrics.add(new Metric("vds.idealstate.split_bucket.pending.average","splitpending"));
        metrics.add(new Metric("vds.idealstate.join_bucket.done_ok.rate","joinok"));
        metrics.add(new Metric("vds.idealstate.join_bucket.done_failed.rate","joinfailed"));
        metrics.add(new Metric("vds.idealstate.join_bucket.pending.average","joinpending"));

        return metrics;
    }

}
