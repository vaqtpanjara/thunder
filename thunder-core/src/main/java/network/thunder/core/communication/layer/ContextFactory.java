package network.thunder.core.communication.layer;

import network.thunder.core.communication.ClientObject;
import network.thunder.core.communication.layer.high.channel.ChannelManager;
import network.thunder.core.communication.layer.high.channel.establish.LNEstablishProcessor;
import network.thunder.core.communication.layer.high.channel.establish.messages.LNEstablishMessageFactory;
import network.thunder.core.communication.layer.high.payments.LNOnionHelper;
import network.thunder.core.communication.layer.high.payments.LNPaymentHelper;
import network.thunder.core.communication.layer.high.payments.LNRoutingHelper;
import network.thunder.core.communication.layer.high.payments.messages.LNPaymentMessageFactory;
import network.thunder.core.communication.layer.low.authentication.AuthenticationProcessor;
import network.thunder.core.communication.layer.low.authentication.messages.AuthenticationMessageFactory;
import network.thunder.core.communication.layer.low.encryption.EncryptionProcessor;
import network.thunder.core.communication.layer.low.encryption.MessageEncrypter;
import network.thunder.core.communication.layer.low.encryption.messages.EncryptionMessageFactory;
import network.thunder.core.communication.layer.low.serialisation.MessageSerializer;
import network.thunder.core.communication.layer.middle.broadcasting.gossip.GossipProcessor;
import network.thunder.core.communication.layer.middle.broadcasting.sync.SyncProcessor;
import network.thunder.core.communication.layer.middle.broadcasting.sync.messages.SyncMessageFactory;
import network.thunder.core.communication.layer.middle.peerseed.messages.PeerSeedMessageFactory;
import network.thunder.core.communication.layer.middle.peerseed.PeerSeedProcessor;
import network.thunder.core.communication.layer.middle.broadcasting.gossip.messages.GossipMessageFactory;
import network.thunder.core.communication.layer.middle.broadcasting.gossip.BroadcastHelper;
import network.thunder.core.communication.layer.middle.broadcasting.gossip.GossipSubject;
import network.thunder.core.communication.layer.middle.broadcasting.sync.SynchronizationHelper;
import network.thunder.core.communication.layer.high.payments.LNPaymentLogic;
import network.thunder.core.communication.layer.high.payments.LNPaymentProcessor;
import network.thunder.core.helper.blockchain.BlockchainHelper;
import network.thunder.core.helper.events.LNEventHelper;
import network.thunder.core.helper.wallet.WalletHelper;
import network.thunder.core.communication.ServerObject;

/**
 * Created by matsjerratsch on 18/01/2016.
 */
public interface ContextFactory {
    MessageSerializer getMessageSerializer ();

    MessageEncrypter getMessageEncrypter ();

    EncryptionProcessor getEncryptionProcessor (ClientObject node);

    AuthenticationProcessor getAuthenticationProcessor (ClientObject node);

    PeerSeedProcessor getPeerSeedProcessor (ClientObject node);

    SyncProcessor getSyncProcessor (ClientObject node);

    GossipProcessor getGossipProcessor (ClientObject node);

    LNEstablishProcessor getLNEstablishProcessor (ClientObject node);

    LNPaymentProcessor getLNPaymentProcessor (ClientObject node);

    LNPaymentHelper getPaymentHelper ();

    LNOnionHelper getOnionHelper ();

    LNEventHelper getEventHelper ();

    BroadcastHelper getBroadcastHelper ();

    SynchronizationHelper getSyncHelper ();

    WalletHelper getWalletHelper ();

    GossipSubject getGossipSubject ();

    LNPaymentLogic getLNPaymentLogic ();

    ServerObject getServerSettings ();

    EncryptionMessageFactory getEncryptionMessageFactory ();

    AuthenticationMessageFactory getAuthenticationMessageFactory ();

    PeerSeedMessageFactory getPeerSeedMessageFactory ();

    SyncMessageFactory getSyncMessageFactory ();

    GossipMessageFactory getGossipMessageFactory ();

    LNEstablishMessageFactory getLNEstablishMessageFactory ();

    LNPaymentMessageFactory getLNPaymentMessageFactory ();

    LNRoutingHelper getLNRoutingHelper ();

    BlockchainHelper getBlockchainHelper ();

    ChannelManager getChannelManager ();
}