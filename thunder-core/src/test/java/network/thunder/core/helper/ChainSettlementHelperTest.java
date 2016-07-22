package network.thunder.core.helper;

import network.thunder.core.communication.LNConfiguration;
import network.thunder.core.communication.NodeKey;
import network.thunder.core.communication.ServerObject;
import network.thunder.core.communication.layer.high.Channel;
import network.thunder.core.communication.layer.high.RevocationHash;
import network.thunder.core.communication.layer.high.payments.*;
import network.thunder.core.communication.processor.implementations.management.ChannelBlockchainWatcher;
import network.thunder.core.database.DBHandler;
import network.thunder.core.database.InMemoryDBHandler;
import network.thunder.core.etc.Constants;
import network.thunder.core.etc.MockChannelManager;
import network.thunder.core.etc.TestTools;
import network.thunder.core.etc.Tools;
import network.thunder.core.helper.blockchain.MockBlockchainHelper;
import org.bitcoinj.core.*;
import org.bitcoinj.script.Script;
import org.junit.Before;
import org.junit.Test;

import java.beans.PropertyVetoException;
import java.sql.SQLException;
import java.util.*;

import static junit.framework.TestCase.assertEquals;

public class ChainSettlementHelperTest {

    LNPaymentLogic paymentLogic = new LNPaymentLogicImpl();
    LNConfiguration configuration = TestTools.getZeroFeeConfiguration();

    ServerObject serverObject1 = new ServerObject();
    ServerObject serverObject2 = new ServerObject();

    DBHandler dbHandler1 = new InMemoryDBHandler();
    DBHandler dbHandler2 = new InMemoryDBHandler();

    MockBlockchainHelper blockchainHelper1 = new MockBlockchainHelper();
    MockBlockchainHelper blockchainHelper2 = new MockBlockchainHelper();

    Channel channel1 = TestTools.getMockChannel(configuration);
    Channel channel2 = TestTools.getMockChannel(configuration);

    ChannelBlockchainWatcher blockchainWatcher1;
    ChannelBlockchainWatcher blockchainWatcher2;

    Set<Script.VerifyFlag> flags = TestTools.getVerifyFlags();

    Transaction channelTransaction;

    long startBalance1;
    long startBalance2;

    long diffBalance1;
    long diffBalance2;

    long paymentValue = 10000;

    Set<Transaction> totalTx = new HashSet<>();

    @Before
    public void prepare () throws PropertyVetoException, SQLException {
        Context.getOrCreate(Constants.getNetwork());

        channel1.nodeKeyClient = new NodeKey(serverObject2.pubKeyServer);
        channel2.nodeKeyClient = new NodeKey(serverObject1.pubKeyServer);

        channel1.retrieveDataFromOtherChannel(channel2);
        channel2.retrieveDataFromOtherChannel(channel1);
        channel2.amountServer = channel1.amountClient;
        channel2.amountClient = channel1.amountServer;

        startBalance1 = channel1.amountServer;
        startBalance2 = channel2.amountServer;

        dbHandler1.insertChannel(channel1);
        dbHandler2.insertChannel(channel2);

        blockchainWatcher1 = new ChannelBlockchainWatcher(blockchainHelper1, new MockChannelManager(), channel1, dbHandler1, paymentLogic);
        blockchainWatcher2 = new ChannelBlockchainWatcher(blockchainHelper2, new MockChannelManager(), channel2, dbHandler2, paymentLogic);

        blockchainWatcher1.start();
        blockchainWatcher2.start();
    }

    @Test
    public void correctlyClaimRedeemOverSecondTransaction () throws InterruptedException {
        addPayment(channel1, channel2, dbHandler2, false);
        diffBalance1 = -paymentValue;
        diffBalance2 = +paymentValue;

        correctChannelClose();
    }

