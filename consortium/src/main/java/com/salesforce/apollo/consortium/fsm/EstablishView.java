/*
 * Copyright (c) 2020, salesforce.com, inc.
 * All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 * For full license text, see the LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause
 */
package com.salesforce.apollo.consortium.fsm;

import java.util.List;

import com.chiralbehaviors.tron.Entry;
import com.chiralbehaviors.tron.Exit;
import com.salesfoce.apollo.consortium.proto.Block;
import com.salesfoce.apollo.consortium.proto.ReplicateTransactions;
import com.salesfoce.apollo.consortium.proto.Stop;
import com.salesfoce.apollo.consortium.proto.StopData;
import com.salesfoce.apollo.consortium.proto.Sync;
import com.salesfoce.apollo.consortium.proto.TotalOrdering;
import com.salesfoce.apollo.consortium.proto.Transaction;
import com.salesfoce.apollo.consortium.proto.Validate;
import com.salesforce.apollo.consortium.Consortium.CollaboratorContext;
import com.salesforce.apollo.consortium.Consortium.Timers;
import com.salesforce.apollo.consortium.CurrentBlock;
import com.salesforce.apollo.consortium.EnqueuedTransaction;
import com.salesforce.apollo.membership.Member;

/**
 * @author hal.hildebrand
 *
 */
public enum EstablishView implements Transitions {

    BUILD {
        @Exit
        public void cancel() {
            context().cancel(Timers.AWAIT_VIEW_MEMBERS);
        }

        @Override
        public Transitions deliverStop(Stop stop, Member from) {
            context().deliverStop(stop, from);
            return null;
        }

        @Override
        public Transitions deliverStopData(StopData stopData, Member from) {
            context().deliverStopData(stopData, from);
            return null;
        }

        @Override
        public Transitions deliverTransaction(Transaction txn, Member from) {
            context().receiveJoin(txn);
            return null;
        }

        @Override
        public Transitions deliverTransactions(ReplicateTransactions txns, Member from) {
            context().receiveJoins(txns, from);
            return null;
        }

        @Override
        public Transitions formView() {
            return ESTABLISHED;
        }

        @Entry
        public void petitionToJoin() {
            context().joinView();
        }

        @Override
        public Transitions receive(Transaction transacton, Member from) {
            context().receive(transacton);
            return null;
        }
    },
    ESTABLISHED {

        @Override
        public Transitions becomeFollower() {
            return FOLLOWER;
        }

        @Override
        public Transitions becomeLeader() {
            return LEADER;
        }

        @Override
        public Transitions deliverStop(Stop stop, Member from) {
            context().deliverStop(stop, from);
            return null;
        }

        @Override
        public Transitions deliverStopData(StopData stopData, Member from) {
            context().deliverStopData(stopData, from);
            return null;
        }

        @Override
        public Transitions deliverSync(Sync syncData, Member from) {
            context().deliverSync(syncData, from);
            return null;
        }

        @Override
        public Transitions deliverTransaction(Transaction txn, Member from) {
            context().receive(txn);
            return null;
        }

        @Override
        public Transitions deliverTransactions(ReplicateTransactions txns, Member from) {
            context().receive(txns, from);
            return null;
        }

        @Override
        public Transitions deliverValidate(Validate validation) {
            context().validate(validation);
            return null;
        }

        @Override
        public Transitions receive(Transaction transaction, Member from) {
            context().receive(transaction);
            return null;
        }

        @Override
        public Transitions startRegencyChange(List<EnqueuedTransaction> transactions) {
            CollaboratorContext context = context();
            fsm().push(ChangeRegency.INITIAL, () -> context.changeRegency(transactions));
            return null;
        }
    },
    FOLLOWER {

        @Override
        public Transitions deliverBlock(Block block, Member from) {
            context().deliverBlock(block, from);
            return null;
        }

        @Override
        public Transitions deliverStop(Stop stop, Member from) {
            return null; // TODO
        }

        @Override
        public Transitions deliverStopData(StopData stopData, Member from) {
            return null; // TODO
        }

        @Override
        public Transitions deliverSync(Sync syncData, Member from) {
            return null; // TODO
        }

        @Override
        public Transitions deliverTotalOrdering(TotalOrdering msg, Member from) {
            context().deliverTotalOrdering(msg, from);
            return null;
        }

        @Override
        public Transitions deliverTransaction(Transaction transaction, Member from) {
            context().receive(transaction);
            return null;
        }

        @Override
        public Transitions deliverTransactions(ReplicateTransactions txns, Member from) {
            context().receive(txns, from);
            return null;
        }

        @Override
        public Transitions deliverValidate(Validate validation) {
            context().validate(validation);
            return null;
        }

        @Override
        public Transitions genesisAccepted() {
            return null;
        }

        @Override
        public Transitions join() {
            return null;
        }

        @Override
        public Transitions processGenesis(CurrentBlock next) {
            context().processGenesis(next);
            return null;
        }

        @Override
        public Transitions receive(Transaction transacton, Member from) {
            context().receive(transacton);
            return null;
        }
    },
    LEADER {

        @Override
        public Transitions deliverBlock(Block block, Member from) {
            context().deliverBlock(block, from);
            return null;
        }

        @Override
        public Transitions deliverStop(Stop stop, Member from) {
            return null; // TODO
        }

        @Override
        public Transitions deliverStopData(StopData stopData, Member from) {
            return null; // TODO
        }

        @Override
        public Transitions deliverSync(Sync syncData, Member from) {
            return null; // TODO
        }

        @Override
        public Transitions deliverTotalOrdering(TotalOrdering msg, Member from) {
            context().deliverTotalOrdering(msg, from);
            return null;
        }

        @Override
        public Transitions deliverTransaction(Transaction transaction, Member from) {
            context().receive(transaction);
            return null;
        }

        @Override
        public Transitions deliverTransactions(ReplicateTransactions txns, Member from) {
            context().receive(txns, from);
            return null;
        }

        @Override
        public Transitions deliverValidate(Validate validation) {
            context().validate(validation);
            context().totalOrderDeliver();
            return null;
        }

        @Entry
        public void generateView() {
            context().generateView();
        }

        @Override
        public Transitions genesisAccepted() {
            return null;
        }

        @Override
        public Transitions join() {
            return null;
        }

        @Override
        public Transitions processGenesis(CurrentBlock next) {
            context().processGenesis(next);
            return null;
        }

        @Override
        public Transitions receive(Transaction transacton, Member from) {
            context().receive(transacton);
            return null;
        }
    };
}