/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.eth.sync.fullsync;

import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.Transaction;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

public class ExtractTxSignaturesTask implements Function<List<Block>, Stream<Block>> {

  @Override
  public Stream<Block> apply(final List<Block> blocks) {
    return blocks.stream().map(this::extractSignatures);
  }

  private Block extractSignatures(final Block block) {
    block.getBody().getTransactions().forEach(Transaction::getSender);
    return block;
  }
}
