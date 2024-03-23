import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.ArrayList;

public class MaxFeeTxHandler {

    private UTXOPool utxoPool;
    
    // Store calculated transaction fees. (Dynamic programming)
    // So that we can retrieve them when needed instead of recalculating them.
    private HashMap<Integer, Double> txFeeCache = new HashMap<>();


    /*
     * Creates a public ledger whose current UTXOPool (collection of unspent
     * transaction outputs) is utxoPool. This should make a defensive copy of
     * utxoPool by using the UTXOPool(UTXOPool uPool) constructor.
     */
    public MaxFeeTxHandler(UTXOPool utxoPool) {
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

        // Loop thru the outputs to calculate the sum of output values.
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
        // Create a TreeSet to store transactions sorted by fees
        Set<Transaction> txsSortedByFees = new TreeSet<>((tx1, tx2) -> {
            return Double.valueOf(txFee(tx2)).compareTo(txFee(tx1));
        });

        // Add all possible transactions to txsSortedByFees
        Collections.addAll(txsSortedByFees, possibleTxs);

        // Store accepted transactions
        ArrayList<Transaction> acceptedTransactions = new ArrayList<Transaction>();

        for (Transaction tx : txsSortedByFees) {
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

    private double txFee(Transaction tx) {
        // Check if the solution is already cached.
        if (txFeeCache.containsKey(tx.hashCode())) {
            return txFeeCache.get(tx.hashCode());
        }

        // Calculate the fee and store the result in the cache.
        double sumInputs = 0.0;
        double sumOutputs = 0.0;

        for (Transaction.Input input : tx.getInputs()) {
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);

            if (isValidTx(tx)) {
                Transaction.Output txOutput = utxoPool.getTxOutput(utxo);
                sumInputs += txOutput.value;
            }
        }

        for (Transaction.Output output : tx.getOutputs()) {
            sumOutputs += output.value;
        }

        double fee = sumInputs - sumOutputs;

        // Store the result in the cache.
        txFeeCache.put(tx.hashCode(), fee);

        return fee;
    }
}
