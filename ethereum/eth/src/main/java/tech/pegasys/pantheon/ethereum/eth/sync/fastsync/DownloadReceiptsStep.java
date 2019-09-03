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
package tech.pegasys.pantheon.ethereum.eth.sync.fastsync;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.GetReceiptsForHeadersTask;
import tech.pegasys.pantheon.plugin.services.MetricsSystem;
import tech.pegasys.pantheon.util.FutureUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class DownloadReceiptsStep
    implements Function<List<Block>, CompletableFuture<List<BlockWithReceipts>>> {
  private final EthContext ethContext;
  private final MetricsSystem metricsSystem;

  public DownloadReceiptsStep(final EthContext ethContext, final MetricsSystem metricsSystem) {
    this.ethContext = ethContext;
    this.metricsSystem = metricsSystem;
  }

  @Override
  public CompletableFuture<List<BlockWithReceipts>> apply(final List<Block> blocks) {
    final List<BlockHeader> headers = blocks.stream().map(Block::getHeader).collect(toList());
    final CompletableFuture<Map<BlockHeader, List<TransactionReceipt>>> getReceipts =
        GetReceiptsForHeadersTask.forHeaders(ethContext, headers, metricsSystem).run();
    final CompletableFuture<List<BlockWithReceipts>> combineWithBlocks =
        getReceipts.thenApply(
            receiptsByHeader -> combineBlocksAndReceipts(blocks, receiptsByHeader));
    FutureUtils.propagateCancellation(combineWithBlocks, getReceipts);
    return combineWithBlocks;
  }

  private List<BlockWithReceipts> combineBlocksAndReceipts(
      final List<Block> blocks, final Map<BlockHeader, List<TransactionReceipt>> receiptsByHeader) {
    return blocks.stream()
        .map(
            block -> {
              final List<TransactionReceipt> receipts =
                  receiptsByHeader.getOrDefault(block.getHeader(), emptyList());
              return new BlockWithReceipts(block, receipts);
            })
        .collect(toList());
  }
}
