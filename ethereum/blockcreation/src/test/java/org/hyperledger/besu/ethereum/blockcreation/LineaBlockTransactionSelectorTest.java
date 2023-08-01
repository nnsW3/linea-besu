package org.hyperledger.besu.ethereum.blockcreation;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class LineaBlockTransactionSelectorTest extends LondonFeeMarketBlockTransactionSelectorTest {
  private static final int BLOCK_MAX_CALLDATA_SIZE = 1000;

  @Override
  protected FeeMarket getFeeMarket() {
    return FeeMarket.zeroBaseFee(0L);
  }

  @Override
  protected Wei getMinGasPrice() {
    return Wei.ZERO;
  }

  @Test
  public void blockCalldataBelowLimitOneTransaction() {
    final ProcessableBlockHeader blockHeader = createBlock(301_000, Wei.ZERO);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelector(
            transactionProcessor,
            blockHeader,
            Wei.ZERO,
            miningBeneficiary,
            Wei.ZERO,
            BLOCK_MAX_CALLDATA_SIZE);

    final Transaction tx = createCalldataTransaction(1, BLOCK_MAX_CALLDATA_SIZE / 10);
    ensureTransactionIsValid(tx);
    transactionPool.addRemoteTransactions(List.of(tx));

    final BlockTransactionSelector.TransactionSelectionResults results =
        selector.buildTransactionListForBlock();

    assertThat(results.getTransactions()).containsExactly(tx);
  }

  @Test
  public void blockCalldataBelowLimitMoreTransactions() {
    final ProcessableBlockHeader blockHeader = createBlock(301_000, Wei.ZERO);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelector(
            transactionProcessor,
            blockHeader,
            Wei.ZERO,
            miningBeneficiary,
            Wei.ZERO,
            BLOCK_MAX_CALLDATA_SIZE);

    final int numTxs = 5;

    final Transaction[] txs = new Transaction[5];

    for (int i = 0; i < numTxs; i++) {
      final Transaction tx = createCalldataTransaction(i, BLOCK_MAX_CALLDATA_SIZE / (numTxs * 2));
      txs[i] = tx;
      ensureTransactionIsValid(tx);
      transactionPool.addRemoteTransactions(List.of(tx));
    }

    final BlockTransactionSelector.TransactionSelectionResults results =
        selector.buildTransactionListForBlock();

    assertThat(results.getTransactions()).containsExactly(txs);
  }

  @Test
  public void blockCalldataEqualsLimitMoreTransactions() {
    final ProcessableBlockHeader blockHeader = createBlock(301_000, Wei.ZERO);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelector(
            transactionProcessor,
            blockHeader,
            Wei.ZERO,
            miningBeneficiary,
            Wei.ZERO,
            BLOCK_MAX_CALLDATA_SIZE);

    final int numTxs = 3;
    int currCalldataSize = 0;
    final Transaction[] txs = new Transaction[6];
    for (int i = 0; i < numTxs; i++) {
      final int txCalldataSize = BLOCK_MAX_CALLDATA_SIZE / (numTxs * 2);
      currCalldataSize += txCalldataSize;
      final Transaction tx = createCalldataTransaction(i, txCalldataSize);
      txs[i] = tx;
      ensureTransactionIsValid(tx);
      transactionPool.addRemoteTransactions(List.of(tx));
    }

    // last tx fill the remaining calldata space for the block
    final Transaction tx =
        createCalldataTransaction(numTxs + 1, BLOCK_MAX_CALLDATA_SIZE - currCalldataSize);
    txs[5] = tx;
    ensureTransactionIsValid(tx);
    transactionPool.addRemoteTransactions(List.of(tx));

    final BlockTransactionSelector.TransactionSelectionResults results =
        selector.buildTransactionListForBlock();

    assertThat(results.getTransactions()).containsExactly(txs);
  }

  @Test
  public void blockCalldataOverLimitOneTransaction() {
    final ProcessableBlockHeader blockHeader = createBlock(301_000, Wei.ZERO);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelector(
            transactionProcessor,
            blockHeader,
            Wei.ZERO,
            miningBeneficiary,
            Wei.ZERO,
            BLOCK_MAX_CALLDATA_SIZE);

    final Transaction tx = createCalldataTransaction(1, BLOCK_MAX_CALLDATA_SIZE + 1);
    ensureTransactionIsValid(tx);
    transactionPool.addRemoteTransactions(List.of(tx));

    final BlockTransactionSelector.TransactionSelectionResults results =
        selector.buildTransactionListForBlock();

    assertThat(results.getTransactions()).isEmpty();
  }

  @Test
  public void blockCalldataOverLimitAfterSomeTransactions() {
    final ProcessableBlockHeader blockHeader = createBlock(301_000, Wei.ZERO);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelector(
            transactionProcessor,
            blockHeader,
            Wei.ZERO,
            miningBeneficiary,
            Wei.ZERO,
            BLOCK_MAX_CALLDATA_SIZE);

    final int numTxs = 5;
    final Transaction[] txs = new Transaction[numTxs];
    for (int i = 0; i < numTxs; i++) {
      final int txCalldataSize = BLOCK_MAX_CALLDATA_SIZE / (numTxs - 1);
      final Transaction tx = createCalldataTransaction(i, txCalldataSize);
      txs[i] = tx;
      ensureTransactionIsValid(tx);
      transactionPool.addRemoteTransactions(List.of(tx));
    }

    final BlockTransactionSelector.TransactionSelectionResults results =
        selector.buildTransactionListForBlock();

    assertThat(results.getTransactions()).containsExactly(txs);
  }

  private Transaction createCalldataTransaction(
      final int transactionNumber, final int payloadSize) {
    return createCalldataTransaction(transactionNumber, Bytes.random(payloadSize));
  }

  private Transaction createCalldataTransaction(final int transactionNumber, final Bytes payload) {
    return Transaction.builder()
        .type(TransactionType.EIP1559)
        .gasLimit(100_000)
        .maxFeePerGas(Wei.ZERO)
        .maxPriorityFeePerGas(Wei.ZERO)
        .nonce(transactionNumber)
        .payload(payload)
        .to(Address.ID)
        .value(Wei.of(transactionNumber))
        .sender(Address.ID)
        .chainId(CHAIN_ID)
        .signAndBuild(keyPair);
  }
}
