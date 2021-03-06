/*
 * Copyright (c) 2020, salesforce.com, inc.
 * All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 * For full license text, see the LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause
 */
package com.salesforce.apollo.comm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesfoce.apollo.proto.ID;
import com.salesforce.apollo.protocols.HashKey;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

/**
 * @author hal.hildebrand
 *
 */
public class RoutableService<Service> {
    private static final Logger log = LoggerFactory.getLogger(RoutableService.class);

    private final Map<HashKey, Service> services = new ConcurrentHashMap<>();

    public void bind(HashKey context, Service service) {
        services.put(context, service);
    }

    public void unbind(HashKey context) {
        services.remove(context);
    }

    public void evaluate(StreamObserver<?> responseObserver, HashKey context, Consumer<Service> c) {
        if (context == null) {
            responseObserver.onError(new StatusRuntimeException(Status.NOT_FOUND));
            try {
                log.trace("Null context");
                responseObserver.onCompleted();
            } catch (Throwable e) {
                log.trace("Error returning error", e);
            }
            return;
        }
        Service service = services.get(context);
        if (service == null) {
            log.trace("No service  {} for context {}", context);
            responseObserver.onError(new StatusRuntimeException(Status.NOT_FOUND));
            try {
                responseObserver.onCompleted();
            } catch (Throwable e) {
                log.trace("Error returning error", e);
            }
        } else {
            c.accept(service);
        }
    }

    public void evaluate(StreamObserver<?> responseObserver, ID id, Consumer<Service> c) {
        evaluate(responseObserver, id.getItselfCount() == 0 ? null : new HashKey(id), c);
    }
}
