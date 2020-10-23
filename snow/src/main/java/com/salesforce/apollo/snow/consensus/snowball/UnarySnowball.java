/*
 * Copyright (c) 2020, salesforce.com, inc.
 * All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 * For full license text, see the LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause
 */
package com.salesforce.apollo.snow.consensus.snowball;

/**
 * @author hal.hildebrand
 *
 */

//UnarySnowball is a snowball instance deciding on one value. After performing
//a network sample of k nodes, if you have alpha votes for the choice, you
//should vote. Otherwise, you should reset.
public interface UnarySnowball extends Cloneable {

    void initialize(int beta);

    void recordSuccessfulPoll();

    void recordUnsucessfulPoll();

    boolean finalized();

    BinarySnowball extend(int beta, int originalPreference);

    UnarySnowball clone();

}