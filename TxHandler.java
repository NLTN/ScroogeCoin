import java.util.HashSet;
import java.util.Arrays;
import java.util.ArrayList;

public class TxHandler {

	private UTXOPool utxoPool;

	/*
	 * Creates a public ledger whose current UTXOPool (collection of unspent
	 * transaction outputs) is utxoPool. This should make a defensive copy of
	 * utxoPool by using the UTXOPool(UTXOPool uPool) constructor.
	 */
	public TxHandler(UTXOPool utxoPool) {
		this.utxoPool = new UTXOPool(utxoPool);
	}

	/*
	 * Returns true if
	 * (1) all outputs claimed by tx are in the current UTXO pool,
	 * (2) the signatures on each input of tx are valid,
	 * (3) no UTXO is claimed multiple times by tx,
	 * (4) all of tx’s output values are non-negative, and
	 * (5) the sum of tx’s input values is greater than or equal to the sum of
	 * its output values;
	 * and false otherwise.
	 */

	public boolean isValidTx(Transaction tx) {
		HashSet<UTXO> claimedUTXOs = new HashSet<UTXO>();
		double sumInputs = 0.0;
		double sumOutputs = 0.0;

		int numInputs = tx.getInputs().size();
		boolean error = false;
		int index = 0;

		while (index < numInputs && !error) {
			Transaction.Input input = tx.getInput(index);
			UTXO ut = new UTXO(input.prevTxHash, input.outputIndex);

			// (1) all outputs claimed by tx are in the current UTXO pool,
			// (2) the signatures on each input of tx are valid,
			// (3) no UTXO is claimed multiple times by tx,
			if (!this.utxoPool.contains(ut) ||
					!utxoPool.getTxOutput(ut).address.verifySignature(tx.getRawDataToSign(index), input.signature) ||
					claimedUTXOs.contains(ut)) {
				error = true;
			} else {
				// Sum of Input Values = Sum of Previous Output Values
				sumInputs += utxoPool.getTxOutput(ut).value;
				claimedUTXOs.add(ut);
			}

			++index;
		}

		if (error) {
			return false;
		}

		// Loop thru the outpust to calculate the sum of output values.
		// And break the loop if there is a negative value.
		index = 0;
		int numOutputs = tx.getOutputs().size();
		double outputValue;
		while (index < numOutputs && (outputValue = tx.getOutput(index).value) >= 0.0) {
			sumOutputs += outputValue;
			++index;
		}

		// (4) all of tx’s output values are non-negative
		// (5) the sum of tx’s input values is greater than or equal to the sum of
		// * its output values;
		return (index == numOutputs) && (sumInputs > sumOutputs);
	}

	/*
	 * Handles each epoch by receiving an unordered array of proposed
	 * transactions, checking each transaction for correctness,
	 * returning a mutually valid array of accepted transactions,
	 * and updating the current UTXO pool as appropriate.
	 */
	public Transaction[] handleTxs(Transaction[] possibleTxs) {
		// Store accepted transactions
		ArrayList<Transaction> acceptedTransactions = new ArrayList<Transaction>();

		for (Transaction tx : possibleTxs) {
			if (isValidTx(tx)) {
				acceptedTransactions.add(tx);

				// Update pool
				for (Transaction.Input input : tx.getInputs()) {
					UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
					this.utxoPool.removeUTXO(utxo);
				}

				byte[] txHash = tx.getHash();

				int numOutputs = tx.getOutputs().size();

				for (int i = 0; i < numOutputs; ++i) {
					UTXO utxo = new UTXO(txHash, i);
					this.utxoPool.addUTXO(utxo, tx.getOutput(i));
				}
			}
		}

		return acceptedTransactions.toArray(new Transaction[acceptedTransactions.size()]);
	}
}
