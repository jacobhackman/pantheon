/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.blockheaders;

import tech.pegasys.pantheon.ethereum.chain.BlockAddedEvent;
import tech.pegasys.pantheon.ethereum.chain.BlockAddedObserver;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.BlockResult;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.BlockResultFactory;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.SubscriptionManager;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.request.SubscriptionType;

import java.util.List;

public class NewBlockHeadersSubscriptionService implements BlockAddedObserver {

  private final SubscriptionManager subscriptionManager;
  private final BlockchainQueries blockchainQueries;
  private final BlockResultFactory blockResult = new BlockResultFactory();

  public NewBlockHeadersSubscriptionService(
      final SubscriptionManager subscriptionManager, final BlockchainQueries blockchainQueries) {
    this.subscriptionManager = subscriptionManager;
    this.blockchainQueries = blockchainQueries;
  }

  @Override
  public void onBlockAdded(final BlockAddedEvent event, final Blockchain blockchain) {
    final List<NewBlockHeadersSubscription> subscribers =
        subscriptionManager.subscriptionsOfType(
            SubscriptionType.NEW_BLOCK_HEADERS, NewBlockHeadersSubscription.class);

    final Hash newBlockHash = event.getBlock().getHash();

    for (final NewBlockHeadersSubscription subscription : subscribers) {
      final BlockResult newBlock =
          subscription.getIncludeTransactions()
              ? blockWithCompleteTransaction(newBlockHash)
              : blockWithTransactionHash(newBlockHash);

      subscriptionManager.sendMessage(subscription.getId(), newBlock);
    }
  }

  private BlockResult blockWithCompleteTransaction(final Hash hash) {
    return blockchainQueries.blockByHash(hash).map(blockResult::transactionComplete).orElse(null);
  }

  private BlockResult blockWithTransactionHash(final Hash hash) {
    return blockchainQueries
        .blockByHashWithTxHashes(hash)
        .map(blockResult::transactionHash)
        .orElse(null);
  }
}
