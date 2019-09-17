import java.util.Vector;

public class TxHandler {

    public UTXOPool utxoPool = null;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent
     * transaction outputs) is {@code utxoPool}. This should make a copy of utxoPool
     * by using the UTXOPool(UTXOPool uPool) constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        // IMPLEMENT THIS
        this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        // IMPLEMENT THIS
        
        Vector<UTXO> utxoVector = new Vector<UTXO>();
        double totalInput = 0.0 ;
        double totalOutPut = 0.0 ;
        
        for(int index = 0; index < tx.numInputs(); ++index){

            Transaction.Input ip =  tx.getInput(index);
            UTXO utxo = new UTXO(ip.prevTxHash, ip.outputIndex);
            //1
            if(! utxoPool.contains(utxo)){
                System.out.println("****************************! utxoPool.contains(utxo)");
                return false ;
            }
            //2
            Transaction.Output op = utxoPool.getTxOutput(utxo);
            boolean verified = Crypto.verifySignature(op.address, tx.getRawDataToSign(index), ip.signature);
            if(! verified) {
                System.out.println("****************************! verified");
                return false ;
            }
            
            //3
            for(UTXO ut: utxoVector)
            if(utxo.equals(ut)) {
                System.out.println("****************************double utxo");
                return false ;
            }
            
            utxoVector.add(utxo);

            totalInput += op.value ;
            /*
            //the outputIndex is different among all the outputIndex of Input
            Vector<Integer> outputIndexSet = new Vector<>();
            if(outputIndexSet.contains(ip.outputIndex))
                return false ;
            outputIndexSet.add(ip.outputIndex);
            */
        }
        

        //4
        for(Transaction.Output op: tx.getOutputs()){
            if(op.value < 0) {
                System.out.println("**************************** op.value < 0");
                return false ;
            }
            totalOutPut += op.value;
        }

        //5
        if(totalInput < totalOutPut){
            System.out.println("****************************totalInput < totalOutPut");
            return false ;
        }

        return true ;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions,
     * checking each transaction for correctness, returning a mutually valid array
     * of accepted transactions, and updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        // IMPLEMENT THIS
        Vector<Transaction> v = new Vector<Transaction>();
        Vector<Transaction> temp = new Vector<Transaction>();
        
        for(Transaction tx: possibleTxs){
            temp.add(tx);
        }
        
        boolean flag = false ;
        do {
            flag = false ;
            for(int index = 0 ; index < temp.size(); ++index){
                // for each transaction:
                Transaction tx = temp.get(index);
                if(isValidTx(tx)){
                    
                    flag = true ;
                    v.add(tx);
                    temp.remove(tx);
                    
                    //update the UTXO POOL (remove utxo | add new utxo)
                    for(Transaction.Input ip: tx.getInputs()){
                        utxoPool.removeUTXO(new UTXO(ip.prevTxHash,ip.outputIndex));
                    }
                    
                    tx.finalize();
                    for(int outPutIndex = 0 ; outPutIndex < tx.numOutputs(); ++outPutIndex){
                        Transaction.Output op = tx.getOutput(outPutIndex);
                        utxoPool.addUTXO(new UTXO(tx.getHash(), outPutIndex), op);
                    }
                }
            }
            if(temp.size() == 0)
            break ;
        }while(flag);
        
        Transaction[] validTransactions = new Transaction[v.size()];
        v.copyInto(validTransactions);
        
        return validTransactions;
    }
    
}
