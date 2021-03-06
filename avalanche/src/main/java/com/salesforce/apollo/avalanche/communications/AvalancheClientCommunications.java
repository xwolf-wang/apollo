/*
 * Copyright (c) 2019, salesforce.com, inc.
 * All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 * For full license text, see the LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause
 */
package com.salesforce.apollo.avalanche.communications;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

import org.apache.commons.math3.util.Pair;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;
import com.salesfoce.apollo.proto.AvalancheGrpc;
import com.salesfoce.apollo.proto.DagNodes;
import com.salesfoce.apollo.proto.Query;
import com.salesfoce.apollo.proto.Query.Builder;
import com.salesfoce.apollo.proto.QueryResult;
import com.salesfoce.apollo.proto.SuppliedDagNodes;
import com.salesforce.apollo.avalanche.AvalancheMetrics;
import com.salesforce.apollo.comm.ServerConnectionCache.CreateClientCommunications;
import com.salesforce.apollo.comm.ServerConnectionCache.ManagedServerConnection;
import com.salesforce.apollo.fireflies.Participant;
import com.salesforce.apollo.membership.Member;
import com.salesforce.apollo.protocols.Avalanche;
import com.salesforce.apollo.protocols.HashKey;

/**
 * @author hal.hildebrand
 * @since 220
 */
public class AvalancheClientCommunications implements Avalanche {

    public static CreateClientCommunications<AvalancheClientCommunications> getCreate(AvalancheMetrics metrics) {
        return (t, f, c) -> new AvalancheClientCommunications(c, t, metrics);

    }

    private final ManagedServerConnection           channel;
    private final AvalancheGrpc.AvalancheFutureStub client;
    private final Member                            member;
    private final AvalancheMetrics                  metrics;

    public AvalancheClientCommunications(ManagedServerConnection conn, Member member, AvalancheMetrics metrics) {
        this.channel = conn;
        this.member = member;
        this.client = AvalancheGrpc.newFutureStub(conn.channel).withCompression("gzip");
        this.metrics = metrics;
    }

    public Participant getMember() {
        return (Participant) member;
    }

    @Override
    public ListenableFuture<QueryResult> query(HashKey context, List<Pair<HashKey, ByteString>> transactions,
                                               Collection<HashKey> wanted) {
        Builder builder = Query.newBuilder().setContext(context.toID());
        transactions.forEach(t -> {
            builder.addHashes(t.getFirst().toID());
            builder.addTransactions(t.getSecond());
        });
        wanted.forEach(e -> builder.addWanted(e.toID()));
        try {
            Query query = builder.build();
            ListenableFuture<QueryResult> result = client.query(query);

            if (metrics != null) {
                result.addListener(() -> {
                    metrics.outboundBandwidth().mark(query.getSerializedSize());
                    QueryResult queryResult;
                    try {
                        queryResult = result.get();
                        metrics.inboundBandwidth().mark(queryResult.getSerializedSize());
                        metrics.outboundQuery().update(query.getSerializedSize());
                        metrics.queryResponse().update(queryResult.getSerializedSize());
                    } catch (InterruptedException | ExecutionException e1) {
                        // ignored for metrics gathering
                    }
                }, ForkJoinPool.commonPool());

            }
            return result;
        } catch (Throwable e) {
            throw new IllegalStateException("Unexpected exception in communication", e);
        }
    }

    public void release() {
        channel.release();
    }

    @Override
    public ListenableFuture<SuppliedDagNodes> requestDAG(HashKey context, Collection<HashKey> want) {
        com.salesfoce.apollo.proto.DagNodes.Builder builder = DagNodes.newBuilder().setContext(context.toID());
        want.forEach(e -> builder.addEntries(e.toID()));
        try {
            DagNodes request = builder.build();
            ListenableFuture<SuppliedDagNodes> requested = client.requestDag(request);
            if (metrics != null) {
                requested.addListener(() -> {
                    metrics.outboundBandwidth().mark(request.getSerializedSize());
                    SuppliedDagNodes suppliedDagNodes;
                    try {
                        suppliedDagNodes = requested.get();
                        metrics.inboundBandwidth().mark(suppliedDagNodes.getSerializedSize());
                        metrics.outboundRequestDag().update(request.getSerializedSize());
                        metrics.requestDagResponse().update(suppliedDagNodes.getSerializedSize());
                    } catch (InterruptedException | ExecutionException e1) {
                        // ignored for metrics gathering
                    }
                }, ForkJoinPool.commonPool());
            }
            return requested;
        } catch (Throwable e) {
            throw new IllegalStateException("Unexpected exception in communication", e);
        }
    }

    @Override
    public String toString() {
        return String.format("->[%s]", member);
    }
}