    @Test
    public void correctlyClaimRefundOverSecondTransaction () throws InterruptedException {
        addPayment(channel2, channel1, dbHandler2, true);
        diffBalance1 = 0;
        diffBalance2 = 0;

        correctChannelClose();
    }

    @Test
    public void correctlyClaimRedeemDirectly () throws InterruptedException {
        addPayment(channel2, channel1, dbHandler2, false);
        diffBalance1 = +paymentValue;
        diffBalance2 = -paymentValue;

        correctChannelClose();
    }

    @Test
    public void correctlyClaimRefundDirectly () throws InterruptedException {
        addPayment(channel1, channel2, dbHandler2, true);
        diffBalance1 = 0;
        diffBalance2 = 0;

        correctChannelClose();
    }

    @Test
    public void cheatedClaimRefundDirectly () throws InterruptedException {
        addPayment(channel1, channel2, dbHandler2, true);
        diffBalance1 = -startBalance1;
        diffBalance2 = +startBalance1;

        invalidChannelClose();
    }

    @Test
    public void cheatedClaimRedeemDirectly () throws InterruptedException {
        addPayment(channel2, channel1, dbHandler2, true);
        diffBalance1 = -startBalance1;
        diffBalance2 = +startBalance1;

        invalidChannelClose();
    }

    private void correctChannelClose () {
        exchangeChannelSignatures();
        channelTransaction = paymentLogic.getSignedChannelTransaction(channel1);
        channelClose();
    }

    private void invalidChannelClose () {
        exchangeChannelSignatures();
        moveRevocationHashFoward();

        channelTransaction = paymentLogic.getSignedChannelTransaction(channel1);
        channelClose();
    }

    private void moveRevocationHashFoward () {
        dbHandler2.updateChannelStatus(null, channel2.getHash(), null, null, null, Collections.singletonList(channel2.revoHashClientNext), null, null);
        channel2.revoHashServerCurrent = new RevocationHash(3, channel2.masterPrivateKeyServer);
        channel2.revoHashClientCurrent = new RevocationHash(3, channel1.masterPrivateKeyServer);
        channel2.revoHashServerNext = new RevocationHash(4, channel2.masterPrivateKeyServer);
        channel2.revoHashClientNext = new RevocationHash(4, channel1.masterPrivateKeyServer);
        channel2.shaChainDepthCurrent = 3;
    }

    private void channelClose () {
        broadcastSpendingTransaction();
        broadcastBlocks(3000);
        validateTransactions();
        validateOutputs();
    }

    public void broadcastSpendingTransaction () {
        channelTransaction.getInputs().forEach(in -> in.setScriptSig(Tools.getDummyScript()));

        System.out.println("ChainSettlementHelperTest.broadcastSpendingTransaction");
        System.out.println(channelTransaction);

        channel1.applyNextRevoHash();
        channel2.applyNextRevoHash();

        channel1.applyNextNextRevoHash();
        channel2.applyNextNextRevoHash();

        blockchainHelper1.mockNewBlock(Collections.singletonList(channelTransaction), true);
        blockchainHelper2.mockNewBlock(Collections.singletonList(channelTransaction), true);
    }

