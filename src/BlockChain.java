import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.HashMap;
import java.util.Vector;

// Block Chain should maintain only limited block nodes to satisfy the functions
// You should not have all the blocks added to the block chain in memory 
// as it would cause a memory overflow.

public class BlockChain {

    public static final int CUT_OFF_AGE = 10;

    // Since there can be (multiple) forks, blocks form a tree rather than a list.
    private Vector<Vector<Block>> chains;

    private TransactionPool transactionPool;

    // we should keep track of a UTXO for each block and those UTXOs for old blocks
    // are not supposed to be updated when new blocks (with new transactions
    // referring those in old blocks) are appended, in order to allow more than one
    // branches to co-exist on purpose
    private HashMap<byte[], UTXOPool> utxoPools; // can get through txhandler.utxoPool

    /**
     * create an empty block chain with just a genesis block. Assume
     * {@code genesisBlock} is a valid block
     */
    public BlockChain(Block genesisBlock) {
        // IMPLEMENT THIS
        // Since there can be (multiple) forks, blocks form a tree rather than a list
        chains = new Vector<Vector<Block>>();
        Vector<Block> first = new Vector<>();
        first.add(genesisBlock);
        chains.add(first);

        transactionPool = new TransactionPool();
        utxoPools = new HashMap<byte[], UTXOPool>();
        UTXOPool initUtxoPool = new UTXOPool();
        initUtxoPool.addUTXO(new UTXO(genesisBlock.getCoinbase().getHash(), 0),
                genesisBlock.getCoinbase().getOutput(0));

        utxoPools.put(genesisBlock.getHash(), initUtxoPool);
    }

    /** Get the maximum height block */
    public Block getMaxHeightBlock() {
        // IMPLEMENT THIS
        Block maxheightBlock = chains.lastElement().firstElement();
        return maxheightBlock;
    }

    /** Get the UTXOPool for mining a new block on top of max height block */
    public UTXOPool getMaxHeightUTXOPool() {
        // IMPLEMENT THIS
        UTXOPool target = utxoPools.get(chains.lastElement().firstElement().getHash());
        if (target == null)
            System.out.println("Can not get the maxHeight UTXOPool!");
        return target;
    }

    /** Get the transaction pool to mine a new block */
    public TransactionPool getTransactionPool() {
        // IMPLEMENT THIS
        return transactionPool;
    }

    /**
     * Add {@code block} to the block chain if it is valid. For validity, all
     * transactions should be valid and block should be at
     * {@code height > (maxHeight - CUT_OFF_AGE)}.
     * 
     * <p>
     * For example, you can try creating a new block over the genesis block (block
     * height 2) if the block chain height is {@code <=
     * CUT_OFF_AGE + 1}. As soon as {@code height > CUT_OFF_AGE + 1}, you cannot
     * create a new block at height 2.
     * 
     * @return true if block is successfully added
     */

    // all Txs are vaild. createBlock-->addBlock, processBlock-->addBlock

    public boolean addBlock(Block block) {
        // IMPLEMENT THIS
        if (block.getPrevBlockHash() == null) {
            System.out.println("Can not add a genesis block in this blockChain!");
            return false;
        }

        byte[] targetPrevHash = block.getPrevBlockHash();

        int current_height = chains.size() - 1;

        for (int i = 0; i <= CUT_OFF_AGE && i <= current_height; i++) {

            for (Block preBlock : chains.get(current_height - i)) {

                if (targetPrevHash.equals(preBlock.getHash())) {

                    // get the utxoPool
                    UTXOPool current_utxoPool = new UTXOPool(utxoPools.get(targetPrevHash));

                    UTXOPool newPool = updateUtxoPool(current_utxoPool, block);
                    if (null == newPool) {
                        return false;
                    }
                    // if this block is valid, then add this utxoPool to the utxoPool map
                    // remember to add coinbase utxo into the utxoPool, but how to do that?
                    //
                    newPool.addUTXO(new UTXO(block.getCoinbase().getHash(), 0), block.getCoinbase().getOutput(0));

                    utxoPools.put(block.getHash(), newPool);

                    // then add this block into the blockChain
                    if (i == 0) {
                        Vector<Block> lastBlocks = new Vector<>();
                        lastBlocks.add(block);
                        chains.add(lastBlocks);
                    } else { // add to the next vector
                        chains.get(current_height + 1 - i).add(block);
                    }
                    // remove normal transactions from transactionPool if a new block is received or
                    // created.
                    for (Transaction tx : block.getTransactions()) {
                        transactionPool.removeTransaction(tx.getHash());
                    }
                    return true;
                }
            }
        }

        System.out.println("Can not insert this block into the blockChain because of CUT_OFF_AGE!");
        return false;
    }

