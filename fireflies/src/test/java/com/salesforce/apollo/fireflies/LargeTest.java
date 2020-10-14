/*
 * Copyright (c) 2019, salesforce.com, inc.
 * All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 * For full license text, see the LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause
 */
package com.salesforce.apollo.fireflies;

import static com.salesforce.apollo.fireflies.PregenLargePopulation.getCa;
import static com.salesforce.apollo.fireflies.PregenLargePopulation.getMember;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.salesforce.apollo.comm.Communications;
import com.salesforce.apollo.comm.LocalCommSimm;
import com.salesforce.apollo.comm.ServerConnectionCache;
import com.salesforce.apollo.membership.CertWithKey;
import com.salesforce.apollo.membership.Member;
import com.salesforce.apollo.protocols.HashKey;
import com.salesforce.apollo.protocols.Utils;

import io.github.olivierlemasle.ca.RootCertificate;

/**
 * @author hal.hildebrand
 * @since 220
 */
public class LargeTest {

    private static final RootCertificate     ca         = getCa();
    private static Map<HashKey, CertWithKey> certs;
    private static final FirefliesParameters parameters = new FirefliesParameters(ca.getX509Certificate());

    @BeforeAll
    public static void beforeClass() {
        certs = IntStream.range(1, PregenLargePopulation.cardinality)
                         .parallel()
                         .mapToObj(i -> getMember(i))
                         .collect(Collectors.toMap(cert -> Member.getMemberId(cert.getCertificate()), cert -> cert));
    }

    private List<Node>            members;
    private List<View>            views;
    private List<Communications>  communications = new ArrayList<>();
    private List<X509Certificate> seeds;
    private MetricRegistry        registry;
    private MetricRegistry        node0Registry;

    @AfterEach
    public void after() {
        if (views != null) {
            views.forEach(v -> v.getService().stop());
            views.clear();
        }

        communications.forEach(e -> e.close());
        communications.clear();
    }

    // @Test
    public void swarm() throws Exception {
        initialize();

        long then = System.currentTimeMillis();
        views.forEach(view -> view.getService().start(Duration.ofMillis(10_000), seeds));

        assertTrue(Utils.waitForCondition(600_000, 10_000, () -> {
            return views.stream().filter(view -> view.getLive().size() != views.size()).count() == 0;
        }));

        System.out.println("View has stabilized in " + (System.currentTimeMillis() - then) + " Ms across all "
                + views.size() + " members");

        Thread.sleep(5_000);

        for (int i = 0; i < parameters.rings; i++) {
            for (View view : views) {
                Set<HashKey> difference = views.get(0).getRing(i).difference(view.getRing(i));
                assertEquals(0, difference.size(), "difference in ring sets: " + difference);
            }
        }

        List<View> invalid = views.stream()
                                  .map(view -> view.getLive().size() != views.size() ? view : null)
                                  .filter(view -> view != null)
                                  .collect(Collectors.toList());
        assertEquals(0, invalid.size());

        Graph<Participant> testGraph = new Graph<>();
        for (View v : views) {
            for (int i = 0; i < parameters.rings; i++) {
                testGraph.addEdge(v.getNode(), v.getRing(i).successor(v.getNode()));
            }
        }
        assertTrue(testGraph.isSC());

        for (View view : views) {
            for (int ring = 0; ring < view.getRings().size(); ring++) {
                final Collection<Participant> membership = view.getRing(ring).members();
                for (Node node : members) {
                    assertTrue(membership.contains(node));
                }
            }
        }

        views.forEach(view -> view.getService().stop());
        ConsoleReporter.forRegistry(node0Registry)
                       .convertRatesTo(TimeUnit.SECONDS)
                       .convertDurationsTo(TimeUnit.MILLISECONDS)
                       .build()
                       .report();
    }

    private void initialize() {
        Random entropy = new Random(0x666);
        registry = new MetricRegistry();
        node0Registry = new MetricRegistry();

        seeds = new ArrayList<>();
        members = certs.values()
                       .parallelStream()
                       .map(cert -> new CertWithKey(cert.getCertificate(), cert.getPrivateKey()))
                       .map(cert -> new Node(cert, parameters))
                       .collect(Collectors.toList());
        assertEquals(certs.size(), members.size());

        while (seeds.size() < parameters.toleranceLevel + 1) {
            CertWithKey cert = certs.get(members.get(entropy.nextInt(24)).getId());
            if (!seeds.contains(cert.getCertificate())) {
                seeds.add(cert.getCertificate());
            }
        }

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(100);
        AtomicBoolean frist = new AtomicBoolean(true);
        views = members.stream().map(node -> {
            FireflyMetricsImpl fireflyMetricsImpl = new FireflyMetricsImpl(
                    frist.getAndSet(false) ? node0Registry : registry);
            Communications comms = new LocalCommSimm(
                    ServerConnectionCache.newBuilder().setTarget(2).setMetrics(fireflyMetricsImpl), node.getId());
            communications.add(comms);
            return new View(node, comms, scheduler, fireflyMetricsImpl);
        }).collect(Collectors.toList());
    }
}