    public void broadcastBlocks (int amount) {
        List<Transaction> txList = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            txList.clear();
            txList.addAll(blockchainHelper1.getNewTransactions());
            txList.addAll(blockchainHelper2.getNewTransactions());

            blockchainHelper1.getNewTransactions().clear();
            blockchainHelper2.getNewTransactions().clear();

            blockchainHelper1.mockNewBlock(txList, true);
            blockchainHelper2.mockNewBlock(txList, true);
        }
        totalTx.addAll(blockchainHelper1.broadcastedTransaction);
        totalTx.addAll(blockchainHelper2.broadcastedTransaction);
    }

    public void validateTransactions () {
        Map<TransactionOutPoint, TransactionOutput> transactionMap = new HashMap<>();
        for (Transaction t : totalTx) {
            for (TransactionOutput output : t.getOutputs()) {
                transactionMap.put(output.getOutPointFor(), output);
            }
        }
        Set<Transaction> invalidTx = new HashSet<>();
        for (Transaction t : totalTx) {
            if (t.equals(channelTransaction)) {
                continue;
            }
            int i = 0;
            for (TransactionInput input : t.getInputs()) {
                TransactionOutput output = transactionMap.get(input.getOutpoint());
                transactionMap.remove(input.getOutpoint());

                try {
                    input.getScriptSig().correctlySpends(t, i, output.getScriptPubKey(), flags);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Invalid tx = " + t.getHash());
                    invalidTx.add(t);
                }
                i++;
            }
        }
        totalTx.removeAll(invalidTx);
    }

    public void validateOutputs () {
        //At the end there should be two outputs for each party paying directly the payments,
        // and another one for each paying the channel balance
        System.out.println("ChainSettlementHelperTest.validateOutputs outputs1");
        long outputs1 = totalTx.stream()
                .map(Transaction::getOutputs)
                .flatMap(Collection::stream)
                .filter(output -> channel1.addressServer.equals(output.getAddressFromP2PKHScript(Constants.getNetwork())))
                .peek(output -> System.out.println(output.getOutPointFor()))
                .mapToLong(output -> output.getValue().value)
                .sum();

        System.out.println("ChainSettlementHelperTest.validateOutputs outputs2");
        long outputs2 = totalTx.stream()
                .map(Transaction::getOutputs)
                .flatMap(Collection::stream)
                .filter(output -> channel1.addressClient.equals(output.getAddressFromP2PKHScript(Constants.getNetwork())))
                .peek(output -> System.out.println(output.getOutPointFor()))
                .mapToLong(output -> output.getValue().value)
                .sum();

        System.out.println("outputs1 = " + outputs1);
        System.out.println("outputs2 = " + outputs2);

        assertEquals(startBalance1 + diffBalance1, outputs1);
        assertEquals(startBalance2 + diffBalance2, outputs2);
    }

    public static PaymentData getMockPaymentData (ECKey key1, ECKey key2) {
        LNConfiguration configuration = TestTools.getZeroFeeConfiguration();
        PaymentData paymentData = new PaymentData();
        paymentData.sending = true;
        paymentData.amount = 10000;
        paymentData.secret = new PaymentSecret(Tools.getRandomByte(20));
        paymentData.timestampOpen = 0;
        paymentData.timestampRefund = paymentData.timestampOpen + configuration.DEFAULT_REFUND_DELAY * 10;

        LNOnionHelper onionHelper = new LNOnionHelperImpl();
        List<byte[]> route = new ArrayList<>();
        route.add(key1.getPubKey());
        route.add(key2.getPubKey());

        paymentData.onionObject = onionHelper.createOnionObject(route, null);

        return paymentData;
    }

    public static PaymentData addPayment (Channel sender, Channel receiver, DBHandler dbHandlerReceiver, boolean refund) {
        PaymentData payment = getMockPaymentData(sender.keyServer, receiver.keyServer);
        PaymentData paymentSender = payment.cloneObject();
        PaymentData paymentReceiver = payment.cloneObject();

        paymentSender.sending = true;
        paymentReceiver.sending = false;

        paymentSender.secret.secret = null;
        if (refund) {
            paymentReceiver.secret.secret = null;
        } else {
            dbHandlerReceiver.addPaymentSecret(paymentSender.secret);
        }

        sender.paymentList.add(paymentSender);
        sender.amountServer -= paymentSender.amount;

        receiver.paymentList.add(paymentReceiver);
        receiver.amountClient -= paymentSender.amount;

        return payment;
    }

    public void exchangeChannelSignatures () {
        LNPaymentLogic paymentLogic = new LNPaymentLogicImpl();

        channel1.channelSignatures = paymentLogic.getSignatureObject(channel2);
        channel2.channelSignatures = paymentLogic.getSignatureObject(channel1);

    }

}