    public Vector<Vector<Block>> getChains() {
        return chains;
    }

    /** Add a transaction to the transaction pool */
    public void addTransaction(Transaction tx) {
        // IMPLEMENT THIS
        TxHandler txhandler = new TxHandler(getMaxHeightUTXOPool());
        if (txhandler.isValidTx(tx))
            transactionPool.addTransaction(tx);
    }


    private boolean verifySignature(PublicKey pubKey, byte[] message, byte[] signature) {
        Signature sig = null;
        try {
            sig = Signature.getInstance("SHA256withRSA");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        try {
            sig.initVerify(pubKey);
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
        try {
            sig.update(message);
            return sig.verify(signature);
        } catch (SignatureException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean isValidTx(UTXOPool utxoPool, Transaction tx) {
        // IMPLEMENT THIS

        Vector<UTXO> utxoVector = new Vector<UTXO>();
        double totalInput = 0.0;
        double totalOutPut = 0.0;

        for (int index = 0; index < tx.numInputs(); ++index) {

            Transaction.Input ip = tx.getInput(index);
            UTXO utxo = new UTXO(ip.prevTxHash, ip.outputIndex);
            // 1
            if (!utxoPool.contains(utxo)) {
                System.out.println("****************************! utxoPool.contains(utxo)");
                return false;
            }
            // 2
            Transaction.Output op = utxoPool.getTxOutput(utxo);

            boolean verified = false;
            try {
                verified = verifySignature(op.address, tx.getRawDataToSign(index), ip.signature);
            } catch (Exception e) {
                System.out.println("NullPointerException in Sign!");
                return false ;
            }
            if (!verified) {
                System.out.println("****************************! verified");
                return false;
            }

            // 3
            for (UTXO ut : utxoVector)
                if (utxo.equals(ut)) {
                    System.out.println("****************************double utxo");
                    return false;
                }

            utxoVector.add(utxo);

            totalInput += op.value;
            /*
             * //the outputIndex is different among all the outputIndex of Input
             * Vector<Integer> outputIndexSet = new Vector<>();
             * if(outputIndexSet.contains(ip.outputIndex)) return false ;
             * outputIndexSet.add(ip.outputIndex);
             */
        }

        // 4
        for (Transaction.Output op : tx.getOutputs()) {
            if (op.value < 0) {
                System.out.println("**************************** op.value < 0");
                return false;
            }
            totalOutPut += op.value;
        }

        // 5
        if (totalInput < totalOutPut) {
            System.out.println("****************************totalInput < totalOutPut");
            return false;
        }

        return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions,
     * checking each transaction for correctness, returning a mutually valid array
     * of accepted transactions, and updating the current UTXO pool as appropriate.
     */
    private UTXOPool updateUtxoPool(UTXOPool utxoPool, Block block) {
        // IMPLEMENT THIS
        UTXOPool updatedPool = new UTXOPool(utxoPool);

        Vector<Transaction> v = new Vector<Transaction>();
        Vector<Transaction> temp = new Vector<Transaction>();

        for (Transaction tx : block.getTransactions())
            temp.add(tx);

        boolean flag = false;

        do {
            flag = false;
            for (int index = 0; index < temp.size(); ++index) {
                // for each transaction:
                Transaction tx = temp.get(index);
                if (isValidTx(updatedPool, tx)) {

                    flag = true;
                    v.add(tx);
                    temp.remove(tx);

                    // update the UTXO POOL (remove utxo | add new utxo)
                    for (Transaction.Input ip : tx.getInputs()) {
                        updatedPool.removeUTXO(new UTXO(ip.prevTxHash, ip.outputIndex));
                    }
                    tx.finalize();
                    for (int outPutIndex = 0; outPutIndex < tx.numOutputs(); ++outPutIndex) {
                        Transaction.Output op = tx.getOutput(outPutIndex);
                        updatedPool.addUTXO(new UTXO(tx.getHash(), outPutIndex), op);
                    }
                }
            }
            if (temp.size() == 0)
                break;
        } while (flag);

        if (v.size() == block.getTransactions().size()) {
            return updatedPool;
        }

        return null;
    }

}